package com.ittools;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.ittools")
public class IttoolsApplication {
    public static void main(String[] args) {
        SpringApplication.run(IttoolsApplication.class, args);
    }
}
