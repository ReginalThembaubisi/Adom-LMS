package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.PoeCompletenessDtos.CompletenessDashboard;
import com.example.learnerassignments.dto.PoeCompletenessDtos.LearnerChecklist;
import com.example.learnerassignments.dto.PoeCompletenessDtos.LearnershipRequirements;
import com.example.learnerassignments.dto.PoeCompletenessDtos.UpdateRequirementRequest;
import com.example.learnerassignments.dto.PoePortfolioDtos.LearnerPortfolioTree;
import com.example.learnerassignments.service.PoeCompletenessService;
import com.example.learnerassignments.service.PoePortfolioBrowserService;
import com.example.learnerassignments.service.PoeRequirementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * The completeness dashboard.
 *
 * Under /api/admin, so ADMIN only — SecurityConfig already covers the whole prefix. The screen
 * reports on every learner in a learnership, which is more than any one facilitator is scoped to
 * see; opening it to LECTURER would need the scoping ScopeService applies elsewhere, and that is a
 * decision to make deliberately rather than by leaving a path off the deny list.
 */
@RestController
@RequestMapping("/api/admin/poe")
@RequiredArgsConstructor
public class PoeDashboardController {

    private final PoeCompletenessService completenessService;
    private final PoeRequirementService requirementService;
    private final PoePortfolioBrowserService portfolioBrowserService;

    @GetMapping("/completeness")
    public ResponseEntity<CompletenessDashboard> completeness(
            @RequestParam(required = false) Long learnershipId,
            @RequestParam(required = false) String cohort) {
        return ResponseEntity.ok(completenessService.dashboard(learnershipId, cohort));
    }

    @GetMapping("/completeness/learners/{learnerId}")
    public ResponseEntity<LearnerChecklist> learnerChecklist(@PathVariable Long learnerId) {
        LearnerChecklist checklist = completenessService.checklistFor(learnerId);
        return checklist == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(checklist);
    }

    /**
     * The full PoE folder structure for one learner, resolved exactly as an export would build
     * it — see {@code PoeExportService.resolvePortfolio} and {@code PoePortfolioBrowserService}'s
     * class doc for why this can never disagree with a zip of the same learner.
     */
    @GetMapping("/portfolio/learners/{learnerId}")
    public ResponseEntity<LearnerPortfolioTree> portfolio(@PathVariable Long learnerId) {
        return ResponseEntity.ok(portfolioBrowserService.browse(learnerId));
    }

    @GetMapping("/learnerships/{learnershipId}/requirements")
    public ResponseEntity<LearnershipRequirements> requirements(@PathVariable Long learnershipId) {
        LearnershipRequirements requirements = requirementService.list(learnershipId);
        return requirements == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(requirements);
    }

    @PutMapping("/learnerships/{learnershipId}/requirements")
    public ResponseEntity<?> updateRequirement(@PathVariable Long learnershipId,
                                               @RequestBody UpdateRequirementRequest request,
                                               Authentication auth) {
        try {
            LearnershipRequirements updated = requirementService.update(
                    learnershipId, request, auth != null ? auth.getName() : "unknown");
            return updated == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }
}
