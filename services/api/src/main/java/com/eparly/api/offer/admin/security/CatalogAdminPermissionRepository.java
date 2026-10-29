package com.eparly.api.offer.admin.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class CatalogAdminPermissionRepository {

    private final JdbcTemplate jdbcTemplate;

    public CatalogAdminPermissionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isActiveAdministrator(UUID userId) {
        Boolean active = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM catalog_admin_permissions permission
                    JOIN users account ON account.id = permission.user_id
                    WHERE permission.user_id = ?
                      AND permission.revoked_at IS NULL
                      AND account.status = 'ACTIVE'
                      AND account.email_verified_at IS NOT NULL
                )
                """, Boolean.class, userId);
        return Boolean.TRUE.equals(active);
    }
}
