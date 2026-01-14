package ru.growerhub.backend.advisor.contract;

public record WateringAdviceBundle(
        WateringPrevious previous,
        WateringAdvice advice
) {
}
