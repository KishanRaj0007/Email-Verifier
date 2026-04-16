package com.jcf.email_verifier.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jcf.email_verifier.model.Contact;
import com.jcf.email_verifier.service.VerificationOrchestratorService;

@RestController
@RequestMapping("/api/v1")
public class VerificationController {
    private final VerificationOrchestratorService orchestratorService;
    public VerificationController(VerificationOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }
    // receive from python
    @PostMapping("/verify-batch")
    public ResponseEntity<List<Contact>> verifyBatch(@RequestBody List<Contact> contacts) {
        if (contacts == null || contacts.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        // Send to virtual threads
        long startTime = System.currentTimeMillis();
        List<Contact> verifiedContacts = orchestratorService.verifyBatch(contacts);
        long endTime = System.currentTimeMillis();
        System.out.println("Verified " + contacts.size() + " emails in " + (endTime - startTime) + "ms");
        // Return the updated JSON array back to Python
        return ResponseEntity.ok(verifiedContacts);
    }
    
    @GetMapping("/health")
    public String healthCheck() {
        return "JCF Email Verifier is running on Java 21 Virtual Threads!";
    }
}
