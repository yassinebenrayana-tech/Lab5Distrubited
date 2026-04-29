package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"org.example"})
public class MicroservicesApplication {

    public static void main(String[] args) {
        SpringApplication.run(MicroservicesApplication.class, args);
    }

    @Configuration
    @Profile({"order", "orchestrator"})
    @EnableJpaRepositories(basePackages = "org.example.order.repository")
    @EntityScan(basePackages = "org.example.order.model")
    static class OrderJpaConfiguration {
    }

    @Configuration
    @Profile("kitchen")
    @EnableJpaRepositories(basePackages = "org.example.kitchen.repository")
    @EntityScan(basePackages = "org.example.kitchen.model")
    static class KitchenJpaConfiguration {
    }

    @Configuration
    @Profile("accounting")
    @EnableJpaRepositories(basePackages = "org.example.accounting.repository")
    @EntityScan(basePackages = "org.example.accounting.model")
    static class AccountingJpaConfiguration {
    }
}

