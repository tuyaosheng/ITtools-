package com.ittools;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.ittools")
@EnableScheduling
public class IttoolsApplication {
    public static void main(String[] args) {
        SpringApplication.run(IttoolsApplication.class, args);
    }
}
