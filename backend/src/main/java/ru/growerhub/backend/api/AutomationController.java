package ru.growerhub.backend.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/farms")
    public AutomationData.FarmsOverview farmsOverview(@AuthenticationPrincipal AuthenticatedUser user) {
        return automationFacade.getFarmsOverview(user);
    }

    @GetMapping("/resources/{resource_id}/statistics")
    public AutomationData.ResourceStatistics resourceStatistics(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("resource_id") Integer resourceId,
            @RequestParam(value = "hours", required = false) Integer hours
    ) {
        return automationFacade.getResourceStatistics(user, resourceId, hours);
    }

    @PostMapping("/farms")
    public AutomationData.FarmsOverview createUserFarm(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody AutomationData.SaveRoomRequest request
    ) {
        return automationFacade.createUserFarm(user, request);
    }

    @PutMapping("/farms/{farm_id}")
    public AutomationData.FarmsOverview updateUserFarm(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("farm_id") Integer farmId,
            @RequestBody AutomationData.SaveRoomRequest request
    ) {
        return automationFacade.updateUserFarm(user, farmId, request);
    }

    @DeleteMapping("/farms/{farm_id}")
    public CommonDtos.MessageResponse deleteUserFarm(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("farm_id") Integer farmId
    ) {
        automationFacade.deleteUserFarm(user, farmId);
        return new CommonDtos.MessageResponse("Farm deleted");
    }

    @PostMapping("/farms/{farm_id}/greenhouses")
    public AutomationData.FarmsOverview createGreenhouse(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("farm_id") Integer farmId,
            @RequestBody AutomationData.SaveBoxRequest request
    ) {
        return automationFacade.createGreenhouse(user, farmId, request);
    }

    @PutMapping("/greenhouses/{greenhouse_id}")
    public AutomationData.FarmsOverview updateGreenhouse(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("greenhouse_id") Integer greenhouseId,
            @RequestBody AutomationData.SaveGreenhouseRequest request
    ) {
        return automationFacade.updateGreenhouse(user, greenhouseId, request);
    }

    @DeleteMapping("/greenhouses/{greenhouse_id}")
    public CommonDtos.MessageResponse deleteGreenhouse(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("greenhouse_id") Integer greenhouseId
    ) {
        automationFacade.deleteGreenhouse(user, greenhouseId);
        return new CommonDtos.MessageResponse("Greenhouse deleted");
    }

    @PutMapping("/farms/{farm_id}/slots")
    public AutomationData.FarmsOverview replaceUserFarmSlots(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("farm_id") Integer farmId,
            @RequestBody AutomationData.SaveZoneSlotsRequest request
    ) {
        return automationFacade.replaceUserFarmSlots(user, farmId, request);
    }

    @PutMapping("/farms/{farm_id}/scenarios")
    public AutomationData.FarmsOverview replaceUserFarmScenarios(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("farm_id") Integer farmId,
            @RequestBody AutomationData.SaveScenariosRequest request
    ) {
        return automationFacade.replaceUserFarmScenarios(user, farmId, request);
    }

    @PutMapping("/greenhouses/{greenhouse_id}/slots")
    public AutomationData.FarmsOverview replaceGreenhouseSlots(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("greenhouse_id") Integer greenhouseId,
            @RequestBody AutomationData.SaveZoneSlotsRequest request
    ) {
        return automationFacade.replaceGreenhouseSlots(user, greenhouseId, request);
    }

    @PutMapping("/greenhouses/{greenhouse_id}/plants")
    public AutomationData.FarmsOverview replaceGreenhousePlants(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("greenhouse_id") Integer greenhouseId,
            @RequestBody AutomationData.SavePlantsRequest request
    ) {
        return automationFacade.replaceGreenhousePlants(user, greenhouseId, request);
    }

    @PatchMapping("/greenhouses/{greenhouse_id}/plants/{plant_id}/watering-rate")
    public AutomationData.FarmsOverview updateGreenhousePlantWateringRate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("greenhouse_id") Integer greenhouseId,
            @PathVariable("plant_id") Integer plantId,
            @RequestBody AutomationData.UpdateWateringRateRequest request
    ) {
        return automationFacade.updateGreenhousePlantWateringRate(user, greenhouseId, plantId, request);
    }

    @PutMapping("/greenhouses/{greenhouse_id}/scenarios")
    public AutomationData.FarmsOverview replaceGreenhouseScenarios(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("greenhouse_id") Integer greenhouseId,
            @RequestBody AutomationData.SaveScenariosRequest request
    ) {
        return automationFacade.replaceGreenhouseScenarios(user, greenhouseId, request);
    }

    @GetMapping("/farm")
    public AutomationData.FarmOverview farmOverview(@AuthenticationPrincipal AuthenticatedUser user) {
        return automationFacade.getFarmOverview(user);
    }

    @PostMapping("/farm")
    public AutomationData.FarmOverview createFarm(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody AutomationData.SaveFarmRequest request
    ) {
        return automationFacade.createFarm(user, request);
    }

    @PutMapping("/farm")
    public AutomationData.FarmOverview updateFarm(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody AutomationData.SaveFarmRequest request
    ) {
        return automationFacade.updateFarm(user, request);
    }

    @PostMapping("/farm/zones")
    public AutomationData.FarmOverview createFarmZone(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody AutomationData.SaveRoomRequest request
    ) {
        return automationFacade.createFarmZone(user, request);
    }

    @PutMapping("/farm/zones/{zone_id}")
    public AutomationData.FarmOverview updateFarmZone(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("zone_id") Integer zoneId,
            @RequestBody AutomationData.SaveRoomRequest request
    ) {
        return automationFacade.updateFarmZone(user, zoneId, request);
    }

    @DeleteMapping("/farm/zones/{zone_id}")
    public CommonDtos.MessageResponse deleteFarmZone(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("zone_id") Integer zoneId
    ) {
        automationFacade.deleteFarmZone(user, zoneId);
        return new CommonDtos.MessageResponse("Zone deleted");
    }

    @PutMapping("/farm/zones/{zone_id}/slots")
    public AutomationData.FarmOverview replaceFarmZoneSlots(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("zone_id") Integer zoneId,
            @RequestBody AutomationData.SaveZoneSlotsRequest request
    ) {
        return automationFacade.replaceFarmZoneSlots(user, zoneId, request);
    }

    @PutMapping("/farm/zones/{zone_id}/plants")
    public AutomationData.FarmOverview replaceFarmZonePlants(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("zone_id") Integer zoneId,
            @RequestBody AutomationData.SavePlantsRequest request
    ) {
        return automationFacade.replaceFarmZonePlants(user, zoneId, request);
    }

    @PutMapping("/farm/zones/{zone_id}/scenarios")
    public AutomationData.FarmOverview replaceFarmZoneScenarios(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("zone_id") Integer zoneId,
            @RequestBody AutomationData.SaveScenariosRequest request
    ) {
        return automationFacade.replaceFarmZoneScenarios(user, zoneId, request);
    }
}
