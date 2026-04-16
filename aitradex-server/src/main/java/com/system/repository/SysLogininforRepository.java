package com.system.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class SysLogininforRepository {

    private final JdbcTemplate jdbcTemplate;

    public SysLogininforRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordLogininfor(String username, String ip, String status, String msg) {
        String sql = "INSERT INTO sys_logininfor (username, ipaddr, status, msg, login_time) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, username, ip, status, msg, LocalDateTime.now());
    }
}
