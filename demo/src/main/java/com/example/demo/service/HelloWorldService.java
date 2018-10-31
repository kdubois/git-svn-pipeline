package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HelloWorldService {

    @Value("${name:demo}")
    private String name;

    public String getHelloMessage() {
        return "This " + name + " is awesome!";
    }

}
