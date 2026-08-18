package com.example.learnerassignments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class LearnerAssignmentsApplication {

	@PostConstruct
	public void init() {
		TimeZone.setDefault(TimeZone.getTimeZone("Africa/Johannesburg"));
	}

	public static void main(String[] args) {
		String dbUrl = System.getenv("SPRING_DATASOURCE_URL");
		if (dbUrl == null || dbUrl.isBlank()) {
			dbUrl = System.getenv("DATABASE_URL");
		}
		if (dbUrl != null && (dbUrl.startsWith("postgres://") || dbUrl.startsWith("postgresql://"))) {
			String prefix = dbUrl.startsWith("postgres://") ? "postgres://" : "postgresql://";
			String rawUrl = dbUrl.substring(prefix.length());
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
				System.out.println("LearnerAssignmentsApplication: Auto-configured connection URL to jdbc:postgresql:// format");
			}
		}
		SpringApplication.run(LearnerAssignmentsApplication.class, args);
	}

}
