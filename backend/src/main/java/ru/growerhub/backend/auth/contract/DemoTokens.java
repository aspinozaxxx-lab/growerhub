package ru.growerhub.backend.auth.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.growerhub.backend.demo.contract.DemoData;

public record DemoTokens(@JsonProperty("access_token") String accessToken, DemoData.Space space) {}
