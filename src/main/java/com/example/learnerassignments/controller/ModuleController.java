package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.ModuleDetailResponseDto;
import com.example.learnerassignments.dto.ModuleResponseDto;
import com.example.learnerassignments.dto.TimelineResponseDto;
import com.example.learnerassignments.service.ModuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleService moduleService;

    @GetMapping("/learners/{studentNumber}/modules")
    public ResponseEntity<List<ModuleResponseDto>> getEnrolledModules(@PathVariable String studentNumber) {
        List<ModuleResponseDto> modules = moduleService.getEnrolledModules(studentNumber);
        return ResponseEntity.ok(modules);
    }

    @GetMapping("/modules/{id}")
    public ResponseEntity<ModuleDetailResponseDto> getModuleDetails(
            @PathVariable Long id,
            @RequestParam(value = "studentNumber", required = false) String studentNumber) {
        ModuleDetailResponseDto details = moduleService.getModuleDetails(id, studentNumber);
        return ResponseEntity.ok(details);
    }

    @GetMapping("/learners/{studentNumber}/timeline")
    public ResponseEntity<List<TimelineResponseDto>> getLearnerTimeline(@PathVariable String studentNumber) {
        List<TimelineResponseDto> timeline = moduleService.getLearnerTimeline(studentNumber);
        return ResponseEntity.ok(timeline);
    }
}
