package com.example.learnerassignments.controller;

import com.example.learnerassignments.dto.SignatureDtos.VerifyResponse;
import com.example.learnerassignments.service.SignatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one endpoint anyone — signed in or not — may call to check a signature.
 *
 * By design this returns nothing beyond {@link VerifyResponse}: document type, signer role,
 * signature date, and whether the hash still matches. No learner name, no ID number, no file
 * content — {@link SignatureService#verify} enforces that at the source, this controller adds
 * nothing to the shape it returns.
 */
@RestController
@RequestMapping("/api/verify")
@RequiredArgsConstructor
public class SignatureVerificationController {

    private final SignatureService signatureService;

    @GetMapping("/{code}")
    public ResponseEntity<VerifyResponse> verify(@PathVariable String code) {
        return ResponseEntity.ok(signatureService.verify(code));
    }
}
