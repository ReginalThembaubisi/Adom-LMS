package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.Moderator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModeratorRepository extends JpaRepository<Moderator, Long> {
    Optional<Moderator> findByUsername(String username);
    boolean existsByUsername(String username);
}
