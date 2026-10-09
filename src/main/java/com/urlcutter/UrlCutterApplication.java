package com.urlcutter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class UrlCutterApplication {
    public UrlCutterApplication() {
        super();
    }

    public static void main(String[] args) {
        SpringApplication.run(UrlCutterApplication.class, args);
    }
}