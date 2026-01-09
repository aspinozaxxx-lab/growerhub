package ru.growerhub.backend;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    protected static final String SECRET = "test-secret-key-test-secret-key-1234";
}

