package com.repository;

import com.domain.entity.BrokerAccountEntity;
import java.sql.PreparedStatement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class BrokerAccountRepository {
    private final JdbcTemplate jdbcTemplate;

    public BrokerAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public BrokerAccountEntity getActiveBrokerAccount() {
        List<BrokerAccountEntity> rows = jdbcTemplate.query("""
                SELECT id, broker, account_name, base_url, enabled, is_active, created_at,
                       api_key_encrypted, api_secret_encrypted, access_token_encrypted, updated_at
                FROM broker_account
                WHERE is_active = TRUE AND enabled = TRUE
                ORDER BY updated_at DESC
                LIMIT 1
                """, (rs, rowNum) -> mapRow(rs));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<BrokerAccountEntity> listBrokerAccounts(int limit) {
        return jdbcTemplate.query("""
                SELECT id, broker, account_name, base_url, enabled, is_active, created_at,
                       api_key_encrypted, api_secret_encrypted, access_token_encrypted, NULL::timestamptz AS updated_at
                FROM broker_account
                ORDER BY created_at DESC
                LIMIT ?
                """, (rs, rowNum) -> mapRow(rs), limit);
    }

    public BrokerAccountEntity createBrokerAccount(String broker, String accountName, String baseUrl,
                                                   String apiKeyEncrypted, String apiSecretEncrypted, String accessTokenEncrypted) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO broker_account (
                      broker, account_name, base_url, api_key_encrypted, api_secret_encrypted, access_token_encrypted
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, broker);
            ps.setString(2, accountName);
            ps.setString(3, baseUrl);
            ps.setString(4, apiKeyEncrypted);
            ps.setString(5, apiSecretEncrypted);
            ps.setString(6, accessTokenEncrypted);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return jdbcTemplate.queryForObject("""
                SELECT id, broker, account_name, base_url, enabled, is_active, created_at,
                       api_key_encrypted, api_secret_encrypted, access_token_encrypted, NULL::timestamptz AS updated_at
                FROM broker_account WHERE id = ?
                """, (rs, rowNum) -> mapRow(rs), key.longValue());
    }

    @Transactional
    public BrokerAccountEntity activateBrokerAccount(long accountId) {
        jdbcTemplate.update("UPDATE broker_account SET is_active = FALSE, updated_at = NOW() WHERE is_active = TRUE");
        List<BrokerAccountEntity> rows = jdbcTemplate.query("""
                UPDATE broker_account
                SET is_active = TRUE, updated_at = NOW()
                WHERE id = ? AND enabled = TRUE
                RETURNING id, broker, account_name, base_url, enabled, is_active, created_at,
                          api_key_encrypted, api_secret_encrypted, access_token_encrypted, updated_at
                """, (rs, rowNum) -> mapRow(rs), accountId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private BrokerAccountEntity mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new BrokerAccountEntity(
                rs.getLong("id"),
                rs.getString("broker"),
                rs.getString("account_name"),
                rs.getString("base_url"),
                rs.getObject("enabled", Boolean.class),
                rs.getObject("is_active", Boolean.class),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getString("api_key_encrypted"),
                rs.getString("api_secret_encrypted"),
                rs.getString("access_token_encrypted"),
                rs.getObject("updated_at", java.time.OffsetDateTime.class));
    }
}
