package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.Message;
import com.example.learnerassignments.model.SenderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByLearnerIdAndLecturerIdOrderByCreatedAtAsc(Long learnerId, Long lecturerId);

    List<Message> findByLecturerIdOrderByCreatedAtDesc(Long lecturerId);

    List<Message> findByLearnerIdOrderByCreatedAtDesc(Long learnerId);

    long countByLearnerIdAndLecturerIdAndSenderTypeAndReadAtIsNull(Long learnerId, Long lecturerId, SenderType senderType);

    long countByLecturerIdAndSenderTypeAndReadAtIsNull(Long lecturerId, SenderType senderType);

    long countByLearnerIdAndSenderTypeAndReadAtIsNull(Long learnerId, SenderType senderType);

    @Modifying
    @Query("UPDATE Message m SET m.readAt = :now WHERE m.learner.id = :learnerId AND m.lecturer.id = :lecturerId AND m.senderType = :senderType AND m.readAt IS NULL")
    void markThreadRead(@Param("learnerId") Long learnerId, @Param("lecturerId") Long lecturerId,
                         @Param("senderType") SenderType senderType, @Param("now") LocalDateTime now);
}
