package com.example.learnerassignments.security;

import com.example.learnerassignments.model.Admin;
import com.example.learnerassignments.model.Lecturer;
import com.example.learnerassignments.repository.AdminRepository;
import com.example.learnerassignments.repository.LecturerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final AdminRepository adminRepository;
    private final LecturerRepository lecturerRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${FACILITATOR_USERNAME:admin}")
    private String defaultUsername;

    @Value("${FACILITATOR_PASSWORD:admin123}")
    private String defaultPassword;

    @Override
    public void run(String... args) {
        adminRepository.findByUsername(defaultUsername).ifPresentOrElse(
            admin -> {
                admin.setPasswordHash(passwordEncoder.encode(defaultPassword));
                adminRepository.save(admin);
            },
            () -> {
                Admin admin = Admin.builder()
                        .username(defaultUsername)
                        .passwordHash(passwordEncoder.encode(defaultPassword))
                        .build();
                adminRepository.save(admin);
                log.info("Seeded initial admin account: {}", defaultUsername);
            }
        );

        lecturerRepository.findByUsername("janedoe").ifPresentOrElse(
            lecturer -> {
                lecturer.setPasswordHash(passwordEncoder.encode("lecturer123"));
                lecturerRepository.save(lecturer);
            },
            () -> {
                Lecturer lecturer = Lecturer.builder()
                        .fullName("Prof. Jane Doe")
                        .email("jane@example.com")
                        .username("janedoe")
                        .passwordHash(passwordEncoder.encode("lecturer123"))
                        .build();
                lecturerRepository.save(lecturer);
                log.info("Seeded initial lecturer account: janedoe");
            }
        );

        lecturerRepository.findByUsername("johnsmith").ifPresentOrElse(
            lecturer -> {
                lecturer.setPasswordHash(passwordEncoder.encode("lecturer123"));
                lecturerRepository.save(lecturer);
            },
            () -> {
                Lecturer lecturer = Lecturer.builder()
                        .fullName("Dr. John Smith")
                        .email("john@example.com")
                        .username("johnsmith")
                        .passwordHash(passwordEncoder.encode("lecturer123"))
                        .build();
                lecturerRepository.save(lecturer);
                log.info("Seeded initial lecturer account: johnsmith");
            }
        );
    }
}
