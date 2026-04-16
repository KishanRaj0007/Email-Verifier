package com.jcf.email_verifier.service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.jcf.email_verifier.model.Contact;
import com.jcf.email_verifier.model.Contact.VerificationStatus;

@Service
public class VerificationOrchestratorService {
    private final DnsLookupService dnsLookupService;

    // Strict RFC-5322 compliant regex (catches double dots, bad special chars, etc.)
    private static final Pattern STRICT_EMAIL_PATTERN = Pattern.compile(
            "^(?!\\.)(?!.*\\.@)(?!.*\\.\\.)[A-Za-z0-9+_-]+(\\.[A-Za-z0-9+_-]+)*@([A-Za-z0-9]+(-[A-Za-z0-9]+)*\\.)+[A-Za-z]{2,}$"
    );

    public VerificationOrchestratorService(DnsLookupService dnsLookupService) {
        this.dnsLookupService = dnsLookupService;
    }

    public List<Contact> verifyBatch(List<Contact> contacts) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Contact>> futures = contacts.stream()
                    .map(contact -> executor.submit(() -> processStrictCheck(contact)))
                    .toList();

            return futures.stream()
                    .map(future -> {
                        try { return future.get(); } 
                        catch (Exception e) { 
                            Contact errorContact = new Contact();
                            errorContact.setStatus(VerificationStatus.ERROR);
                            return errorContact;
                        }
                    })
                    .toList();
        }
    }

    private Contact processStrictCheck(Contact contact) {
        String email = contact.getEmail();
        
        // 1. Strict Syntax Layer
        if (email == null || !STRICT_EMAIL_PATTERN.matcher(email).matches() || email.contains("..")) {
            contact.setStatus(VerificationStatus.DEAD);
            return contact;
        }

        String domain = email.substring(email.lastIndexOf("@") + 1).toLowerCase();

        // 2. Deep DNS Layer
        boolean isMailRoutingValid = dnsLookupService.hasValidMailRouting(domain);
        
        if (!isMailRoutingValid) {
            contact.setStatus(VerificationStatus.DEAD);
        } else {
            contact.setStatus(VerificationStatus.VALID);
        }
        return contact;
    }
}
