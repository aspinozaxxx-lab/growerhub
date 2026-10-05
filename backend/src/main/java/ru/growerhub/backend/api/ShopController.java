package ru.growerhub.backend.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.growerhub.backend.common.config.ShopSettings;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.shop.ShopFacade;
import ru.growerhub.backend.shop.contract.ShopData;

@RestController
public class ShopController {
    private final ShopFacade shop;
    private final ShopSettings settings;
    public ShopController(ShopFacade shop, ShopSettings settings) { this.shop = shop; this.settings = settings; }

    @GetMapping("/api/shop/catalog")
    public ShopData.Catalog catalog() { return shop.catalog(); }

    @PostMapping("/api/shop/requests")
    public ShopData.Receipt submit(@RequestBody ShopData.Submission input, HttpServletRequest request) {
        return shop.submit(input, clientAddress(request));
    }

    @GetMapping("/api/admin/shop/requests")
    public ShopData.Requests list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) ShopData.Status status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return shop.list(user, status, page, size);
    }

    @GetMapping("/api/admin/shop/requests/{id}")
    public ShopData.Request get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) { return shop.get(user, id); }

    public record StatusInput(ShopData.Status status) { }
    @PatchMapping("/api/admin/shop/requests/{id}")
    public ShopData.Request update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
            @RequestBody StatusInput input) { return shop.update(user, id, input.status()); }

    @PostMapping("/api/admin/shop/requests/{id}/retry-notification")
    public ShopData.Request retry(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) { return shop.retry(user, id); }

    private String clientAddress(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String real = request.getHeader("X-Real-IP");
        if (settings.trustedProxyAddresses().contains(remote) && real != null && real.length() <= 45 && real.matches("[0-9a-fA-F:.]+")) return real;
        return remote;
    }
}
