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
        java.util.List<java.util.Map<String, Object>> adminsList = new java.util.ArrayList<>();
        for (com.example.learnerassignments.model.Admin a : adminRepository.findAll()) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("username", a.getUsername());
            map.put("isDefaultPassword", passwordEncoder.matches("admin123", a.getPasswordHash()));
            adminsList.add(map);
        }

        java.util.List<java.util.Map<String, Object>> lecturersList = new java.util.ArrayList<>();
        for (com.example.learnerassignments.model.Lecturer l : lecturerRepository.findAll()) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("username", l.getUsername());
            map.put("fullName", l.getFullName());
            map.put("isDefaultPassword", passwordEncoder.matches("password123", l.getPasswordHash()));
            lecturersList.add(map);
        }

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("admins", adminsList);
        response.put("lecturers", lecturersList);

        return ResponseEntity.ok(response);
    }
}
