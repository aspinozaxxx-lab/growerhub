package ru.growerhub.backend.common.component;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.common.config.security.PasswordPbkdf2Settings;

@Component
public class PasswordHasher implements ru.growerhub.backend.common.contract.PasswordHasher {
    private static final String PREFIX = "$pbkdf2-sha256$";
    private final PasswordPbkdf2Settings settings;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordHasher(PasswordPbkdf2Settings settings) {
        this.settings = settings;
    }

    public String hash(String plainPassword) {
        byte[] salt = new byte[settings.getSaltBytes()];
        secureRandom.nextBytes(salt);
        int rounds = settings.getRounds();
        byte[] digest = pbkdf2(plainPassword.toCharArray(), salt, rounds, settings.getHashBytes());
        return PREFIX + rounds + "$" + ab64Encode(salt) + "$" + ab64Encode(digest);
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
        String encoded = Base64.getEncoder().withoutPadding().encodeToString(data);
        return encoded.replace('+', '.');
    }

    private static byte[] ab64Decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return new byte[0];
        }
        String normalized = encoded.replace('.', '+');
        int length = normalized.length();
        int mod = length % 4;
        if (mod == 1) {
            throw new IllegalArgumentException("Invalid ab64 length");
        }
        if (mod != 0) {
            normalized = normalized + "=".repeat(4 - mod);
        }
        return Base64.getDecoder().decode(normalized);
    }
}
