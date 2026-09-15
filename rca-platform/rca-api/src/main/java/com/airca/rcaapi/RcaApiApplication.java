package com.airca.rcaapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.airca.rcaapi", "com.airca.rca.framework"})
public class RcaApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RcaApiApplication.class, args);
    }
}
