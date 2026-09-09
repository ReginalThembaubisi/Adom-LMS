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

import java.security.SecureRandom;
import java.util.Base64;
import java.util.function.Supplier;

/**
 * Seeds the accounts needed to sign in to a fresh installation.
 *
 * It used to re-hash a fixed password onto every staff account on every single boot, with
 * four of those passwords written as literals in this file — a public repository. Two
 * consequences, both live in production until this change:
 *
 *  - Anyone who could read the repository held working lecturer, moderator and assessor
 *    credentials for the running system.
 *  - Changing a staff password was impossible. The admin screens write a new hash, and the
 *    next restart overwrote it. On a Free instance that sleeps when idle, that is often
 *    minutes later, so a rotation would appear to succeed and quietly undo itself.
 *
 * Now: an account that already exists is never touched. Nothing is seeded from a literal.
 * Rotation happens through the admin screens and sticks, or — for the admin account, which
 * has no self-service screen — through an explicit, deliberately loud opt-in below.
 */
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
    private String adminUsername;

    /** Blank means "generate one and print it once", never a default everybody knows. */
    @Value("${FACILITATOR_PASSWORD:}")
    private String adminPassword;

    /**
     * The demo lecturer, moderator and assessor accounts. Off by default: a fresh deployment
     * should not mint working staff logins nobody asked for. Existing installations already
     * have these accounts, and turning this off does not remove them — change their passwords
     * through the admin screens, or delete the ones you do not use.
     */
    @Value("${staff.seed.demo-accounts:false}")
    private boolean seedDemoAccounts;

    @Value("${staff.seed.demo-password:}")
    private String demoPassword;

    /**
     * One-shot rotation for accounts that already exist, for the admin account in particular,
     * since it has no screen to change its own password. Set it, set FACILITATOR_PASSWORD to
     * the new value, deploy, confirm the log line, then set it back to false. It is off by
     * default precisely so that a restart cannot silently reset a password again.
     */
    @Value("${staff.seed.rotate-on-boot:false}")
    private boolean rotateOnBoot;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public void run(String... args) {
        seedAdmin();

        if (seedDemoAccounts) {
            if (demoPassword == null || demoPassword.isBlank()) {
                log.warn("staff.seed.demo-accounts is on but staff.seed.demo-password is not set. "
                        + "Skipping the demo staff accounts rather than inventing a password for them.");
            } else {
                seedIfAbsent("lecturer janedoe",
                        () -> lecturerRepository.findByUsername("janedoe").isPresent(),
                        () -> lecturerRepository.save(Lecturer.builder()
                                .fullName("Prof. Jane Doe").email("jane@example.com")
                                .username("janedoe").passwordHash(passwordEncoder.encode(demoPassword))
                                .build()));
                seedIfAbsent("lecturer johnsmith",
                        () -> lecturerRepository.findByUsername("johnsmith").isPresent(),
                        () -> lecturerRepository.save(Lecturer.builder()
                                .fullName("Dr. John Smith").email("john@example.com")
                                .username("johnsmith").passwordHash(passwordEncoder.encode(demoPassword))
                                .build()));
                seedIfAbsent("moderator",
                        () -> moderatorRepository.findByUsername("moderator").isPresent(),
                        () -> moderatorRepository.save(Moderator.builder()
                                .fullName("Official Moderator").email("moderator@example.com")
                                .username("moderator").passwordHash(passwordEncoder.encode(demoPassword))
                                .build()));
                seedIfAbsent("assessor",
                        () -> assessorRepository.findByUsername("assessor").isPresent(),
                        () -> assessorRepository.save(Assessor.builder()
                                .fullName("Official Assessor").email("assessor@example.com")
                                .username("assessor").passwordHash(passwordEncoder.encode(demoPassword))
                                .build()));
            }
        }
    }

    private void seedAdmin() {
        var existing = adminRepository.findByUsername(adminUsername);

        if (existing.isPresent()) {
            if (rotateOnBoot && adminPassword != null && !adminPassword.isBlank()) {
                Admin admin = existing.get();
                admin.setPasswordHash(passwordEncoder.encode(adminPassword));
                adminRepository.save(admin);
                log.warn("Rotated the password for admin account '{}' because "
                        + "staff.seed.rotate-on-boot is enabled. Set it back to false now — "
                        + "while it is on, every restart re-applies FACILITATOR_PASSWORD and no "
                        + "other password change to this account can stick.", adminUsername);
            } else {
                log.info("Admin account '{}' already exists; leaving its password alone.", adminUsername);
            }
            return;
        }

        String password = adminPassword;
        boolean generated = password == null || password.isBlank();
        if (generated) {
            password = generatePassword();
        }

        adminRepository.save(Admin.builder()
                .username(adminUsername)
                .passwordHash(passwordEncoder.encode(password))
                .build());

        if (generated) {
            // Printed once, on the boot that created the account. A generated secret in the
            // log is far better than a default every reader of this repository knows.
            log.warn("Created admin account '{}' with a generated password: {}  "
                    + "Sign in and change it, and set FACILITATOR_PASSWORD. This is the only "
                    + "time it will be printed.", adminUsername, password);
        } else {
            log.info("Created admin account '{}' from the configured password.", adminUsername);
        }
    }

    private void seedIfAbsent(String description, Supplier<Boolean> exists, Runnable create) {
        if (exists.get()) {
            log.info("Demo account {} already exists; leaving its password alone.", description);
            return;
        }
        create.run();
        log.info("Seeded demo account {}.", description);
    }

    private String generatePassword() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
