package ru.growerhub.backend.auth.contract;

public record AuthMethods(AuthMethodLocal local, AuthMethodProvider google, AuthMethodProvider yandex) {
}
