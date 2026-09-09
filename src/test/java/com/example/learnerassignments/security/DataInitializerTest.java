package com.example.learnerassignments.security;

import com.example.learnerassignments.model.Admin;
import com.example.learnerassignments.repository.AdminRepository;
import com.example.learnerassignments.repository.AssessorRepository;
import com.example.learnerassignments.repository.LecturerRepository;
import com.example.learnerassignments.repository.ModeratorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seeding must not undo a password change.
 *
 * This ran on every boot and re-hashed a fixed password onto every staff account, four of
 * them written as literals in the source of a public repository. The rotation test is the
 * one that matters: an admin who changed a lecturer's password through the admin screens
 * would have seen it work, and seen it silently revert on the next restart — which, on an
 * instance that sleeps when idle, is usually minutes away.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DataInitializerTest {

    @Autowired DataInitializer dataInitializer;
    @Autowired AdminRepository adminRepository;
    @Autowired LecturerRepository lecturerRepository;
    @Autowired ModeratorRepository moderatorRepository;
    @Autowired AssessorRepository assessorRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String adminUsername;

    @BeforeEach
    void useAnIsolatedAdminAccount() {
        adminUsername = "admin-" + System.nanoTime();
        ReflectionTestUtils.setField(dataInitializer, "adminUsername", adminUsername);
        ReflectionTestUtils.setField(dataInitializer, "adminPassword", "");
        ReflectionTestUtils.setField(dataInitializer, "seedDemoAccounts", false);
        ReflectionTestUtils.setField(dataInitializer, "demoPassword", "");
        ReflectionTestUtils.setField(dataInitializer, "rotateOnBoot", false);
    }

    @Test
    @DisplayName("a changed password survives the next boot")
    void restartDoesNotUndoAPasswordChange() {
        ReflectionTestUtils.setField(dataInitializer, "adminPassword", "the-seeded-password");
        dataInitializer.run();

        // Someone changes it afterwards, the way the admin screens do.
        Admin admin = adminRepository.findByUsername(adminUsername).orElseThrow();
        admin.setPasswordHash(passwordEncoder.encode("chosen-by-a-human"));
        adminRepository.save(admin);

        dataInitializer.run();

        Admin afterRestart = adminRepository.findByUsername(adminUsername).orElseThrow();
        assertThat(passwordEncoder.matches("chosen-by-a-human", afterRestart.getPasswordHash()))
                .as("a restart must not reinstate the seeded password")
                .isTrue();
        assertThat(passwordEncoder.matches("the-seeded-password", afterRestart.getPasswordHash()))
                .isFalse();
    }

    @Test
    @DisplayName("no staff password is seeded from a value written in the source")
    void noHardcodedPasswords() {
        ReflectionTestUtils.setField(dataInitializer, "seedDemoAccounts", true);
        ReflectionTestUtils.setField(dataInitializer, "demoPassword", "");

        dataInitializer.run();

        // The four demo accounts used to be created here with lecturer123, lecturer123,
        // moderator123 and assessor123 — all readable in the repository.
        assertThat(lecturerRepository.findByUsername("janedoe")).isEmpty();
        assertThat(lecturerRepository.findByUsername("johnsmith")).isEmpty();
        assertThat(moderatorRepository.findByUsername("moderator")).isEmpty();
        assertThat(assessorRepository.findByUsername("assessor")).isEmpty();
    }

    @Test
    @DisplayName("demo staff accounts are not created unless asked for")
    void demoAccountsAreOffByDefault() {
        ReflectionTestUtils.setField(dataInitializer, "demoPassword", "a-configured-password");

        dataInitializer.run();

        assertThat(lecturerRepository.findByUsername("janedoe")).isEmpty();
        assertThat(moderatorRepository.findByUsername("moderator")).isEmpty();
        assertThat(assessorRepository.findByUsername("assessor")).isEmpty();
    }

    @Test
    @DisplayName("an admin created without a configured password gets a generated one, not a known one")
    void generatedRatherThanDefaultPassword() {
        dataInitializer.run();

        Admin admin = adminRepository.findByUsername(adminUsername).orElseThrow();
        assertThat(passwordEncoder.matches("admin123", admin.getPasswordHash()))
                .as("the old default must not be reachable")
                .isFalse();
        assertThat(admin.getPasswordHash()).isNotBlank();
    }

    @Test
    @DisplayName("rotation happens only when explicitly switched on")
    void rotationIsOptIn() {
        ReflectionTestUtils.setField(dataInitializer, "adminPassword", "first-password");
        dataInitializer.run();

        ReflectionTestUtils.setField(dataInitializer, "adminPassword", "second-password");
        dataInitializer.run();

        Admin unrotated = adminRepository.findByUsername(adminUsername).orElseThrow();
        assertThat(passwordEncoder.matches("first-password", unrotated.getPasswordHash()))
                .as("changing the configured password alone must not touch a live account")
                .isTrue();

        ReflectionTestUtils.setField(dataInitializer, "rotateOnBoot", true);
        dataInitializer.run();

        Admin rotated = adminRepository.findByUsername(adminUsername).orElseThrow();
        assertThat(passwordEncoder.matches("second-password", rotated.getPasswordHash()))
                .as("the opt-in switch must actually rotate, or there is no way back in")
                .isTrue();
    }
}
