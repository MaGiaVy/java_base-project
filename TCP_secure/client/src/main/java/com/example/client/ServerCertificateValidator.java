package com.example.client;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * X.509 Server Certificate Validator for Secure TCP Chat.
 * Validates validity dates and checks expected hostname against
 * Subject Alternative Names (SAN - DNS & IP) and fallback Common Name (CN).
 */
public class ServerCertificateValidator {

    private static final int SAN_TYPE_DNS = 2;
    private static final int SAN_TYPE_IP = 7;
    private static final Pattern CN_PATTERN = Pattern.compile("CN=([^,]+)", Pattern.CASE_INSENSITIVE);

    /**
     * Validates the server certificate attached to the active SSLSocket session.
     *
     * @param socket       the connected and handshaked SSLSocket
     * @param expectedHost the expected hostname or IP address
     * @throws CertificateException if validation fails (expired, invalid, hostname mismatch)
     */
    public static void validate(SSLSocket socket, String expectedHost) throws CertificateException {
        if (socket == null) {
            throw new CertificateException("SSLSocket cannot be null");
        }
        try {
            Certificate[] peerCertificates = socket.getSession().getPeerCertificates();
            if (peerCertificates == null || peerCertificates.length == 0) {
                throw new CertificateException("No peer certificates found in SSL session");
            }
            if (!(peerCertificates[0] instanceof X509Certificate)) {
                throw new CertificateException("Peer certificate is not an X509Certificate");
            }
            validate((X509Certificate) peerCertificates[0], expectedHost);
        } catch (SSLPeerUnverifiedException e) {
            throw new CertificateException("SSL Peer unverified: " + e.getMessage(), e);
        }
    }

    /**
     * Validates an X509Certificate against time validity and expected hostname.
     *
     * @param cert         the X509Certificate to validate
     * @param expectedHost the expected hostname or IP address
     * @throws CertificateException if validation fails
     */
    public static void validate(X509Certificate cert, String expectedHost) throws CertificateException {
        if (cert == null) {
            throw new CertificateException("Certificate cannot be null");
        }
        if (expectedHost == null || expectedHost.trim().isEmpty()) {
            throw new CertificateException("Expected host cannot be null or empty");
        }

        // 1. Check validity (NotBefore and NotAfter dates)
        try {
            cert.checkValidity();
        } catch (CertificateExpiredException e) {
            throw new CertificateException("Server certificate has expired: " + e.getMessage(), e);
        } catch (CertificateNotYetValidException e) {
            throw new CertificateException("Server certificate is not yet valid: " + e.getMessage(), e);
        }

        // 2. Validate hostname against SAN (Subject Alternative Names) and CN (Common Name)
        String host = expectedHost.trim();
        List<String> allSans = new ArrayList<>();
        boolean matched = false;

        try {
            Collection<List<?>> sans = cert.getSubjectAlternativeNames();
            if (sans != null) {
                for (List<?> entry : sans) {
                    if (entry != null && entry.size() >= 2) {
                        Object typeObj = entry.get(0);
                        Object valObj = entry.get(1);
                        if (typeObj instanceof Number && valObj != null) {
                            int type = ((Number) typeObj).intValue();
                            if (type == SAN_TYPE_DNS || type == SAN_TYPE_IP) {
                                String sanVal = valObj.toString().trim();
                                allSans.add(sanVal);
                                if (matchHost(host, sanVal)) {
                                    matched = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        } catch (CertificateException e) {
            throw e;
        } catch (Exception ignored) {
            // Error parsing SAN, proceed to fallback CN check
        }

        List<String> allCns = extractCommonNames(cert);

        if (!matched) {
            for (String cn : allCns) {
                if (matchHost(host, cn)) {
                    matched = true;
                    break;
                }
            }
        }

        if (!matched) {
            throw new CertificateException(String.format(
                    "Hostname mismatch: expected '%s', but certificate has SAN: %s, CN: %s",
                    host, allSans, allCns
            ));
        }
    }

    /**
     * Checks if the expected host matches a certificate SAN/CN entry.
     */
    private static boolean matchHost(String expected, String certEntry) {
        if (expected.equalsIgnoreCase(certEntry)) {
            return true;
        }
        // Loopback equivalence
        if (("localhost".equalsIgnoreCase(expected) && "127.0.0.1".equals(certEntry))
                || ("127.0.0.1".equals(expected) && "localhost".equalsIgnoreCase(certEntry))) {
            return true;
        }
        // Wildcard support: *.example.com
        if (certEntry.startsWith("*.")) {
            String suffix = certEntry.substring(2);
            int dotIdx = expected.indexOf('.');
            if (dotIdx > 0) {
                String expectedSuffix = expected.substring(dotIdx + 1);
                return expectedSuffix.equalsIgnoreCase(suffix);
            }
        }
        return false;
    }

    /**
     * Extracts Common Name (CN) attributes from certificate subject principal.
     */
    private static List<String> extractCommonNames(X509Certificate cert) {
        List<String> cns = new ArrayList<>();
        String dn = cert.getSubjectX500Principal().getName();
        try {
            LdapName ldapDN = new LdapName(dn);
            for (Rdn rdn : ldapDN.getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    Object val = rdn.getValue();
                    if (val != null) {
                        cns.add(val.toString().trim());
                    }
                }
            }
        } catch (Exception e) {
            Matcher m = CN_PATTERN.matcher(dn);
            while (m.find()) {
                cns.add(m.group(1).trim());
            }
        }
        return cns;
    }
}
