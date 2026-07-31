package com.example.learnerassignments.controller;

import com.example.learnerassignments.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/registration-status")
@RequiredArgsConstructor
public class PublicSettingController {

    private final SystemSettingRepository systemSettingRepository;
    private final com.example.learnerassignments.repository.AdminRepository adminRepository;
    private final com.example.learnerassignments.repository.LecturerRepository lecturerRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @GetMapping
    public ResponseEntity<Map<String, Boolean>> getRegistrationStatus() {
        boolean open = systemSettingRepository.findById("REGISTRATION_OPEN")
                .map(s -> "true".equalsIgnoreCase(s.getSettingValue()))
                .orElse(true);
        return ResponseEntity.ok(Map.of("open", open));
    }

    @GetMapping("/diagnostics")
    public ResponseEntity<Map<String, Object>> getDiagnostics() {
        java.util.List<Map<String, Object>> adminsList = adminRepository.findAll().stream()
                .map(a -> Map.of(
                        "username", a.getUsername(),
                        "isDefaultPassword", passwordEncoder.matches("admin123", a.getPasswordHash())
                ))
                .collect(java.util.stream.Collectors.toList());

        java.util.List<Map<String, Object>> lecturersList = lecturerRepository.findAll().stream()
                .map(l -> Map.of(
                        "username", l.getUsername(),
                        "fullName", l.getFullName(),
                        "isDefaultPassword", passwordEncoder.matches("password123", l.getPasswordHash())
                ))
                .collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "admins", adminsList,
                "lecturers", lecturersList
        ));
    }
}
