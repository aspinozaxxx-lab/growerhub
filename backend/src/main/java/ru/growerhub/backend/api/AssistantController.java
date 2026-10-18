package ru.growerhub.backend.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ru.growerhub.backend.advisor.AdvisorFacade;
import ru.growerhub.backend.advisor.contract.AssistantData.*;
import ru.growerhub.backend.common.contract.AuthenticatedUser;

@RestController
@RequestMapping("/api/advisor")
public class AssistantController {
    private final AdvisorFacade advisor; private final AssistantOrigin origin; private final AssistantStreams streams;
    public AssistantController(AdvisorFacade advisor, AssistantOrigin origin, AssistantStreams streams) { this.advisor = advisor; this.origin = origin; this.streams = streams; }
    @GetMapping("/capabilities")
    public Capabilities capabilities(@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request, HttpServletResponse response) { return advisor.assistantCapabilities(user, origin.resolve(request, response)); }
    @GetMapping("/models")
    public List<Model> models(@AuthenticationPrincipal AuthenticatedUser user) { return advisor.assistantModels(user); }
    @GetMapping("/conversations")
    public List<Conversation> conversations(@AuthenticationPrincipal AuthenticatedUser user) { return advisor.assistantConversations(user); }
    @PostMapping("/conversations")
    public Conversation create(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody(required = false) Options options,
            HttpServletRequest request, HttpServletResponse response) { return advisor.createAssistantConversation(user, origin.resolve(request, response), options); }
    @PutMapping("/conversations/{id}")
    public Conversation configure(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user, @RequestBody Options options,
            HttpServletRequest request, HttpServletResponse response) { return advisor.configureAssistantConversation(id, options, user, origin.resolve(request, response)); }
    @GetMapping("/conversations/{id}/messages")
    public List<Message> messages(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) { return advisor.assistantMessages(id, user); }
    @PostMapping("/conversations/{id}/messages")
    public Message send(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user, @RequestBody Send body,
            HttpServletRequest request, HttpServletResponse response) { return advisor.sendAssistantMessage(id, body, user, origin.resolve(request, response)); }
    @GetMapping("/messages/{id}")
    public Message message(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) { return advisor.assistantMessage(id, user); }
    @DeleteMapping("/messages/{id}")
    public Message cancel(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) { return advisor.cancelAssistantMessage(id, user); }
    @GetMapping(value = "/messages/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no"); response.setHeader("Cache-Control", "no-store");
        return streams.open(id, user);
    }
    @PostMapping(value = "/conversations/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Attachment upload(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user, @RequestPart("photo") MultipartFile photo,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        return advisor.uploadAssistantPhoto(id, photo.getBytes(), user, origin.resolve(request, response));
    }
    @GetMapping("/photos/{id}")
    public ResponseEntity<byte[]> photo(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        var image = advisor.assistantImage(id, user);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(image.mime())).body(image.bytes());
    }
    @PostMapping("/proposals/{id}/confirm")
    public Proposal confirm(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user, @RequestBody Confirm body) { return advisor.confirmAssistantProposal(id, body, user, false); }
    @PostMapping("/proposals/{id}/reject")
    public Proposal reject(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user, @RequestBody Confirm body) { return advisor.confirmAssistantProposal(id, body, user, true); }
    @GetMapping("/usage")
    public Usage usage(@AuthenticationPrincipal AuthenticatedUser user) { return advisor.assistantUsage(user); }
    @PutMapping("/public")
    public Usage publicSwitch(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody Switch body) { return advisor.switchPublicAssistant(body.enabled(), user); }
    @PutMapping("/preferences")
    public Preferences preferences(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody Preferences body) { return advisor.saveAssistantPreferences(body, user); }
}
