CREATE OR REPLACE FUNCTION fn_document_owner_permission() RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    target_schema TEXT;
BEGIN
    SELECT n.nspname
      INTO target_schema
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE c.oid = TG_RELID;

    EXECUTE format(
        'INSERT INTO %I.document_permission (document_id, user_id, permission_level, granted_by)
         VALUES ($1, $2, $3, $4)
         ON CONFLICT (document_id, user_id) DO NOTHING',
        target_schema)
    USING NEW.id, NEW.owner_user_id, 'OWNER', NEW.owner_user_id;

    RETURN NEW;
END;
$$;
