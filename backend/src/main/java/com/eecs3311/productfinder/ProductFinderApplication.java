package com.eecs3311.productfinder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.WebApplicationType;

@SpringBootApplication
public class ProductFinderApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ProductFinderApplication.class);
        boolean cliEnabled = Boolean.getBoolean("app.cli.enabled")
                || java.util.Arrays.stream(args).anyMatch(argument ->
                        argument.equals("--app.cli.enabled") || argument.equals("--app.cli.enabled=true"));
        if (cliEnabled) {
            application.setWebApplicationType(WebApplicationType.NONE);
        }
        application.run(args);
    }
}
