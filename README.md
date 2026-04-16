## **How to Use It**

Follow these steps to set up and run the service locally:

1. **Install Java 21:** Download Java 21 from Eclipse Temurin at [https://adoptium.net/temurin/releases/?version=21](https://adoptium.net/temurin/releases/?version=21).  
2. **Configure Environment:** Add the Java installation path to your system's PATH environment variable.  
3. **Verify Installation:** Open your command prompt (cmd) and run `java -version` to ensure Java is visible and correctly installed.  
4. **Clone the Repository:** Clone this project to your local machine.  
5. **Run the Application:** Start the Spring Boot application (default port is typically 8080).  
6. **Make Requests:** Send a POST request containing your email data to the verification endpoint (“api/v1/verify-batch”). It is highly recommended to use the dedicated broker script for this: [Email Verifier Broker](https://github.com/KishanRaj0007/Email-verifier-broker).

## **About**

This service is a high-speed, highly concurrent microservice designed to filter and validate large batches of email addresses. Instead of relying on traditional, slow, and often blocked network protocols, it rapidly identifies definitively "dead" or mathematically undeliverable emails, allowing you to sanitize your data lists efficiently.

## **Mechanism**

The application achieves massive concurrency by leveraging Java 21 Virtual Threads (Project Loom). The verification pipeline consists of the following layers:

* **Format Validation:** Applies strict, RFC-compliant Regex checks to filter out malformed addresses immediately.  
* **DNS Resolution:** Utilizes the Java Naming and Directory Interface (JNDI) API over Port 53 to query the internet's Domain Name System. It checks if the domain associated with the email address actually possesses the infrastructure (Mail Exchange or Web servers) required to receive messages.

## **Challenges and Solutions**

During the development of this microservice, two major network infrastructure challenges were identified and resolved.

### **Issue 1: ISP Port 25 Blocking**

**The Problem:** Most residential Internet Service Providers (ISPs) completely block outgoing traffic on Port 25 to prevent spam. This makes traditional SMTP handshakes impossible from a local machine. This was verified by running the following command in PowerShell, which resulted in a connection failure: `Test-NetConnection -ComputerName gmail-smtp-in.l.google.com -Port 25`

**The Solution:** Completely eliminate the reliance on SMTP. Instead, the service relies entirely on Port 53 (DNS), which is never blocked. The validation flow now relies on:

1. Strict Regex formatting checks.  
2. DNS checks (MX and A records) via the JNDI API to verify if a mail server exists for the domain.  
3. Client-side de-duplication to reduce unnecessary load.

### **Issue 2: High Concurrency Router Drops and Timeouts**

**The Problem:** Because Java Virtual Threads execute tasks so rapidly, the application fires thousands of DNS requests simultaneously. Standard local Wi-Fi routers interpret this as a flood attack and silently drop the requests. Consequently, Java receives no answer and falsely marks valid emails as "DEAD". Furthermore, the initial code only fell back to IPv4 (A) records and had a strict timeout of 2000ms. Under heavy load, DNS lookups easily take 3-4 seconds, causing the app to give up at 2.1 seconds and fail.

**The Solution:** Bypass the local network constraints and implement smarter network fallbacks.

* **Bypass Local DNS (`Context.PROVIDER_URL`):** We instructed Java to bypass the local ISP/router DNS entirely. The application now routes queries directly to Google's and Cloudflare's massive public backend infrastructure (e.g., 8.8.8.8), which can easily handle thousands of concurrent queries without dropping them.  
* **Smarter Error Handling (`CommunicationException` Catch):** Previously, a network timeout resulted in a hard "False" (Dead) return. Now, if the network drops the connection entirely, the system catches the `CommunicationException`, prints a warning, and assumes the email is potentially valid. This prevents losing a good lead due to a temporary Wi-Fi hiccup.  
* **Relaxed Timeouts:** Timeouts were increased to accommodate high-concurrency processing delays.  
* **AAAA Support:** Added fallback queries for IPv6 (AAAA) records, ensuring servers running strictly on modern IPv6 infrastructure are no longer missed.