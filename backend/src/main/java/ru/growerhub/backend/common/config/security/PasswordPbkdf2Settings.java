package ru.growerhub.backend.common.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Nastroyki PBKDF2 dlya paroliv.
@ConfigurationProperties(prefix = "security.password.pbkdf2")
public class PasswordPbkdf2Settings {
    // Kolichestvo iteracij.
    private int rounds = 29000;
    // Razmer soli (bytes).
    private int saltBytes = 16;
    // Razmer hesha (bytes).
    private int hashBytes = 32;

    public int getRounds() {
        return rounds;
    }

    public void setRounds(int rounds) {
        this.rounds = rounds;
    }

    public int getSaltBytes() {
        return saltBytes;
    }

    public void setSaltBytes(int saltBytes) {
        this.saltBytes = saltBytes;
    }

    public int getHashBytes() {
        return hashBytes;
    }

    public void setHashBytes(int hashBytes) {
        this.hashBytes = hashBytes;
    }
}
