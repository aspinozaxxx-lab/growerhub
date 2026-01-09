package ru.growerhub.backend.auth;

public record AuthMethods(AuthMethodLocal local, AuthMethodProvider google, AuthMethodProvider yandex) {
}
