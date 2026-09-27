package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.AppointmentRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentRequestRepository extends JpaRepository<AppointmentRequest, Long> {

    List<AppointmentRequest> findAllByOrderByCreatedAtDesc();
}
