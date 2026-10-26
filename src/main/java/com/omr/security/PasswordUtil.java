package com.omr.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/** PBKDF2 password hashing for teacher accounts; plain-text passwords are never stored. */
public final class PasswordUtil {
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();
    private PasswordUtil() {}

    public static String newSalt() { byte[] salt = new byte[16]; RANDOM.nextBytes(salt); return HexFormat.of().formatHex(salt); }
    public static String hash(String password, String saltHex) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), HexFormat.of().parseHex(saltHex), ITERATIONS, KEY_BITS);
        try { return HexFormat.of().formatHex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded()); }
        catch (Exception e) { throw new IllegalStateException("Password hashing is unavailable", e); }
        finally { spec.clearPassword(); }
    }
    public static boolean verify(String password, String salt, String expectedHex) {
        try { return MessageDigest.isEqual(HexFormat.of().parseHex(hash(password,salt)), HexFormat.of().parseHex(expectedHex)); }
        catch (RuntimeException e) { return false; }
    }
}
