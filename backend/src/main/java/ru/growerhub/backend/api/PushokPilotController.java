package ru.growerhub.backend.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.user.UserFacade;
import ru.growerhub.backend.user.contract.PushokPilot;

@RestController
public class PushokPilotController {
    private final UserFacade users;

    public PushokPilotController(UserFacade users) { this.users = users; }

    public record Input(@JsonProperty("contact_method") PushokPilot.ContactMethod contactMethod,
                        String contact, String equipment) {}
    public record RequestResponse(@JsonProperty("contact_method") PushokPilot.ContactMethod contactMethod,
                                  String contact, String equipment,
                                  @JsonProperty("requested_at") LocalDateTime requestedAt,
                                  @JsonProperty("contacted_at") LocalDateTime contactedAt) {}
    public record Result(RequestResponse request) {}
    public record AdminEntry(@JsonProperty("user_id") Integer userId, String username, String email,
                             RequestResponse request) {}

    @GetMapping("/api/users/me/pushok-pilot")
    public Result get(@AuthenticationPrincipal AuthenticatedUser user) {
        return new Result(toResponse(users.getPushokPilot(user.id())));
    }

    @PutMapping("/api/users/me/pushok-pilot")
    public Result save(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody Input input) {
        return new Result(toResponse(users.savePushokPilot(user.id(), input.contactMethod(), input.contact(), input.equipment())));
    }

    @DeleteMapping("/api/users/me/pushok-pilot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal AuthenticatedUser user) { users.withdrawPushokPilot(user.id()); }

    @GetMapping("/api/admin/pushok-pilots")
    public List<AdminEntry> list(@AuthenticationPrincipal AuthenticatedUser user) {
        requireAdmin(user);
        return users.listPushokPilots().stream()
                .map(entry -> new AdminEntry(entry.userId(), entry.username(), entry.email(), toResponse(entry.request())))
                .toList();
    }

    @PostMapping("/api/admin/pushok-pilots/{user_id}/contacted")
    public Result contacted(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable("user_id") Integer userId) {
        requireAdmin(user);
        return new Result(toResponse(users.markPushokPilotContacted(userId)));
    }

    private void requireAdmin(AuthenticatedUser user) {
        if (user == null || !user.isAdmin()) throw new ApiException(HttpStatus.FORBIDDEN, "Nedostatochno prav");
    }

    private RequestResponse toResponse(PushokPilot.Request request) {
        return request == null ? null : new RequestResponse(request.contactMethod(), request.contact(), request.equipment(),
                request.requestedAt(), request.contactedAt());
    }
}
