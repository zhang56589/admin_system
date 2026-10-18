package com.example.adminsystem;

import com.example.adminsystem.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class AdminSystemBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminSystemBackendApplication.class, args);
    }
}
