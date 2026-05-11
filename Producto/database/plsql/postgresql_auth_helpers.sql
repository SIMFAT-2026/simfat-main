-- ==========================================
-- Stored procedure/function helpers (PostgreSQL)
-- ==========================================

-- Devuelve cantidad de tokens refresh activos por usuario.
CREATE OR REPLACE FUNCTION fn_active_refresh_tokens_count(p_user_id VARCHAR)
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_count INTEGER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM refresh_tokens
     WHERE user_id = p_user_id
       AND revoked_at IS NULL
       AND expires_at > NOW();

    RETURN COALESCE(v_count, 0);
END;
$$;

-- Revoca en bloque todos los refresh tokens activos de un usuario.
CREATE OR REPLACE PROCEDURE sp_revoke_all_refresh_tokens(
    IN p_user_id VARCHAR,
    IN p_reason_ip VARCHAR DEFAULT '0.0.0.0'
)
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE refresh_tokens
       SET revoked_at = NOW(),
           created_by_ip = COALESCE(created_by_ip, p_reason_ip)
     WHERE user_id = p_user_id
       AND revoked_at IS NULL;
END;
$$;
