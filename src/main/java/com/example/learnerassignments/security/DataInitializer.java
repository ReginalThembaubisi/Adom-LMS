package com.example.learnerassignments.security;

import com.example.learnerassignments.model.Admin;
import com.example.learnerassignments.model.Lecturer;
import com.example.learnerassignments.model.Moderator;
import com.example.learnerassignments.model.Assessor;
import com.example.learnerassignments.repository.AdminRepository;
import com.example.learnerassignments.repository.LecturerRepository;
import com.example.learnerassignments.repository.ModeratorRepository;
import com.example.learnerassignments.repository.AssessorRepository;
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
    private final ModeratorRepository moderatorRepository;
    private final AssessorRepository assessorRepository;
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

        moderatorRepository.findByUsername("moderator").ifPresentOrElse(
            mod -> {
                mod.setPasswordHash(passwordEncoder.encode("moderator123"));
                moderatorRepository.save(mod);
            },
            () -> {
                Moderator mod = Moderator.builder()
                        .fullName("Official Moderator")
                        .email("moderator@example.com")
                        .username("moderator")
                        .passwordHash(passwordEncoder.encode("moderator123"))
                        .build();
                moderatorRepository.save(mod);
                log.info("Seeded initial moderator account: moderator");
            }
        );

        assessorRepository.findByUsername("assessor").ifPresentOrElse(
            ass -> {
                ass.setPasswordHash(passwordEncoder.encode("assessor123"));
                assessorRepository.save(ass);
            },
            () -> {
                Assessor ass = Assessor.builder()
                        .fullName("Official Assessor")
                        .email("assessor@example.com")
                        .username("assessor")
                        .passwordHash(passwordEncoder.encode("assessor123"))
                        .build();
                assessorRepository.save(ass);
                log.info("Seeded initial assessor account: assessor");
            }
        );
    }
}
