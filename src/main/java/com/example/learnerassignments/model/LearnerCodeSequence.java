package com.example.learnerassignments.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "learner_code_sequences")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearnerCodeSequence {

    @Id
    @Column(name = "year_val", nullable = false)
    private Integer yearVal;

    @Column(name = "last_seq", nullable = false)
    private Integer lastSeq;
}
