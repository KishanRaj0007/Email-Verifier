package com.jcf.email_verifier.service;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.springframework.stereotype.Service;
@Service
public class DnsLookupService {

    public boolean hasValidMailRouting(String domain) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
            
            // FIX 1: BYPASS LOCAL ROUTER RATE LIMITS. Force Java to use Google (8.8.8.8) and Cloudflare (1.1.1.1) DNS servers
            env.put(Context.PROVIDER_URL, "dns://8.8.8.8 dns://8.8.4.4 dns://1.1.1.1");
            
            // FIX 2: RELAX TIMEOUTS FOR HIGH CONCURRENCY
            env.put("com.sun.jndi.dns.timeout.initial", "4000"); // 4 seconds
            env.put("com.sun.jndi.dns.timeout.retries", "3");    // Try 3 times before giving up
            
            DirContext dirContext = new InitialDirContext(env);

            // Layer 1: Check for MX Records
            Attributes mxAttributes = dirContext.getAttributes(domain, new String[]{"MX"});
            Attribute mxAttribute = mxAttributes.get("MX");

            if (mxAttribute != null && mxAttribute.size() > 0) {
                NamingEnumeration<?> enumeration = mxAttribute.getAll();
                while (enumeration.hasMore()) {
                    String record = enumeration.next().toString();
                    
                    // The "Null MX" Trap Check
                    if (record.trim().equals("0 .") || record.endsWith(" .")) {
                        return false; 
                    }
                }
                return true;
            }

            // Layer 2: Fallback to A Record (IPv4)
            Attributes aAttributes = dirContext.getAttributes(domain, new String[]{"A"});
            if (aAttributes.get("A") != null && aAttributes.get("A").size() > 0) {
                return true; 
            }

            // Layer 3: FIX 3 - Fallback to AAAA Record (IPv6)
            Attributes aaaaAttributes = dirContext.getAttributes(domain, new String[]{"AAAA"});
            if (aaaaAttributes.get("AAAA") != null && aaaaAttributes.get("AAAA").size() > 0) {
                return true; 
            }

        } catch (javax.naming.CommunicationException e) {
            // This means the DNS query timed out entirely (Network failure).
            // It is safer to assume TRUE (Valid) here so we don't get a False Negative,
            System.out.println("⚠ DNS Timeout for: " + domain + " (Assuming risky/valid to avoid false negative)");
            return true; 
            
        } catch (javax.naming.NameNotFoundException e) {
            // This is the absolute confirmation that the domain DOES NOT EXIST on the internet.
            return false;
            
        } catch (NamingException e) {
            // Generic DNS failure
            return false;
        }

        return false; // No MX, No A, No AAAA found
    }
}
