package ru.growerhub.backend.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.dto.CommonDtos;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.automation.contract.AutomationData;
import ru.growerhub.backend.common.contract.AuthenticatedUser;

@RestController
@RequestMapping("/api/automation")
public class AutomationController {
    private final AutomationFacade automationFacade;

    public AutomationController(AutomationFacade automationFacade) {
        this.automationFacade = automationFacade;
    }

    @GetMapping
    public AutomationData.Overview overview(@AuthenticationPrincipal AuthenticatedUser user) {
        return automationFacade.getOverview(user);
    }

    @PostMapping("/zones")
    public AutomationData.Overview createZone(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody AutomationData.SaveRoomRequest request
    ) {
        return automationFacade.createRoom(user, request);
    }

    @PutMapping("/zones/{zone_id}")
    public AutomationData.Overview updateZone(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("zone_id") Integer zoneId,
            @RequestBody AutomationData.SaveRoomRequest request
    ) {
        return automationFacade.updateRoom(user, zoneId, request);
    }

    @DeleteMapping("/zones/{zone_id}")
    public CommonDtos.MessageResponse deleteZone(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("zone_id") Integer zoneId
    ) {
        automationFacade.deleteRoom(user, zoneId);
        return new CommonDtos.MessageResponse("Zone deleted");
    }

    @PutMapping("/zones/{zone_id}/resources")
    public AutomationData.Overview replaceZoneResources(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("zone_id") Integer zoneId,
            @RequestBody AutomationData.SaveResourcesRequest request
    ) {
        return automationFacade.replaceRoomResources(user, zoneId, request);
    }

    @PutMapping("/zones/{zone_id}/scenarios")
    public AutomationData.Overview replaceZoneScenarios(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("zone_id") Integer zoneId,
            @RequestBody AutomationData.SaveScenariosRequest request
    ) {
        return automationFacade.replaceRoomScenarios(user, zoneId, request);
    }

    @PostMapping("/zones/{zone_id}/sections")
    public AutomationData.Overview createSection(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("zone_id") Integer zoneId,
            @RequestBody AutomationData.SaveBoxRequest request
    ) {
        return automationFacade.createBox(user, zoneId, request);
    }

    @PutMapping("/sections/{section_id}")
    public AutomationData.Overview updateSection(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("section_id") Integer sectionId,
            @RequestBody AutomationData.SaveBoxRequest request
    ) {
        return automationFacade.updateBox(user, sectionId, request);
    }

    @DeleteMapping("/sections/{section_id}")
    public CommonDtos.MessageResponse deleteSection(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("section_id") Integer sectionId
    ) {
        automationFacade.deleteBox(user, sectionId);
        return new CommonDtos.MessageResponse("Section deleted");
    }

    @PutMapping("/sections/{section_id}/resources")
    public AutomationData.Overview replaceSectionResources(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("section_id") Integer sectionId,
            @RequestBody AutomationData.SaveResourcesRequest request
    ) {
        return automationFacade.replaceBoxResources(user, sectionId, request);
    }

    @PutMapping("/sections/{section_id}/scenarios")
    public AutomationData.Overview replaceSectionScenarios(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("section_id") Integer sectionId,
            @RequestBody AutomationData.SaveScenariosRequest request
    ) {
        return automationFacade.replaceBoxScenarios(user, sectionId, request);
    }
}
