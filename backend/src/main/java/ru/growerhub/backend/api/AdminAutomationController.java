package ru.growerhub.backend.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.dto.CommonDtos;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.automation.contract.AutomationData;
import ru.growerhub.backend.common.contract.AuthenticatedUser;

@RestController
@Validated
public class AdminAutomationController {
    private final AutomationFacade automationFacade;

    public AdminAutomationController(AutomationFacade automationFacade) {
        this.automationFacade = automationFacade;
    }

    @GetMapping("/api/admin/automation")
    public AutomationData.Overview getOverview(@AuthenticationPrincipal AuthenticatedUser user) {
        requireAdmin(user);
        return automationFacade.getOverview();
    }

    @PostMapping("/api/admin/automation/rooms")
    public AutomationData.Overview createRoom(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody AutomationData.SaveRoomRequest request
    ) {
        requireAdmin(user);
        return automationFacade.createRoom(request);
    }

    @PutMapping("/api/admin/automation/rooms/{room_id}")
    public AutomationData.Overview updateRoom(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("room_id") Integer roomId,
            @RequestBody AutomationData.SaveRoomRequest request
    ) {
        requireAdmin(user);
        return automationFacade.updateRoom(roomId, request);
    }

    @DeleteMapping("/api/admin/automation/rooms/{room_id}")
    public CommonDtos.MessageResponse deleteRoom(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("room_id") Integer roomId
    ) {
        requireAdmin(user);
        automationFacade.deleteRoom(roomId);
        return new CommonDtos.MessageResponse("Room deleted");
    }

    @PostMapping("/api/admin/automation/rooms/{room_id}/boxes")
    public AutomationData.Overview createBox(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("room_id") Integer roomId,
            @RequestBody AutomationData.SaveBoxRequest request
    ) {
        requireAdmin(user);
        return automationFacade.createBox(roomId, request);
    }

    @PutMapping("/api/admin/automation/boxes/{box_id}")
    public AutomationData.Overview updateBox(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("box_id") Integer boxId,
            @RequestBody AutomationData.SaveBoxRequest request
    ) {
        requireAdmin(user);
        return automationFacade.updateBox(boxId, request);
    }

    @DeleteMapping("/api/admin/automation/boxes/{box_id}")
    public CommonDtos.MessageResponse deleteBox(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("box_id") Integer boxId
    ) {
        requireAdmin(user);
        automationFacade.deleteBox(boxId);
        return new CommonDtos.MessageResponse("Box deleted");
    }

    @PutMapping("/api/admin/automation/boxes/{box_id}/plants")
    public AutomationData.Overview replaceBoxPlants(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("box_id") Integer boxId,
            @RequestBody AutomationData.SavePlantsRequest request
    ) {
        requireAdmin(user);
        return automationFacade.replaceBoxPlants(boxId, request);
    }

    @PutMapping("/api/admin/automation/rooms/{room_id}/resources")
    public AutomationData.Overview replaceRoomResources(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("room_id") Integer roomId,
            @RequestBody AutomationData.SaveResourcesRequest request
    ) {
        requireAdmin(user);
        return automationFacade.replaceRoomResources(roomId, request);
    }

    @PutMapping("/api/admin/automation/boxes/{box_id}/resources")
    public AutomationData.Overview replaceBoxResources(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("box_id") Integer boxId,
            @RequestBody AutomationData.SaveResourcesRequest request
    ) {
        requireAdmin(user);
        return automationFacade.replaceBoxResources(boxId, request);
    }

    @PutMapping("/api/admin/automation/rooms/{room_id}/scenarios")
    public AutomationData.Overview replaceRoomScenarios(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("room_id") Integer roomId,
            @RequestBody AutomationData.SaveScenariosRequest request
    ) {
        requireAdmin(user);
        return automationFacade.replaceRoomScenarios(roomId, request);
    }

    @PutMapping("/api/admin/automation/boxes/{box_id}/scenarios")
    public AutomationData.Overview replaceBoxScenarios(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("box_id") Integer boxId,
            @RequestBody AutomationData.SaveScenariosRequest request
    ) {
        requireAdmin(user);
        return automationFacade.replaceBoxScenarios(boxId, request);
    }

    private void requireAdmin(AuthenticatedUser user) {
        if (user == null || !user.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Nedostatochno prav");
        }
    }
}
