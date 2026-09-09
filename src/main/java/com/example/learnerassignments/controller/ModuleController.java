package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.ModuleDetailResponseDto;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.security.CurrentStaff;
import com.example.learnerassignments.service.ModuleService;
import com.example.learnerassignments.service.ScopeService;
import org.springframework.security.core.Authentication;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleService moduleService;
    private final CurrentStaff currentStaff;
    private final ScopeService scopeService;

    // Staff view of a module. The learner-facing equivalents now live on /api/me, where the
    // learner comes from the session instead of the path: a student number in the URL let
    // anyone read anyone's enrolment, timeline and per-slot submission state.
    @GetMapping("/modules/{id}")
    public ResponseEntity<ModuleDetailResponseDto> getModuleDetails(@PathVariable Long id, Authentication auth) {
        // A role check cannot express "only the modules your assigned learners are enrolled
        // on", so the narrowing happens here where the assignment rows can be consulted.
        // Out of scope reads as not found rather than forbidden, as everywhere else.
        boolean permitted = currentStaff.resolve(auth)
                .map(principal -> scopeService.canAccessModule(principal, id))
                .orElse(false);
        if (!permitted) {
            throw new ResourceNotFoundException("Module not found with id: " + id);
        }
        return ResponseEntity.ok(moduleService.getModuleDetails(id, null));
    }
}
