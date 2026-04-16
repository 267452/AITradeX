-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    user_id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nick_name VARCHAR(50),
    email VARCHAR(100),
    phonenumber VARCHAR(20),
    sex CHAR(1) DEFAULT '0',
    avatar VARCHAR(500),
    status CHAR(1) DEFAULT '0',
    login_ip VARCHAR(128),
    login_date TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    remark VARCHAR(500)
);

-- 登录日志表
CREATE TABLE IF NOT EXISTS sys_logininfor (
    info_id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50),
    ipaddr VARCHAR(128),
    login_location VARCHAR(255),
    browser VARCHAR(50),
    os VARCHAR(50),
    status CHAR(1) DEFAULT '0',
    msg VARCHAR(255),
    login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建用户索引
CREATE INDEX IF NOT EXISTS idx_sys_user_username ON sys_user(username);
CREATE INDEX IF NOT EXISTS idx_sys_user_status ON sys_user(status);
CREATE INDEX IF NOT EXISTS idx_sys_logininfor_username ON sys_logininfor(username);
CREATE INDEX IF NOT EXISTS idx_sys_logininfor_login_time ON sys_logininfor(login_time);

-- 插入默认管理员用户 (密码: admin123)
-- BCrypt 加密后的密码
INSERT INTO sys_user (username, password, nick_name, email, status, remark)
VALUES ('admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '管理员', 'admin@example.com', '0', '系统管理员')
ON CONFLICT (username) DO NOTHING;
