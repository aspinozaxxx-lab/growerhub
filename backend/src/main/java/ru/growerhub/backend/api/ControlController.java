package ru.growerhub.backend.api;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.automation.contract.ControlData.*;
import ru.growerhub.backend.common.contract.AuthenticatedUser;

@RestController
@RequestMapping("/api/automation")
public class ControlController {
    private final AutomationFacade facade;
    public ControlController(AutomationFacade facade) { this.facade = facade; }
    @GetMapping("/controls") public List<Status> list(@AuthenticationPrincipal AuthenticatedUser user) { return facade.listControls(user); }
    @GetMapping("/resources/{id}/control") public Status get(@PathVariable Integer id, @AuthenticationPrincipal AuthenticatedUser user) { return facade.getControl(id, user); }
    @PutMapping("/resources/{id}/control") public Status manual(@PathVariable Integer id, @RequestBody Manual request, @AuthenticationPrincipal AuthenticatedUser user) { return facade.setManualControl(id, request, user); }
    @DeleteMapping("/resources/{id}/control") public Status release(@PathVariable Integer id, @RequestParam UUID demandId, @RequestParam int revision, @AuthenticationPrincipal AuthenticatedUser user) { return facade.releaseManualControl(id, demandId, revision, user); }
}
