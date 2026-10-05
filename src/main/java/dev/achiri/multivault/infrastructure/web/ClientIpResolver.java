package dev.achiri.multivault.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.util.Optional;

public interface ClientIpResolver {

    Optional<InetAddress> resolve(HttpServletRequest request);
}