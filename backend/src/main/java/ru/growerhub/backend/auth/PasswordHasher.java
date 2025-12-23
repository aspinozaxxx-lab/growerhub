package ru.growerhub.backend.auth;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

@Component
public class PasswordHasher {
    private static final String PREFIX = "$pbkdf2-sha256$";
    private static final int DEFAULT_ROUNDS = 29000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final char[] AB64_CHARS =
            "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int[] AB64_INV = buildInverse();
    private final SecureRandom secureRandom = new SecureRandom();

    public String hash(String plainPassword) {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] digest = pbkdf2(plainPassword.toCharArray(), salt, DEFAULT_ROUNDS, HASH_BYTES);
        return PREFIX + DEFAULT_ROUNDS + "$" + ab64Encode(salt) + "$" + ab64Encode(digest);
    }

    public boolean verify(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null) {
            return false;
        }
        String[] parts = storedHash.split("\\$");
        if (parts.length != 5 || !parts[1].equals("pbkdf2-sha256")) {
            return false;
        }
        int rounds;
        try {
            rounds = Integer.parseInt(parts[2]);
        } catch (NumberFormatException ex) {
            return false;
        }
        byte[] salt;
        byte[] expected;
        try {
            salt = ab64Decode(parts[3]);
            expected = ab64Decode(parts[4]);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        byte[] actual = pbkdf2(plainPassword.toCharArray(), salt, rounds, expected.length);
        return MessageDigest.isEqual(actual, expected);
    }

    private byte[] pbkdf2(char[] password, byte[] salt, int rounds, int length) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, rounds, length * 8);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("PBKDF2 failure", ex);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static String ab64Encode(byte[] data) {
        StringBuilder sb = new StringBuilder((data.length * 4 + 2) / 3);
        int i = 0;
        while (i < data.length) {
            int b0 = data[i++] & 0xff;
            if (i == data.length) {
                sb.append(AB64_CHARS[b0 >>> 2]);
                sb.append(AB64_CHARS[(b0 & 0x03) << 4]);
                break;
            }
            int b1 = data[i++] & 0xff;
            if (i == data.length) {
                sb.append(AB64_CHARS[b0 >>> 2]);
                sb.append(AB64_CHARS[((b0 & 0x03) << 4) | (b1 >>> 4)]);
                sb.append(AB64_CHARS[(b1 & 0x0f) << 2]);
                break;
            }
            int b2 = data[i++] & 0xff;
            sb.append(AB64_CHARS[b0 >>> 2]);
            sb.append(AB64_CHARS[((b0 & 0x03) << 4) | (b1 >>> 4)]);
            sb.append(AB64_CHARS[((b1 & 0x0f) << 2) | (b2 >>> 6)]);
            sb.append(AB64_CHARS[b2 & 0x3f]);
        }
        return sb.toString();
    }

    private static byte[] ab64Decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return new byte[0];
        }
        int length = encoded.length();
        if (length % 4 == 1) {
            throw new IllegalArgumentException("Invalid ab64 length");
        }
        int outputLength = (length * 6) / 8;
        byte[] out = new byte[outputLength];
        int outPos = 0;
        int i = 0;
        while (i < length) {
            int c1 = decodeChar(encoded.charAt(i++));
            int c2 = decodeChar(encoded.charAt(i++));
            int b0 = (c1 << 2) | (c2 >>> 4);
            out[outPos++] = (byte) b0;
            if (i >= length) {
                break;
            }
            int c3 = decodeChar(encoded.charAt(i++));
            int b1 = ((c2 & 0x0f) << 4) | (c3 >>> 2);
            out[outPos++] = (byte) b1;
            if (i >= length) {
                break;
            }
            int c4 = decodeChar(encoded.charAt(i++));
            int b2 = ((c3 & 0x03) << 6) | c4;
            out[outPos++] = (byte) b2;
        }
        if (outPos != out.length) {
            return Arrays.copyOf(out, outPos);
        }
        return out;
    }

    private static int decodeChar(char value) {
        if (value >= AB64_INV.length || AB64_INV[value] < 0) {
            throw new IllegalArgumentException("Invalid ab64 char");
        }
        return AB64_INV[value];
    }

    private static int[] buildInverse() {
        int[] inverse = new int[128];
        Arrays.fill(inverse, -1);
        for (int i = 0; i < AB64_CHARS.length; i++) {
            inverse[AB64_CHARS[i]] = i;
        }
        return inverse;
    }
}
