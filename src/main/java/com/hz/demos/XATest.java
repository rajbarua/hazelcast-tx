package com.hz.demos;

import java.io.File;
import java.io.FilenameFilter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.Transaction;
import javax.transaction.xa.XAResource;

import org.postgresql.xa.PGXADataSource;

import com.atomikos.icatch.jta.UserTransactionManager;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.transaction.HazelcastXAResource;
import com.hazelcast.transaction.TransactionContext;

class XATest {
    public static String MAP_NAME = "amap";
    public static long FIRST_KEY = 1;
    public static long SECOND_KEY = 2;

    public static void main(String[] args) throws Exception {
        XATest txTest = new XATest();
        var firstCustomer = new Customer(1, "name", 1);
        // create database schema
        XADataSource pgXADataSource = txTest.getXADataSource();
        try (Connection connection = pgXADataSource.getXAConnection().getConnection()) {
            txTest.executeStatement(connection, true,
                    "CREATE TABLE customer_one (id INT PRIMARY KEY, name VARCHAR(255), uniqueId INT UNIQUE)");
        }
        HazelcastInstance hzClient = txTest.getHzClient();
        //The first customer should be inserted succesfully
        txTest.testTx(hzClient, pgXADataSource, firstCustomer);

        if (txTest.validatePresent(hzClient, firstCustomer)) {
            System.out.println("First test was successful");
        } else {
            System.out.println("First test failed");
        }

        //The second customer insertion should fail due to unique constraint violation in the database
        var secondCustomer = new Customer(2, "name", 1);
        txTest.testTx(hzClient, pgXADataSource, secondCustomer);
        if (txTest.validatePresent(hzClient, secondCustomer)) {
            System.out.println("Second test failed");
        } else {
            System.out.println("Second test was successful");
        }

        //The second customer insertion should fail due to unique constraint violation in the database
        var thridCustomer = new Customer(3, "name", 3);
        txTest.testTx(hzClient, pgXADataSource, thridCustomer);
        if (txTest.validatePresent(hzClient, thridCustomer)) {
            System.out.println("Third test was successful");
        } else {
            System.out.println("Third test failed");
        }
        hzClient.shutdown();
    }

    private void testTx(HazelcastInstance hzClient, XADataSource pgXADataSource, Customer customer)
            throws Exception {
        // I am using atomikos but you can use any other JTA provider
        cleanAtomikosLogs();
        UserTransactionManager tm = new UserTransactionManager();
        tm.begin();

        Transaction transaction = tm.getTransaction();

        HazelcastXAResource xaResource = hzClient.getXAResource();
        XAConnection pgXAConnection = pgXADataSource.getXAConnection();
        XAResource pgXAResource = pgXAConnection.getXAResource();
        transaction.enlistResource(pgXAResource);
        transaction.enlistResource(xaResource);
        boolean rollback = false;
        try (Connection connection = pgXAConnection.getConnection()) {
            connection.setAutoCommit(false);
            TransactionContext context = xaResource.getTransactionContext();
            context.getMap(MAP_NAME).put(customer.id(), customer);
            executeStatement(connection, false,
                    "INSERT INTO customer_one (id, name, uniqueId) VALUES (?, ?, ?)", customer.id(), customer.name(),
                    customer.uniqueId());
            // if(true)
            //     throw new RuntimeException("Simulating a failure");
            transaction.delistResource(xaResource, XAResource.TMSUCCESS);
            transaction.delistResource(pgXAResource, XAResource.TMSUCCESS);
            tm.commit();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            rollback = true;
        } finally {
            if (rollback) {
                try {
                    tm.rollback();
                } catch (Exception rollbackException) {
                    System.out.println("Error during rollback: " + rollbackException.getMessage());
                }
            }
        }
        cleanAtomikosLogs();
    }
    private boolean validatePresent(HazelcastInstance hzClient, Customer customer) {
        boolean present = false;
        // also check if the data is present in the database
        try {
            Connection connection = getXADataSource().getXAConnection().getConnection();
            Statement statement = connection.createStatement();
            var resultSet = statement.executeQuery("SELECT * FROM customer_one WHERE id = " + customer.id());
            if (resultSet.next()) {
                present = resultSet.getInt("id") == customer.id() && resultSet.getString("name").equals(customer.name())
                        && resultSet.getInt("uniqueId") == customer.uniqueId();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return present && hzClient.getMap(MAP_NAME).get(customer.id) != null
                && hzClient.getMap(MAP_NAME).get(customer.id).equals(customer);
    }

    public HazelcastInstance getHzClient() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setClusterName("dev");
        clientConfig.getNetworkConfig().addAddress("localhost:5701");
        return HazelcastClient.newHazelcastClient(clientConfig);
    }
    private XADataSource getXADataSource() {
        PGXADataSource factory = new PGXADataSource();
        factory.setUrl("jdbc:postgresql://localhost:5432/hazelcast");
        factory.setUser("hazelcast");
        factory.setPassword("hazelcast");
        factory.setDatabaseName("hazelcast");
        return factory;
    }

    private void executeStatement(Connection connection, boolean commit, String statement, Object... params)
            throws Exception {
        try (PreparedStatement stmt = connection.prepareStatement(statement)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            stmt.execute();
        } catch (SQLException e) {
            if (e.getSQLState().equals("42P07")) {
                System.out.println("Table already exists");
            } else {
                throw e;
            }
        }
    }

    public record Customer(long id, String name, long uniqueId) {
    }

    private static void cleanAtomikosLogs() {
        try {
            File currentDir = new File(".");
            final File[] tmLogs = currentDir.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(".epoch") || name.startsWith("tmlog");
                }
            });
            for (File tmLog : tmLogs) {
                tmLog.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
