package com.example.learnerassignments.security;

import com.example.learnerassignments.model.Admin;
import com.example.learnerassignments.model.Lecturer;
import com.example.learnerassignments.repository.AdminRepository;
import com.example.learnerassignments.repository.LecturerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StaffUserDetailsService implements UserDetailsService {

    private final AdminRepository adminRepository;
    private final LecturerRepository lecturerRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. Search Admin repository first
        Optional<Admin> adminOpt = adminRepository.findByUsername(username);
        if (adminOpt.isPresent()) {
            Admin admin = adminOpt.get();
            return User.builder()
                    .username(admin.getUsername())
                    .password(admin.getPasswordHash())
                    .roles("ADMIN")
                    .build();
        }

        // 2. Search Lecturer repository next
        Optional<Lecturer> lecturerOpt = lecturerRepository.findByUsername(username);
        if (lecturerOpt.isPresent()) {
            Lecturer lecturer = lecturerOpt.get();
            return User.builder()
                    .username(lecturer.getUsername())
                    .password(lecturer.getPasswordHash())
                    .roles("LECTURER")
                    .build();
        }

        throw new UsernameNotFoundException("User not found with username: " + username);
    }
}
