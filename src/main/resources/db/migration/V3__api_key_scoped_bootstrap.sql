UPDATE api_key
 SET scopes = ARRAY['api_keys:read','api_keys:manage','tenant:settings:write']
 WHERE '*' = ANY(scopes);

ALTER TABLE api_key DROP CONSTRAINT IF EXISTS chk_api_key_actor;
ALTER TABLE api_key ALTER COLUMN created_by_user_id DROP NOT NULL;
ALTER TABLE api_key ADD COLUMN created_by_api_key_id UUID REFERENCES api_key(id) ON DELETE SET NULL;
ALTER TABLE api_key ADD CONSTRAINT chk_api_key_actor CHECK (created_by_user_id IS NOT NULL OR created_by_api_key_id IS NOT NULL);
ALTER TABLE api_key ADD CONSTRAINT chk_no_wildcard_scope CHECK (NOT ('*' = ANY(scopes)));
