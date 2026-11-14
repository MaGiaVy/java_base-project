package com.example.client;

import java.io.FileInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class ServerCertificateValidatorTest {

    public static void main(String[] args) {
        System.out.println("=== RUNNING ServerCertificateValidatorTest ===");

        try (FileInputStream in = new FileInputStream("client/certificates/server.crt")) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(in);

            // Test 1: localhost should PASS
            try {
                ServerCertificateValidator.validate(cert, "localhost");
                System.out.println("[PASS] Validation with 'localhost' succeeded as expected.");
            } catch (CertificateException e) {
                System.err.println("[FAIL] 'localhost' validation failed: " + e.getMessage());
                System.exit(1);
            }

            // Test 2: 127.0.0.1 should PASS
            try {
                ServerCertificateValidator.validate(cert, "127.0.0.1");
                System.out.println("[PASS] Validation with '127.0.0.1' succeeded as expected.");
            } catch (CertificateException e) {
                System.err.println("[FAIL] '127.0.0.1' validation failed: " + e.getMessage());
                System.exit(1);
            }

            // Test 3: evil.com should FAIL with CertificateException (TC03)
            try {
                ServerCertificateValidator.validate(cert, "evil.com");
                System.err.println("[FAIL] Validation with 'evil.com' did NOT throw CertificateException!");
                System.exit(1);
            } catch (CertificateException e) {
                System.out.println("[PASS] 'evil.com' correctly rejected: " + e.getMessage());
            }

            // Test 4: wrong IP should FAIL with CertificateException (TC03)
            try {
                ServerCertificateValidator.validate(cert, "192.168.1.99");
                System.err.println("[FAIL] Validation with wrong IP did NOT throw CertificateException!");
                System.exit(1);
            } catch (CertificateException e) {
                System.out.println("[PASS] '192.168.1.99' correctly rejected: " + e.getMessage());
            }

            System.out.println("=== ALL VALIDATOR TESTS PASSED! ===");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
