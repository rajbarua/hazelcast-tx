package com.hz.demos;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hz.demos.domain.Customer;

public class PutIfAbsentTest {
    public static String DATA_MAP_1 = "amap";
    public static String UNIQUE_ID_MAP = "uniqueIdMap";

    public static void main(String[] args) throws Exception {
        PutIfAbsentTest testClass = new PutIfAbsentTest();
        HazelcastInstance hzClient = Common.getHzClient();
        Common.cleanup(null, hzClient);
        //insert with new uniqueId and should be allowed
        testClass.runTest(hzClient, new Customer(1, "name", 1), "First test", true);
        //insert with new uniqueId and should be allowed
        testClass.runTest(hzClient, new Customer(2, "name", 2), "Second test", true);
        //insert with existing uniqueId and should be stopped
        testClass.runTest(hzClient, new Customer(3, "name", 1), "Third test", false);
        //update with existing uniqueId associated to another pk, therefore should be stopped
        testClass.runTest(hzClient, new Customer(2, "name", 1), "Fourth test", false);
        //update with existing uniqueId associated to same pk, therefore should be allowd
        testClass.runTest(hzClient, new Customer(2, "name", 2), "Fourth test", true);
        hzClient.shutdown();
    }

    private void runTest(HazelcastInstance hzClient, Customer customer, String name, boolean shouldInsert) {
        boolean isInserted = false;
        IMap<Long, Long> uniqueIdMap = hzClient.getMap(UNIQUE_ID_MAP);
        IMap<Long, Customer> dataMap = hzClient.getMap(DATA_MAP_1);

        Long id = uniqueIdMap.putIfAbsent(customer.getUniqueId(), customer.getId());
        if (id == null || (id != null && id == customer.getId())) {
            // If "id" is null then this is an insert and should be allowed
            // If "id" is NOT null AND incoming "id" is uniqueIdMap entry, then this is an update and should be allowed
            dataMap.put(customer.getId(), customer);
            isInserted = true;
        } else{ 
            //if (id != null && id != customer.getId()) 
            // this is an insert but with duplicate uniqueIdMap entry and should be stopped
            //return;
            isInserted = false;
        }

        if (isInserted == shouldInsert) {
            System.out.println(name + " was successful");
        } else {
            System.out.println(name + " failed");
        }
    }

}
