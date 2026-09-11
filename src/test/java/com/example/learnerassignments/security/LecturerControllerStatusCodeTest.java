package com.example.learnerassignments.security;

import com.example.learnerassignments.model.Lecturer;
import com.example.learnerassignments.repository.LecturerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code LecturerController} was the one controller in the codebase that never adopted
 * {@link com.example.learnerassignments.exception.ResourceNotFoundException} — every not-found
 * case threw a bare {@code RuntimeException}, which {@code GlobalExceptionHandler}'s catch-all
 * could only answer as 500. Found during the Phase 9 exception-handling audit; every sibling
 * controller (assessor, moderator) already got this right.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LecturerControllerStatusCodeTest {

    @Autowired MockMvc mockMvc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired LecturerRepository lecturerRepository;

    private static final String PW = "staff-password";
    private String lecturerUser;

    @BeforeEach
    void seed() {
        lecturerUser = "lecturer-" + System.nanoTime();
        lecturerRepository.save(Lecturer.builder()
                .fullName("A Lecturer").username(lecturerUser)
                .passwordHash(passwordEncoder.encode(PW)).createdAt(LocalDateTime.now()).build());
    }

    @Test
    @DisplayName("Closing a session that does not exist is 404, not the 500 a bare RuntimeException used to produce")
    void missingSessionIs404NotServerError() throws Exception {
        mockMvc.perform(put("/api/lecturer/sessions/999999/close")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic(lecturerUser, PW)))
                .andExpect(status().isNotFound());
    }
}
