package ru.growerhub.backend.automation.contract;

import java.time.LocalDateTime;

public interface WeatherForecastGateway {
    WeatherForecastData.Forecast forecast(WeatherForecastData.Location location, LocalDateTime now);
}
