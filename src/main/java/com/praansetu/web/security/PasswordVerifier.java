package com.praansetu.web.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * Verifies passwords stored by the desktop application
 * ({@code pbkdf2-sha256$<iterations>$<b64 salt>$<b64 hash>}). The web console never creates hashes.
 */
public final class PasswordVerifier {

    private PasswordVerifier() {
    }

    public static boolean verify(char[] password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !"pbkdf2-sha256".equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, expected.length * 8);
            try {
                byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
                boolean ok = MessageDigest.isEqual(expected, actual);
                Arrays.fill(actual, (byte) 0);
                return ok;
            } finally {
                spec.clearPassword();
            }
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            return false;
        }
    }
}
