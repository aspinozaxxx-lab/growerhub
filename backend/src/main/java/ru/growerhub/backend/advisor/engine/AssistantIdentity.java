package ru.growerhub.backend.advisor.engine;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.advisor.contract.AssistantData.Origin;
import ru.growerhub.backend.common.config.advisor.AssistantSettings;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.demo.DemoFacade;

@Component
public class AssistantIdentity {
    private final AssistantSettings settings;
    private final DemoFacade demo;
    public AssistantIdentity(AssistantSettings settings, DemoFacade demo) { this.settings = settings; this.demo = demo; }

    public record Identity(String billing, String ip, String visitor, boolean guest, boolean owner) {}

    public Identity resolve(AuthenticatedUser user, Origin origin) {
        Integer account = user.id();
        String admission = null;
        if (user.isDemo()) {
            var identity = demo.assistantIdentity(user);
            account = identity.accountId(); admission = identity.admissionKey();
        }
        boolean guest = account == null;
        boolean owner = account != null && account.equals(settings.ownerUserId());
        String visitor = origin != null && origin.visitor() != null ? origin.visitor() : admission;
        if (guest && (visitor == null || visitor.isBlank()))
            throw new DomainException("unauthorized", "Откройте помощника из приложения");
        return new Identity(key(guest ? "guest:" + visitor : "account:" + account),
                origin != null && origin.address() != null ? key("ip:" + origin.address()) : null,
                visitor == null ? null : key("visitor:" + visitor), guest, owner);
    }

    public String key(String value) {
        if (settings.hmacSecret() == null || settings.hmacSecret().length() < 32)
            throw new DomainException("unavailable", "Помощник ещё настраивается");
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(settings.hmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }
}
