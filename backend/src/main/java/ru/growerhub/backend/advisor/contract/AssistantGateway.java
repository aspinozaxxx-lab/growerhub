package ru.growerhub.backend.advisor.contract;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public interface AssistantGateway {
    List<AssistantData.Model> models();
    AssistantData.Outcome run(AssistantData.Work work, Consumer<String> onText,
            Function<AssistantData.ToolCall, JsonNode> onTool, java.util.function.BooleanSupplier cancelled);
    void cancel(String provider, java.util.UUID jobId);
}
