package ru.growerhub.backend.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.growerhub.backend.auth.AuthFacade;
import ru.growerhub.backend.auth.contract.DemoTokens;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.demo.DemoFacade;
import ru.growerhub.backend.demo.contract.DemoData;

@RestController
@RequestMapping("/api/demo")
public class DemoController {
    private final AuthFacade auth;
    private final DemoFacade demo;
    public DemoController(AuthFacade auth, DemoFacade demo) { this.auth = auth; this.demo = demo; }

    @PostMapping("/start")
    public DemoTokens start(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) Start request, HttpServletRequest httpRequest, HttpServletResponse response) {
        return auth.startDemo(authorization, request == null ? "ru" : request.locale(),
                request == null ? "UTC" : request.timezone(), httpRequest, response);
    }

    @PostMapping("/refresh")
    public DemoTokens refresh(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            HttpServletRequest request) { return auth.refreshDemo(authorization, request); }

    @PostMapping("/save")
    public DemoTokens save(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody(required = false) Save request,
            HttpServletRequest httpRequest, HttpServletResponse response) {
        return auth.saveDemo(user, request != null && request.replace(), httpRequest, response);
    }

    @PostMapping("/reset")
    public DemoTokens reset(@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request, HttpServletResponse response) {
        return auth.resetDemo(user, request, response);
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) { auth.logoutDemo(request, response); }

    @GetMapping("/status")
    public DemoData.Status status(@AuthenticationPrincipal AuthenticatedUser user) { return demo.status(user); }

    @GetMapping("/catalog")
    public List<DemoData.Profile> catalog(@AuthenticationPrincipal AuthenticatedUser user) { return demo.catalog(user); }

    @PostMapping("/devices")
    public DemoData.Device add(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody DemoData.AddDevice request) {
        return demo.addDevice(user, request);
    }

    @PostMapping("/environment")
    public DemoData.Status environment(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody DemoData.Environment request) {
        return demo.environment(user, request);
    }

    public record Start(String locale, String timezone) {}
    public record Save(boolean replace) {}
}
