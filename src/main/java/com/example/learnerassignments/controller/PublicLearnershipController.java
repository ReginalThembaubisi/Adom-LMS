package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.LearnershipResponseDto;
import com.example.learnerassignments.repository.LearnershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/learnerships")
@RequiredArgsConstructor
public class PublicLearnershipController {

    private final LearnershipRepository learnershipRepository;

    @GetMapping
    public ResponseEntity<List<LearnershipResponseDto>> getPublicLearnerships() {
        List<LearnershipResponseDto> list = learnershipRepository.findAll().stream()
                .map(l -> LearnershipResponseDto.builder()
                        .id(l.getId())
                        .name(l.getName())
                        .qualificationCode(l.getQualificationCode())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }
}
