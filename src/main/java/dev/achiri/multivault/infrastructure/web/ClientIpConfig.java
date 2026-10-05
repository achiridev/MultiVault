package dev.achiri.multivault.infrastructure.web;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ClientIpProperties.class)
public class ClientIpConfig {
}