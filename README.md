# Hazelcast Transactions - Atomicity, Consistency, Isolation, Durability (ACID) properties

## Atomicity
As an example, can Hazelcast udpate 4 IMaps with associated MapStore? The simple answer is, **no**. MapStore cannot participate in a transaction, irrespective of the write-through or write-behind mode. While transactions are depcrecated in Hazelcast, XA transactions still are available to use till version 7.0. See example `XATest.java` for more details.

### XATest.java
Inserts data into 4 IMaps backed by database. All done via XA transactions.
Run the test
```shell
docker compose up
mvn clean install exec:java -Dexec.mainClass=com.hz.demos.XATest
```

## Consistency
Hazelcast provides strong consistency guarantees. It is possible to read your own writes, and the data is always consistent across the cluster. Hazelcast provides a consistent view of the data across the cluster.

## Refereces
- https://hazelcast.com/blog/design-considerations-when-using-transactionality/
- https://hazelcast.com/blog/making-your-data-safe-from-loss/
- https://hazelcast.com/blog/transactional-connectors-in-hazelcast-jet/
- https://docs.hazelcast.com/hazelcast/5.5/pipelines/xa