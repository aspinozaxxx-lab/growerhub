package ru.growerhub.backend.api;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.automation.contract.RuleData.*;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
@RestController
@RequestMapping("/api/automation/rules")
public class RuleController {
    private final AutomationFacade facade;
    public RuleController(AutomationFacade facade) { this.facade = facade; }
    @GetMapping public List<Rule> list(@AuthenticationPrincipal AuthenticatedUser user) { return facade.listRules(user); }
    @GetMapping("/{id}") public Rule get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) { return facade.getRule(id, user); }
    @PostMapping public Rule create(@RequestBody Save command, @AuthenticationPrincipal AuthenticatedUser user) { return facade.saveRule(null, command, user); }
    @PutMapping("/{id}") public Rule save(@PathVariable UUID id, @RequestBody Save command, @AuthenticationPrincipal AuthenticatedUser user) { return facade.saveRule(id, command, user); }
    @PostMapping("/preview") public Preview preview(@RequestParam(required = false) UUID id, @RequestBody Definition definition, @AuthenticationPrincipal AuthenticatedUser user) { return facade.previewRule(id, definition, user); }
    @PutMapping("/{id}/enabled") public Rule toggle(@PathVariable UUID id, @RequestBody Toggle command, @AuthenticationPrincipal AuthenticatedUser user) { return facade.toggleRule(id, command, user); }
    @PostMapping("/{id}/restore") public Rule restore(@PathVariable UUID id, @RequestBody Restore command, @AuthenticationPrincipal AuthenticatedUser user) { return facade.restoreRule(id, command, user); }
    @GetMapping("/{id}/versions") public List<Version> versions(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) { return facade.ruleVersions(id, user); }
    @GetMapping("/{id}/history") public List<Run> history(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) { return facade.ruleHistory(id, user); }
}
