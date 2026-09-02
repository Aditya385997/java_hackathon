package com.aditya.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync   // T-4: offline re-planning runs off the request thread
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
