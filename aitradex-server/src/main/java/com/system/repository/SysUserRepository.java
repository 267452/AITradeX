package com.system.repository;

import com.system.domain.entity.SysUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class SysUserRepository {

    private final JdbcTemplate jdbcTemplate;

    public SysUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<SysUser> ROW_MAPPER = new RowMapper<SysUser>() {
        @Override
        public SysUser mapRow(ResultSet rs, int rowNum) throws SQLException {
            SysUser user = new SysUser();
            user.setUserId(rs.getLong("user_id"));
            user.setUsername(rs.getString("username"));
            user.setPassword(rs.getString("password"));
            user.setNickName(rs.getString("nick_name"));
            user.setEmail(rs.getString("email"));
            user.setPhonenumber(rs.getString("phonenumber"));
            user.setSex(rs.getString("sex"));
            user.setAvatar(rs.getString("avatar"));
            user.setStatus(rs.getString("status"));
            user.setLoginIp(rs.getString("login_ip"));
            user.setLoginDate(rs.getTimestamp("login_date") != null 
                ? rs.getTimestamp("login_date").toLocalDateTime() : null);
            user.setCreateTime(rs.getTimestamp("create_time") != null 
                ? rs.getTimestamp("create_time").toLocalDateTime() : null);
            user.setUpdateTime(rs.getTimestamp("update_time") != null 
                ? rs.getTimestamp("update_time").toLocalDateTime() : null);
            user.setRemark(rs.getString("remark"));
            return user;
        }
    };

    public Optional<SysUser> findByUsername(String username) {
        String sql = "SELECT * FROM sys_user WHERE username = ?";
        List<SysUser> users = jdbcTemplate.query(sql, ROW_MAPPER, username);
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    public Optional<SysUser> findById(Long userId) {
        String sql = "SELECT * FROM sys_user WHERE user_id = ?";
        List<SysUser> users = jdbcTemplate.query(sql, ROW_MAPPER, userId);
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    public SysUser save(SysUser user) {
        if (user.getUserId() == null) {
            String sql = "INSERT INTO sys_user (username, password, nick_name, email, phonenumber, sex, avatar, status, remark) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            jdbcTemplate.update(sql, 
                user.getUsername(),
                user.getPassword(),
                user.getNickName(),
                user.getEmail(),
                user.getPhonenumber(),
                user.getSex() != null ? user.getSex() : "0",
                user.getAvatar(),
                user.getStatus() != null ? user.getStatus() : "0",
                user.getRemark());
            
            String idSql = "SELECT lastval()";
            Long id = jdbcTemplate.queryForObject(idSql, Long.class);
            user.setUserId(id);
        } else {
            String sql = "UPDATE sys_user SET nick_name = ?, email = ?, phonenumber = ?, sex = ?, " +
                        "avatar = ?, status = ?, update_time = CURRENT_TIMESTAMP WHERE user_id = ?";
            jdbcTemplate.update(sql,
                user.getNickName(),
                user.getEmail(),
                user.getPhonenumber(),
                user.getSex(),
                user.getAvatar(),
                user.getStatus(),
                user.getUserId());
        }
        return user;
    }

    public void updateLoginInfo(Long userId, String ip) {
        String sql = "UPDATE sys_user SET login_ip = ?, login_date = CURRENT_TIMESTAMP WHERE user_id = ?";
        jdbcTemplate.update(sql, ip, userId);
    }
}
