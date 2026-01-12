package ru.growerhub.backend.common.contract;

public interface PasswordHasher {
    String hash(String plainPassword);
    boolean verify(String plainPassword, String storedHash);
}
