package com.example.demo.controller;


import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.LocalServerPort;

import java.net.URL;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.equalTo;


public class DemoAT {
    private static final Logger log = LoggerFactory.getLogger(DemoAT.class);

    @LocalServerPort
    private int port = 8020;

    private URL base;

    @Before
    public void setUp() throws Exception {
        this.base = new URL("http://localhost:" + port + "/");
    }

    @Test
    public void getHello() {
        when().get(base + "demo/")
                .then().assertThat().body(equalTo("This demo is awesome!"));
    }
}
