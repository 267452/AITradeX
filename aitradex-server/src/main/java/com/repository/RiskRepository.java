package com.repository;

import com.domain.entity.RiskRuleEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class RiskRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RiskRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<RiskRuleEntity> getRiskRules() {
        return jdbcTemplate.query("""
                SELECT id, name, rule_type, rule_config, enabled, priority, created_at, updated_at
                FROM risk_rule
                ORDER BY priority DESC, created_at DESC
                """, (rs, rowNum) -> mapRiskRule(rs));
    }

    public RiskRuleEntity getRiskRuleById(long id) {
        List<RiskRuleEntity> rows = jdbcTemplate.query("""
                SELECT id, name, rule_type, rule_config, enabled, priority, created_at, updated_at
                FROM risk_rule
                WHERE id = ?
                """, (rs, rowNum) -> mapRiskRule(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<RiskRuleEntity> getEnabledRiskRules() {
        return jdbcTemplate.query("""
                SELECT id, name, rule_type, rule_config, enabled, priority, created_at, updated_at
                FROM risk_rule
                WHERE enabled = true
                ORDER BY priority DESC, created_at DESC
                """, (rs, rowNum) -> mapRiskRule(rs));
    }

    public long createRiskRule(String name, String ruleType, Map<String, Object> ruleConfig, boolean enabled, int priority) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO risk_rule (name, rule_type, rule_config, enabled, priority)
                    VALUES (?, ?, ?::jsonb, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, name);
            ps.setString(2, ruleType);
            try {
                ps.setString(3, objectMapper.writeValueAsString(ruleConfig));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("json_encode_failed", e);
            }
            ps.setBoolean(4, enabled);
            ps.setInt(5, priority);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    public boolean updateRiskRule(long id, String name, String ruleType, Map<String, Object> ruleConfig, boolean enabled, int priority) {
        int updated = jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    UPDATE risk_rule
                    SET name = ?, rule_type = ?, rule_config = ?::jsonb, enabled = ?, priority = ?, updated_at = NOW()
                    WHERE id = ?
                    """);
            ps.setString(1, name);
            ps.setString(2, ruleType);
            try {
                ps.setString(3, objectMapper.writeValueAsString(ruleConfig));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("json_encode_failed", e);
            }
            ps.setBoolean(4, enabled);
            ps.setInt(5, priority);
            ps.setLong(6, id);
            return ps;
        });
        return updated > 0;
    }

    public boolean deleteRiskRule(long id) {
        int deleted = jdbcTemplate.update("DELETE FROM risk_rule WHERE id = ?", id);
        return deleted > 0;
    }

    public boolean toggleRiskRule(long id, boolean enabled) {
        int updated = jdbcTemplate.update(
                "UPDATE risk_rule SET enabled = ?, updated_at = NOW() WHERE id = ?",
                enabled, id);
        return updated > 0;
    }

    private RiskRuleEntity mapRiskRule(java.sql.ResultSet rs) throws java.sql.SQLException {
        try {
            Map<String, Object> ruleConfig = objectMapper.readValue(rs.getString("rule_config"), Map.class);
            java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
            java.sql.Timestamp updatedAt = rs.getTimestamp("updated_at");
            OffsetDateTime createdAtOffset = createdAt != null ? createdAt.toInstant().atOffset(java.time.ZoneOffset.UTC) : null;
            OffsetDateTime updatedAtOffset = updatedAt != null ? updatedAt.toInstant().atOffset(java.time.ZoneOffset.UTC) : null;
            return new RiskRuleEntity(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("rule_type"),
                    ruleConfig,
                    rs.getBoolean("enabled"),
                    rs.getInt("priority"),
                    createdAtOffset,
                    updatedAtOffset
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("json_decode_failed", e);
        }
    }
}