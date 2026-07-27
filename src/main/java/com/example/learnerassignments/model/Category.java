package com.example.learnerassignments.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "categories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"lecturer", "modules", "learnership"})
@ToString(exclude = {"lecturer", "modules", "learnership"})
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_type", nullable = false, length = 50)
    private String categoryType; // e.g. "FUNDAMENTAL", "CORE", "ELECTIVE"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learnership_id")
    private Learnership learnership;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecturer_id")
    private Lecturer lecturer;

    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    private List<Module> modules;
}
