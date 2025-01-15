package com.hz.demos.domain;

import com.hazelcast.license.shaded.com.fasterxml.jackson.databind.ObjectMapper;

public class Customer {
    private long id;
    private String name;
    private long uniqueId;

    public Customer() {
    }


    public Customer(int id, String name, int uniqueId) {
        this.id = id;
        this.name = name;
        this.uniqueId = uniqueId;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getId() {
        return id;
    }
    public void setId(long id) {
        this.id = id;
    }

    public long getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(long uniqueId) {
        this.uniqueId = uniqueId;
    }


    public static Customer fromJson(String custJson) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(custJson, Customer.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public String toJson() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    /*
     * Example json
      {
         "id": 1,
        "name": "first",
       "uniqueId": 1
      }
     */
}
