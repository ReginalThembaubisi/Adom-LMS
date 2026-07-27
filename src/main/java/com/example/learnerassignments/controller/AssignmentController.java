package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.AssignmentResponse;
import com.example.learnerassignments.dto.AssignmentSubmissionOverviewResponse;
import com.example.learnerassignments.dto.CreateAssignmentRequest;
import com.example.learnerassignments.service.AssignmentService;
import com.example.learnerassignments.service.SubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final SubmissionService submissionService;

    @PostMapping
    public ResponseEntity<AssignmentResponse> createAssignment(@Valid @RequestBody CreateAssignmentRequest request) {
        AssignmentResponse response = assignmentService.createAssignment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AssignmentResponse>> getAllAssignments() {
        List<AssignmentResponse> assignments = assignmentService.getAllAssignments();
        return ResponseEntity.ok(assignments);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssignmentResponse> getAssignmentById(@PathVariable Long id) {
        AssignmentResponse assignment = assignmentService.getAssignmentById(id);
        return ResponseEntity.ok(assignment);
    }

    @GetMapping("/{id}/submissions")
    public ResponseEntity<AssignmentSubmissionOverviewResponse> getAssignmentSubmissions(@PathVariable Long id) {
        AssignmentSubmissionOverviewResponse overview = submissionService.getAssignmentSubmissionsOverview(id);
        return ResponseEntity.ok(overview);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAssignment(@PathVariable Long id) {
        assignmentService.deleteAssignment(id);
        return ResponseEntity.noContent().build();
    }
}
