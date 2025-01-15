package com.hz.demos;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.XADataSource;

import org.postgresql.xa.PGXADataSource;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;

public class Common implements Serializable {

    public static XADataSource getXADataSource(String host) {
        PGXADataSource factory = new PGXADataSource();
        factory.setUrl("jdbc:postgresql://" + host + ":5432/hazelcast");
        factory.setUser("hazelcast");
        factory.setPassword("hazelcast");
        factory.setDatabaseName("hazelcast");
        return factory;
    }

    public static void createTable(XADataSource pgXADataSource) throws Exception {

        try (Connection connection = pgXADataSource.getXAConnection().getConnection()) {
            executeStatement(connection, true,
                    "CREATE TABLE customer_one (id INT PRIMARY KEY, name VARCHAR(255), uniqueId INT UNIQUE)");
        }
    }

    public static void executeStatement(Connection connection, boolean commit, String statement, Object... params)
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

    public static HazelcastInstance getHzClient() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setClusterName("dev");
        clientConfig.getNetworkConfig().addAddress("localhost:5701");
        return HazelcastClient.newHazelcastClient(clientConfig);
    }

    public static void cleanup(XADataSource pgXADataSource, HazelcastInstance hz) {
        if (pgXADataSource != null) {
            try (Connection connection = pgXADataSource.getXAConnection().getConnection()) {
                executeStatement(connection, true,
                        "DROP TABLE customer_one");
            } catch (Exception e) {
                System.out.println("Table does not exist");
            }
        }
        hz.getMap(XATest.MAP_NAME).destroy();
        hz.getMap(XATest.ANOTHER_MAP_NAME).destroy();
        hz.getMap(XATest.UNIQUE_ID_MAP).destroy();
    }

}
