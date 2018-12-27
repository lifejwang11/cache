package com.wgg.cache;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;

@org.springframework.stereotype.Service
public class Service {

    @Cacheable(value = "name",key = "#name")
    public User getUser(String name){
        System.out.println("get");
        return null;
    }

    @CachePut(value = "name" ,key = "#user.username")
    public User updateUser(User user){
        System.out.println("update");
        return new User("111","233333");
    }

    @CacheEvict(value = "name",allEntries = true,key = "#user.username")
    public User deleteUser(User user){
        System.out.println("delete");
        return null;
    }


}
