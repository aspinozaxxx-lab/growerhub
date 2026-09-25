package ru.growerhub.backend.mqtt;

import static org.mockito.Mockito.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import ru.growerhub.backend.demo.DemoFacade;
import ru.growerhub.backend.zigbee.ZigbeeFacade;

class MqttZigbeeWateringGatewayTest {
    @Test
    @SuppressWarnings("unchecked")
    void physicalStartIsNotRetainedOrRedeliveredAndStopUsesQosOne() throws Exception {
        var publisher = mock(MqttPublisher.class);
        ObjectProvider<MqttPublisher> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(publisher);
        var zigbee = mock(ZigbeeFacade.class);
        var demo = mock(DemoFacade.class);
        var gateway = new MqttZigbeeCommandGateway(provider, zigbee, demo);
        var start = Map.<String, Object>of("state", "ON", "duration", 30);
        gateway.publishWateringStart("test", "valve", start);
        gateway.publishSet("test", "valve", Map.of("state", "OFF"));
        verify(publisher).publishJson("test/valve/set", start, 0, false);
        verify(publisher).publishJson("test/valve/set", Map.of("state", "OFF"), 1, false);
        verify(zigbee, times(2)).requirePhysicalBaseTopic("test");
        verifyNoInteractions(demo);
    }
}
