package dev.achiri.multivault.apikey.mapper;

import dev.achiri.multivault.apikey.dto.ApiKeyIssuedResponse;
import dev.achiri.multivault.apikey.dto.ApiKeyResponse;
import dev.achiri.multivault.apikey.model.ApiKey;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ApiKeyMapper {

    @Mapping(target = "expired", source = "expiresAt", qualifiedByName = "isExpired")
    ApiKeyResponse toResponse(ApiKey apiKey);

    List<ApiKeyResponse> toResponseList(List<ApiKey> apiKeys);

    @Mapping(target = "id", source = "apiKey.id")
    @Mapping(target = "name", source = "apiKey.name")
    @Mapping(target = "keyPrefix", source = "apiKey.keyPrefix")
    @Mapping(target = "keyType", source = "apiKey.keyType")
    @Mapping(target = "key", source = "rawKey")
    @Mapping(target = "scopes", source = "apiKey.scopes")
    @Mapping(target = "createdAt", source = "apiKey.createdAt")
    @Mapping(target = "expiresAt", source = "apiKey.expiresAt")
    ApiKeyIssuedResponse toIssuedResponse(ApiKey apiKey, String rawKey);

    @Named("isExpired")
    default boolean isExpired(Instant expiresAt) {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}
