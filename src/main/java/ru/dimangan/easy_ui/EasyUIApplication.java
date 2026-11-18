package ru.dimangan.easy_ui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EasyUIApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasyUIApplication.class, args);
    }

}
