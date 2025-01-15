package com.hz.demos;

import java.io.Serializable;
import java.util.Optional;
import java.util.Properties;

import javax.sql.XADataSource;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.jet.Util;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.config.ProcessingGuarantee;
import com.hazelcast.jet.kafka.KafkaSources;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.StreamStage;
import com.hz.demos.domain.Customer;

public class PipelineTx implements Serializable {
    private static final String JOB_NAME = "PipelineTx";

    public static void main(String[] args) throws Exception {
        PipelineTx txTest = new PipelineTx();
        XADataSource pgXADataSource = Common.getXADataSource("localhost");
        Common.createTable(pgXADataSource);
        HazelcastInstance hzClient = Common.getHzClient();
        Optional.ofNullable(hzClient.getJet().getJob(JOB_NAME)).ifPresent(j -> j.cancel());
        JobConfig jobConfig = new JobConfig();
        jobConfig.addClass(PipelineTx.class);
        jobConfig.addClass(Common.class);
        jobConfig.addClass(Customer.class);
        jobConfig.setProcessingGuarantee(ProcessingGuarantee.EXACTLY_ONCE);
        jobConfig.setName(JOB_NAME);
        hzClient.getJet().newJob(txTest.getPipeline(), jobConfig);
        // txTest.pushToKafka(new Customer(1, "first", 1));
        // Thread.sleep(TimeUnit.SECONDS.toMillis(10));
        // txTest.pushToKafka(new Customer(2, "second", 1));
        hzClient.shutdown();
    }

    private Pipeline getPipeline() {
        Pipeline p = Pipeline.create();
        StreamStage<Customer> customer = p.readFrom(KafkaSources.kafka(getKafkaProperties(), MyConstants.KAFKA_TOPIC))
                .withNativeTimestamps(5)
                .map(e -> e.getValue().toString())
                .map(custJson -> {
                    // convert incomsing json to Customer object
                    Customer cust = Customer.fromJson(custJson);
                    return cust;
                });
        customer
                .map(c -> Util.entry(c.getId(), c))
                .writeTo(Sinks.map("amap"));

        customer
                .writeTo(Sinks.jdbc("INSERT INTO customer_one (id, name, uniqueId) VALUES (?, ?, ?)",
                        () -> Common.getXADataSource("postgres"),
                        (stmt, item) -> {
                            stmt.setLong(1, item.getId());
                            stmt.setString(2, item.getName());
                            stmt.setLong(3, item.getUniqueId());
                            stmt.addBatch();
                        }));
        return p;
    }

    private Properties getKafkaProperties() {
        Properties props = new Properties();
        props.setProperty("bootstrap.servers", "kafka:9092");
        props.setProperty("key.deserializer", StringDeserializer.class.getCanonicalName());
        props.setProperty("value.deserializer", StringDeserializer.class.getCanonicalName());
        props.setProperty("key.serializer", StringSerializer.class.getCanonicalName());
        props.setProperty("value.serializer", StringSerializer.class.getCanonicalName());
        props.setProperty("auto.offset.reset", "earliest");
        return props;
    }

    private void pushToKafka(Customer customer) {
        Properties props = getKafkaProperties();
        // connecting from outside the container
        props.setProperty("bootstrap.servers", "localhost:9093");
        KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        producer.send(
                new ProducerRecord<String, String>(MyConstants.KAFKA_TOPIC, customer.getId() + "", customer.toJson()));
        producer.flush();
        producer.close();
    }
}
