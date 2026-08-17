package com.example.learnerassignments.config;

import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

@Configuration
public class DatabaseConfig {

    @PostConstruct
    public void init() {
        String dbUrl = System.getenv("SPRING_DATASOURCE_URL");
        if (dbUrl != null && dbUrl.startsWith("postgres://")) {
            // Convert postgres://username:password@hostname:port/dbname
            // to jdbc:postgresql://hostname:port/dbname
            String rawUrl = dbUrl.substring(11); // remove postgres://
            int atIndex = rawUrl.indexOf("@");
            if (atIndex != -1) {
                String credentials = rawUrl.substring(0, atIndex);
                String hostAndDb = rawUrl.substring(atIndex + 1);
                
                String[] credParts = credentials.split(":");
                String username = credParts[0];
                String password = credParts.length > 1 ? credParts[1] : "";
                
                String jdbcUrl = "jdbc:postgresql://" + hostAndDb;
                
                System.setProperty("spring.datasource.url", jdbcUrl);
                System.setProperty("spring.datasource.username", username);
                System.setProperty("spring.datasource.password", password);
                
                System.out.println("DatabaseConfig: Auto-converted postgres:// URL to jdbc:postgresql:// format");
            }
        }
    }
}
