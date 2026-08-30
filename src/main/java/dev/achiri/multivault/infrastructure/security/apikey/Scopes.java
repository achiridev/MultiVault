package dev.achiri.multivault.infrastructure.security.apikey;

import java.util.List;

public final class Scopes {

    public static final String DOCUMENTS_READ = "documents:read";
    public static final String DOCUMENTS_WRITE = "documents:write";
    public static final String DOCUMENTS_DELETE = "documents:delete";

    public static final String FOLDERS_READ = "folders:read";
    public static final String FOLDERS_WRITE = "folders:write";
    public static final String FOLDERS_DELETE = "folders:delete";

    public static final String PERMISSIONS_READ = "permissions:read";
    public static final String PERMISSIONS_MANAGE = "permissions:manage";

    public static final String API_KEYS_READ = "api_keys:read";
    public static final String API_KEYS_MANAGE = "api_keys:manage";

    public static final String TENANT_SETTINGS_READ = "tenant:settings:read";
    public static final String TENANT_SETTINGS_WRITE = "tenant:settings:write";

    public static final String AUDIT_READ = "audit:read";

    public static final String SYSTEM_SYNC = "system:sync";
    public static final String SYSTEM_BACKUP = "system:backup";
    public static final String SYSTEM_WEBHOOKS = "system:webhooks";
    public static final String SYSTEM_HEALTH = "system:health";

    public static final String WILDCARD = "*";

    private static final List<String> ALL = List.of(
            DOCUMENTS_READ, DOCUMENTS_WRITE, DOCUMENTS_DELETE,
            FOLDERS_READ, FOLDERS_WRITE, FOLDERS_DELETE,
            PERMISSIONS_READ, PERMISSIONS_MANAGE,
            API_KEYS_READ, API_KEYS_MANAGE,
            TENANT_SETTINGS_READ, TENANT_SETTINGS_WRITE,
            AUDIT_READ,
            SYSTEM_SYNC, SYSTEM_BACKUP, SYSTEM_WEBHOOKS, SYSTEM_HEALTH
    );

    private Scopes() {
    }

    public static List<String> all() {
        return ALL;
    }
}
