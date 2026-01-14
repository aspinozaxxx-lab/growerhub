package ru.growerhub.backend.advisor.engine;

import java.util.List;
import ru.growerhub.backend.advisor.contract.WateringPrevious;
import ru.growerhub.backend.plant.contract.PlantInfo;
import ru.growerhub.backend.plant.contract.PlantMetricBucketPoint;

public record WateringAdviceContext(
        Integer plantId,
        PlantInfo plant,
        Integer plantAgeDays,
        WateringPrevious previous,
        List<PlantMetricBucketPoint> history,
        String fertilizersName,
        String fertilizersFormat
) {
}
