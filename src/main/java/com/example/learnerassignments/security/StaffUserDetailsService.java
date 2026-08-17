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
    private final ModeratorRepository moderatorRepository;
    private final AssessorRepository assessorRepository;

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

        // 3. Search Moderator repository
        Optional<Moderator> moderatorOpt = moderatorRepository.findByUsername(username);
        if (moderatorOpt.isPresent()) {
            Moderator moderator = moderatorOpt.get();
            return User.builder()
                    .username(moderator.getUsername())
                    .password(moderator.getPasswordHash())
                    .roles("MODERATOR")
                    .build();
        }

        // 4. Search Assessor repository
        Optional<Assessor> assessorOpt = assessorRepository.findByUsername(username);
        if (assessorOpt.isPresent()) {
            Assessor assessor = assessorOpt.get();
            return User.builder()
                    .username(assessor.getUsername())
                    .password(assessor.getPasswordHash())
                    .roles("ASSESSOR")
                    .build();
        }

        throw new UsernameNotFoundException("User not found with username: " + username);
    }
}
