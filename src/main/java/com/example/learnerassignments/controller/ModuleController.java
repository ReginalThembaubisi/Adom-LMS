package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.ModuleDetailResponseDto;
import com.example.learnerassignments.service.ModuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleService moduleService;

    // Staff view of a module. The learner-facing equivalents now live on /api/me, where the
    // learner comes from the session instead of the path: a student number in the URL let
    // anyone read anyone's enrolment, timeline and per-slot submission state.
    @GetMapping("/modules/{id}")
    public ResponseEntity<ModuleDetailResponseDto> getModuleDetails(@PathVariable Long id) {
        return ResponseEntity.ok(moduleService.getModuleDetails(id, null));
    }
}
