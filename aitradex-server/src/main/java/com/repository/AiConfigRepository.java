package com.repository;

import com.domain.entity.AiConfigEntity;
import java.sql.PreparedStatement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class AiConfigRepository {
    private final JdbcTemplate jdbcTemplate;

    public AiConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AiConfigEntity getActiveConfig() {
        List<AiConfigEntity> rows = jdbcTemplate.query("""
                SELECT id, provider, model, model_id, api_key_encrypted, base_url, temperature, max_tokens,
                       enabled, is_active, created_at, updated_at
                FROM ai_config
                WHERE is_active = TRUE
                LIMIT 1
                """, (rs, rowNum) -> mapRow(rs));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<AiConfigEntity> listAllConfigs() {
        return jdbcTemplate.query("""
                SELECT id, provider, model, model_id, api_key_encrypted, base_url, temperature, max_tokens,
                       enabled, is_active, created_at, updated_at
                FROM ai_config
                ORDER BY updated_at DESC
                """, (rs, rowNum) -> mapRow(rs));
    }

    public AiConfigEntity getByProvider(String provider) {
        List<AiConfigEntity> rows = jdbcTemplate.query("""
                SELECT id, provider, model, model_id, api_key_encrypted, base_url, temperature, max_tokens,
                       enabled, is_active, created_at, updated_at
                FROM ai_config
                WHERE provider = ?
                LIMIT 1
                """, (rs, rowNum) -> mapRow(rs), provider);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @org.springframework.transaction.annotation.Transactional
    public AiConfigEntity saveOrUpdate(AiConfigEntity config) {
        AiConfigEntity existing = getByProvider(config.provider());
        if (existing != null) {
            jdbcTemplate.update("""
                    UPDATE ai_config SET
                        model = ?, model_id = ?, api_key_encrypted = ?, base_url = ?,
                        temperature = ?, max_tokens = ?, enabled = ?, updated_at = NOW()
                    WHERE provider = ?
                    """,
                    config.model(), config.modelId(), config.apiKeyEncrypted(), config.baseUrl(),
                    config.temperature(), config.maxTokens(), config.enabled(),
                    config.provider());
            return getByProvider(config.provider());
        } else {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement("""
                        INSERT INTO ai_config (provider, model, model_id, api_key_encrypted, base_url, temperature, max_tokens, enabled, is_active)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, new String[]{"id"});
                ps.setString(1, config.provider());
                ps.setString(2, config.model());
                ps.setString(3, config.modelId());
                ps.setString(4, config.apiKeyEncrypted());
                ps.setString(5, config.baseUrl());
                ps.setDouble(6, config.temperature() != null ? config.temperature() : 0.3);
                ps.setInt(7, config.maxTokens() != null ? config.maxTokens() : 2000);
                ps.setBoolean(8, config.enabled() != null ? config.enabled() : true);
                ps.setBoolean(9, config.active() != null ? config.active() : true);
                return ps;
            }, keyHolder);
            Number key = keyHolder.getKey();
            List<AiConfigEntity> rows = jdbcTemplate.query("""
                    SELECT id, provider, model, model_id, api_key_encrypted, base_url, temperature, max_tokens,
                           enabled, is_active, created_at, updated_at
                    FROM ai_config WHERE id = ?
                    """, (rs, rowNum) -> mapRow(rs), key.longValue());
            return rows.isEmpty() ? null : rows.get(0);
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public void setActive(String provider) {
        jdbcTemplate.update("UPDATE ai_config SET is_active = FALSE WHERE is_active = TRUE");
        jdbcTemplate.update("UPDATE ai_config SET is_active = TRUE, updated_at = NOW() WHERE provider = ?", provider);
    }

    @org.springframework.transaction.annotation.Transactional
    public void delete(String provider) {
        jdbcTemplate.update("DELETE FROM ai_config WHERE provider = ?", provider);
    }

    private AiConfigEntity mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AiConfigEntity(
                rs.getLong("id"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getString("model_id"),
                rs.getString("api_key_encrypted"),
                rs.getString("base_url"),
                rs.getObject("temperature", Double.class),
                rs.getObject("max_tokens", Integer.class),
                rs.getObject("enabled", Boolean.class),
                rs.getObject("is_active", Boolean.class),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class));
    }
}
