package com.example.learnerassignments.repository;

import com.example.learnerassignments.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByCategoryTypeIgnoreCase(String categoryType);
    List<Category> findByLecturerId(Long lecturerId);
}
