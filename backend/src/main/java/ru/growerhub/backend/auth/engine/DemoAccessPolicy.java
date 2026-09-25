package ru.growerhub.backend.auth.engine;

public final class DemoAccessPolicy {
    private DemoAccessPolicy() {}

    public static boolean allows(String method, String path) {
        if ("GET".equals(method)) {
            return path.matches("/api/demo/(status|catalog)")
                    || path.equals("/api/devices/my")
                    || path.matches("/api/device/[^/]+/settings")
                    || path.matches("/api/plants(?:/\\d+(?:/(?:history|journal(?:/export)?))?)?")
                    || path.matches("/api/journal/photos/\\d+")
                    || path.matches("/api/sensors/\\d+/history")
                    || path.matches("/api/pumps/\\d+/watering/status")
                    || path.matches("/api/manual-watering(?:/(?:pumps|resources)/\\d+/sessions|/greenhouses/\\d+/statistics)?")
                    || path.matches("/api/automation/(?:farms|resources/\\d+/statistics)")
                    || path.matches("/api/zigbee/coordinators(?:/[0-9a-fA-F-]+(?:/overview|/devices/[^/]+/history)?)?");
        }
        if ("POST".equals(method)) {
            return path.matches("/api/demo/(devices|environment|reset)")
                    || path.equals("/api/plants")
                    || path.matches("/api/plants/\\d+/(journal|harvest)")
                    || path.matches("/api/automation/farms(?:/\\d+/greenhouses)?")
                    || path.matches("/api/manual-watering/(?:pumps|resources)/\\d+/(start|stop)")
                    || path.matches("/api/pumps/\\d+/watering/(start|stop|reboot)")
                    || path.matches("/api/zigbee/coordinators/[0-9a-fA-F-]+/devices/[^/]+/(set-state|set|rename)");
        }
        if ("PUT".equals(method)) {
            return path.equals("/api/automation/scenarios/enabled")
                    || path.matches("/api/automation/(?:farms|greenhouses)/\\d+(?:/(slots|scenarios|plants))?")
                    || path.matches("/api/device/[^/]+/settings")
                    || path.matches("/api/(sensors|pumps)/\\d+/bindings");
        }
        if ("PATCH".equals(method)) {
            return path.matches("/api/plants/\\d+(?:/journal/\\d+)?")
                    || path.matches("/api/automation/greenhouses/\\d+/plants/\\d+/watering-rate");
        }
        return "DELETE".equals(method) && (path.matches("/api/plants/\\d+(?:/journal/\\d+)?")
                || path.matches("/api/automation/(farms|greenhouses)/\\d+"));
    }
}
