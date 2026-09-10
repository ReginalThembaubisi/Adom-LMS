package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdAndUserRoleOrderByCreatedAtDesc(Long userId, String userRole);

    long countByUserIdAndUserRoleAndReadAtIsNull(Long userId, String userRole);
}
