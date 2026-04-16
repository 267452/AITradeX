package com;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AitradexJavaApplication {
    public static void main(String[] args) {
        SpringApplication.run(AitradexJavaApplication.class, args);
    }
}
