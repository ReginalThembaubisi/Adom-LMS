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
		SpringApplication.run(LearnerAssignmentsApplication.class, args);
	}

}
