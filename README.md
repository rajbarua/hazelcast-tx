# Hazelcast Transactions - Atomicity, Consistency, Isolation, Durability (ACID) properties

## Atomicity
As an example, can Hazelcast udpate 4 IMaps with associated MapStore? The simple answer is, **no**. MapStore cannot participate in a transaction, irrespective of the write-through or write-behind mode. While transactions are depcrecated in Hazelcast, XA transactions still are available to use till version 7.0. Therefore as a temporary workaround, See example `XATest.java` where data is inserted in Hazelcast and PostgreSQL atomically.

### XATest.java
Start the docker compose `docker compose up` and then run `XATest` with the IDE or with the following command:
```shell
mvn clean install exec:java -Dexec.mainClass=com.hz.demos.XATest
```
#### Atomicity via partition locking
If the 4 maps being modified have the same key, or present in the same partition by way of being PartitionAware, then the atomicity can be achieved by locking the partition or using EntryProcessors (which essentially locks the partion till the operation is complete). 
But if we include MapStore as part of the operation, then that locks have to held for a long time. This is not recommended as it can lead to performance issues. Note that MapStore should be write-through as write-behind will not make sense if we want to fail upon database write fails.
Therefore this mechanism works best when no database updates are involved. Or if database updates are indeed involved, then they are executed write-behind and any failure should be handled by way of compensating transactions.

#### Atomicity via Pipelines
Start the docker compose `docker compose up` and then run `PipelineTx` with the IDE or with the following command:
```shell
mvn clean install exec:java -Dexec.mainClass=com.hz.demos.PipelineTx
```

## Consistency
Hazelcast CP datastructures provides strong consistency guarantees. It is possible to read your own writes, and the data is always consistent across the cluster. Hazelcast CP datastructures provides a consistent view of the data across the cluster.

## Refereces
- https://hazelcast.com/blog/design-considerations-when-using-transactionality/
- https://hazelcast.com/blog/making-your-data-safe-from-loss/
- https://hazelcast.com/blog/transactional-connectors-in-hazelcast-jet/
- https://docs.hazelcast.com/hazelcast/5.5/pipelines/xa