package com.jpg.portfolio.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan("com.jpg.portfolio")
public class PortfolioOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortfolioOrchestratorApplication.class, args);
    }
}
