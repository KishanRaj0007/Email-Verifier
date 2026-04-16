package com.jcf.email_verifier.model;

import lombok.Data;

@Data
public class Contact {
    private String email;
    private String name;
    private String phone;
    private int score;
    private String url;
    
    private VerificationStatus status = VerificationStatus.UNVERIFIED;
    public enum VerificationStatus {
        UNVERIFIED,      // Default state before processing
        VALID,           // Server responded with 250 OK
        DEAD,            // No MX record, or server responded with 550 User Unknown
        // RISKY_CATCH_ALL, // Server accepts everything (might bounce later)
        ERROR            // Connection timeout or internal error
    }
}
