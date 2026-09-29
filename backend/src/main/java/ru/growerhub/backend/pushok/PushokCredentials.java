package ru.growerhub.backend.pushok;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.common.config.zigbee.PushokSettings;
import ru.growerhub.backend.zigbee.contract.PushokCredentialGateway;

@Component
public class PushokCredentials implements PushokCredentialGateway {
    private final PushokSettings settings;
    private final ObjectMapper mapper;
    private final SecureRandom random = new SecureRandom();
    public PushokCredentials(PushokSettings settings, ObjectMapper mapper) { this.settings = settings; this.mapper = mapper; }

    public record Secrets(String privateKey, String publicKey, String userId, String mqttPassword) {
        @Override public String toString() { return "PushokSecrets[redacted]"; }
    }

    @Override public String createEncryptedCredentials(UUID coordinatorId, String mqttPassword) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            var pair = generator.generateKeyPair();
            var point = ((ECPublicKey) pair.getPublic()).getW();
            byte[] publicKey = new byte[65]; publicKey[0] = 4;
            copyCoordinate(point.getAffineX().toByteArray(), publicKey, 1);
            copyCoordinate(point.getAffineY().toByteArray(), publicKey, 33);
            byte[] userId = new byte[32]; random.nextBytes(userId);
            var secrets = new Secrets(encode(pair.getPrivate().getEncoded()), encode(publicKey), encode(userId), mqttPassword);
            return encrypt(coordinatorId, mapper.writeValueAsBytes(secrets));
        } catch (Exception ex) { throw new IllegalStateException("PushOk credential encryption unavailable"); }
    }

    public Secrets decrypt(UUID coordinatorId, String encrypted) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encrypted);
            if (bytes.length < 29 || bytes[0] != 1) throw new IllegalArgumentException();
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Arrays.copyOfRange(bytes, 1, 13)));
            cipher.updateAAD(coordinatorId.toString().getBytes(StandardCharsets.UTF_8));
            return mapper.readValue(cipher.doFinal(Arrays.copyOfRange(bytes, 13, bytes.length)), Secrets.class);
        } catch (Exception ex) { throw new IllegalStateException("PushOk credentials cannot be decrypted"); }
    }

    private String encrypt(UUID coordinatorId, byte[] bytes) throws Exception {
        byte[] nonce = new byte[12]; random.nextBytes(nonce);
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(coordinatorId.toString().getBytes(StandardCharsets.UTF_8));
        byte[] ciphertext = cipher.doFinal(bytes);
        return encode(ByteBuffer.allocate(13 + ciphertext.length).put((byte) 1).put(nonce).put(ciphertext).array());
    }

    private SecretKeySpec key() {
        byte[] key = Base64.getDecoder().decode(settings.getEncryptionKey());
        if (key.length != 32) throw new IllegalStateException("PushOk requires a 256-bit encryption key");
        return new SecretKeySpec(key, "AES");
    }
    private static String encode(byte[] bytes) { return Base64.getEncoder().encodeToString(bytes); }
    private static void copyCoordinate(byte[] source, byte[] target, int offset) {
        int length = Math.min(32, source.length);
        System.arraycopy(source, source.length - length, target, offset + 32 - length, length);
    }
}
