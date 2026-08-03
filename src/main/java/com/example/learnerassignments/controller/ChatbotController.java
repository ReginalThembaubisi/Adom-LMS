package com.example.learnerassignments.controller;

import com.example.learnerassignments.service.ChatbotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping("/ask")
    public ResponseEntity<Map<String, String>> askChatbot(@RequestBody Map<String, String> payload) {
        String query = payload.get("query");
        String studentNumber = payload.get("studentNumber");
        
        if (studentNumber == null || studentNumber.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("response", "Student number is required."));
        }
        
        String reply = chatbotService.generateResponse(query, studentNumber);
        return ResponseEntity.ok(Map.of("response", reply));
    }
}
