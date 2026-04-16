package com.repository;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SystemSettingRepository {
    private final JdbcTemplate jdbcTemplate;

    public SystemSettingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean pingPostgres() {
        Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return value != null && value == 1;
    }

    public String getSystemSetting(String key) {
        List<String> rows = jdbcTemplate.query("SELECT setting_value FROM system_setting WHERE setting_key = ?",
                (rs, rowNum) -> rs.getString(1), key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void upsertSystemSetting(String key, String value) {
        jdbcTemplate.update("""
                INSERT INTO system_setting(setting_key, setting_value, updated_at)
                VALUES (?, ?, NOW())
                ON CONFLICT (setting_key)
                DO UPDATE SET setting_value = EXCLUDED.setting_value, updated_at = NOW()
                """, key, value);
    }
}
