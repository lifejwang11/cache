package com.wgg.cache;

import com.wgg.cache.twocache.RedisConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@org.springframework.stereotype.Controller
public class Controller {

    @Autowired
    private Service service;

    public  User user;
    @GetMapping(value = "test")
    @ResponseBody
    public String test(){
        System.out.println("start eache");
        user=service.updateUser(new User("111","222"));

  //      Cache cache = cacheManager.getCache(map.get("keyCode").toString());
        user=service.getUser("111");

  //      System.out.println(user.getUsername());

        service.deleteUser(user);

        user=service.getUser("111");

        return "OK";
    }
}
