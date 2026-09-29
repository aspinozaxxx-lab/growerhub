package ru.growerhub.backend.pushok;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import ru.growerhub.backend.common.config.zigbee.PushokSettings;

final class PushokCloudClient implements AutoCloseable {
    static final class Failure extends RuntimeException {
        private final String code;
        Failure(String code) { super(code); this.code = code; }
        String code() { return code; }
    }
    private final ObjectMapper mapper;
    private final PushokSettings settings;
    private final ConcurrentHashMap<Integer, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final ArrayBlockingQueue<JsonNode> events = new ArrayBlockingQueue<>(256);
    private final AtomicInteger sequence = new AtomicInteger();
    private volatile boolean open;
    private WebSocket socket;
    private HttpClient http;
    private String hubPublicKey;

    PushokCloudClient(ObjectMapper mapper, PushokSettings settings) { this.mapper = mapper; this.settings = settings; }

    void connect(String hubId, PushokCredentials.Secrets secrets, String expectedKey, boolean allowPairing) {
        try {
            URI cloud = URI.create(settings.getCloudUrl());
            if (!"wss".equals(cloud.getScheme()) || cloud.getRawQuery() != null || cloud.getUserInfo() != null
                    || !hubId.matches(settings.getHubIdPattern())) throw new Failure("INVALID_HUB_ID");
            URI endpoint = URI.create(cloud.toString().replaceAll("/$", "") + "/" + hubId + "/client");
            http = HttpClient.newBuilder().connectTimeout(timeout()).build();
            socket = http.newWebSocketBuilder()
                    .connectTimeout(timeout()).buildAsync(endpoint, new Listener())
                    .get(settings.getRequestTimeoutSeconds(), TimeUnit.SECONDS);
            open = true;
            JsonNode identity = request("pubKey", null);
            if (!hubId.equals(identity.path("hostname").asText())) throw new Failure("HUB_IDENTITY_CHANGED");
            hubPublicKey = identity.path("key").asText();
            if (expectedKey != null && !expectedKey.equals(hubPublicKey)) throw new Failure("HUB_IDENTITY_CHANGED");
            try { authenticate(secrets); }
            catch (Failure failure) {
                if (!allowPairing || !"PAIRING_REQUIRED".equals(failure.code())) throw failure;
                JsonNode registered = request("addUser", Map.of("user_id", secrets.userId(), "public_key", secrets.publicKey(), "role", 1));
                if (!registered.asBoolean(false)) throw new Failure("PAIRING_REQUIRED");
                authenticate(secrets);
            }
        } catch (Failure failure) { close(); throw failure; }
        catch (Exception ex) { close(); throw new Failure("CLOUD_UNAVAILABLE"); }
    }

    private void authenticate(PushokCredentials.Secrets secrets) throws Exception {
        byte[] raw = Base64.getDecoder().decode(hubPublicKey);
        if (raw.length != 65 || raw[0] != 4) throw new Failure("HUB_PROTOCOL_ERROR");
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        var point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(raw, 1, 33)), new BigInteger(1, Arrays.copyOfRange(raw, 33, 65)));
        PublicKey gateway = KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, parameters.getParameterSpec(ECParameterSpec.class)));
        var privateKey = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(secrets.privateKey())));
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH"); agreement.init(privateKey); agreement.doPhase(gateway, true);
        byte[] shared = agreement.generateSecret();
        byte[] deviceNonce;
        try { deviceNonce = crypt(Cipher.DECRYPT_MODE, shared, new byte[12], Base64.getDecoder().decode(request("challenge", Map.of("user_id", secrets.userId())).asText())); }
        catch (AEADBadTagException ex) { throw new Failure("PAIRING_REQUIRED"); }
        if (deviceNonce.length != 32) throw new Failure("HUB_PROTOCOL_ERROR");
        byte[] userNonce = new byte[32]; new SecureRandom().nextBytes(userNonce);
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey); signature.update(deviceNonce); signature.update(userNonce);
        byte[] signed = signature.sign();
        byte[] password = crypt(Cipher.ENCRYPT_MODE, shared, Arrays.copyOf(deviceNonce, 12),
                ByteBuffer.allocate(signed.length + 32).put(signed).put(userNonce).array());
        JsonNode response = request("authenticate", Map.of("password", Base64.getEncoder().encodeToString(password), "version", "0.1.0"));
        if (!response.path("authorized").asBoolean()) throw new Failure("PAIRING_REQUIRED");
        byte[] proof = crypt(Cipher.DECRYPT_MODE, shared, Arrays.copyOf(deviceNonce, 12), Base64.getDecoder().decode(response.path("dev_sign").asText()));
        signature.initVerify(gateway); signature.update(userNonce);
        if (!signature.verify(proof)) throw new Failure("HUB_IDENTITY_CHANGED");
    }

    JsonNode request(String method, Object params) {
        if (!open) throw new Failure("CLOUD_UNAVAILABLE");
        int id = sequence.incrementAndGet();
        var future = new CompletableFuture<JsonNode>(); pending.put(id, future);
        try {
            var message = mapper.createObjectNode().put("id", id).put("m", method);
            if (params != null) message.set("p", mapper.valueToTree(params));
            socket.sendText(mapper.writeValueAsString(message), true).get(settings.getRequestTimeoutSeconds(), TimeUnit.SECONDS);
            JsonNode response = future.get(settings.getRequestTimeoutSeconds(), TimeUnit.SECONDS);
            if (response.has("error")) throw new Failure("addUser".equals(method) ? "PAIRING_REQUIRED" : "HUB_COMMAND_REJECTED");
            return response.path("result");
        } catch (Failure failure) { throw failure; }
        catch (Exception ex) { throw new Failure("CLOUD_UNAVAILABLE"); }
        finally { pending.remove(id); }
    }
    JsonNode pollEvent() { return events.poll(); }
    String hubPublicKey() { return hubPublicKey; }
    boolean isOpen() { return open; }
    void ping() { if (open) socket.sendPing(ByteBuffer.wrap(new byte[] { 1 })); }
    private Duration timeout() { return Duration.ofSeconds(settings.getRequestTimeoutSeconds()); }

    private static byte[] crypt(int mode, byte[] key, byte[] nonce, byte[] bytes) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        return cipher.doFinal(bytes);
    }
    @Override public void close() {
        open = false;
        if (socket != null) socket.abort();
        if (http != null) http.shutdownNow();
        pending.values().forEach(future -> future.completeExceptionally(new Failure("CLOUD_UNAVAILABLE")));
        pending.clear();
    }
    private final class Listener implements WebSocket.Listener {
        private final StringBuilder text = new StringBuilder();
        @Override public void onOpen(WebSocket webSocket) { webSocket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            text.append(data);
            if (text.length() > settings.getMaxMessageBytes()) { close(); webSocket.abort(); return null; }
            if (last) {
                try {
                    JsonNode message = mapper.readTree(text.toString());
                    if (message.has("broadcast")) {
                        if (!events.offer(message.get("broadcast"))) close();
                    } else {
                        var future = pending.get(message.path("id").asInt());
                        if (future != null) future.complete(message);
                    }
                } catch (Exception ex) { close(); }
                finally { text.setLength(0); }
            }
            webSocket.request(1); return null;
        }
        @Override public CompletionStage<?> onPing(WebSocket ws, ByteBuffer message) { ws.request(1); return ws.sendPong(message); }
        @Override public CompletionStage<?> onPong(WebSocket ws, ByteBuffer message) { ws.request(1); return null; }
        @Override public CompletionStage<?> onClose(WebSocket ws, int status, String reason) { close(); return null; }
        @Override public void onError(WebSocket ws, Throwable error) { close(); }
    }
}
