package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.SignableType;
import com.example.learnerassignments.model.SignatureEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SignatureEventRepository extends JpaRepository<SignatureEvent, Long> {

    Optional<SignatureEvent> findByVerificationCode(String verificationCode);

    /** The one signature currently in force for a row, if any — revoked ones don't count. */
    Optional<SignatureEvent> findFirstBySignableTypeAndSignableIdAndRevokedAtIsNull(
            SignableType signableType, Long signableId);

    /** Every signature ever recorded against a row, revoked included — the audit trail. */
    List<SignatureEvent> findBySignableTypeAndSignableIdOrderBySignedAtDesc(
            SignableType signableType, Long signableId);
}
