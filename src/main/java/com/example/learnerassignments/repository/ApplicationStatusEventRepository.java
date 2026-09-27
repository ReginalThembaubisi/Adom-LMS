package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.ApplicationStatusEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationStatusEventRepository extends JpaRepository<ApplicationStatusEvent, Long> {
}
