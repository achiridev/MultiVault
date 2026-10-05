package dev.achiri.multivault.infrastructure.ratelimit.model;

public enum RateLimitScope {
    IP,
    GLOBAL,
    API_KEY,
    TENANT
}