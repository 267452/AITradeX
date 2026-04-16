package com.system.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.common.exception.BusinessException;
import com.system.domain.entity.SysUser;
import com.system.domain.request.LoginBody;
import com.system.domain.request.RegisterBody;
import com.system.domain.response.LoginVo;
import com.system.repository.SysLogininforRepository;
import com.system.repository.SysUserRepository;
import com.util.JwtUtils;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SysLoginService {

    private final SysUserRepository userRepository;
    private final SysLogininforRepository logininforRepository;
    private final JwtUtils jwtUtils;

    private static final int MAX_RETRY_COUNT = 5;
    private static final long LOCK_TIME_MS = 10 * 60 * 1000;

    private final ConcurrentHashMap<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();

    public SysLoginService(SysUserRepository userRepository, 
                          SysLogininforRepository logininforRepository,
                          JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.logininforRepository = logininforRepository;
        this.jwtUtils = jwtUtils;
    }

    public LoginVo login(LoginBody loginBody, String ip) {
        String username = loginBody.getUsername();
        String password = loginBody.getPassword();

        checkLoginAttempts(username);

        SysUser user = userRepository.findByUsername(username)
            .orElseThrow(() -> {
                recordLogininfor(username, ip, "1", "用户不存在");
                return new BusinessException("用户不存在或密码错误");
            });

        if ("1".equals(user.getStatus())) {
            recordLogininfor(username, ip, "1", "账户已停用");
            throw new BusinessException("账户已停用");
        }

        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), user.getPassword());
        if (!result.verified) {
            handleLoginFail(username, ip);
            throw new BusinessException("用户不存在或密码错误");
        }

        clearLoginAttempts(username);

        userRepository.updateLoginInfo(user.getUserId(), ip);
        recordLogininfor(username, ip, "0", "登录成功");

        String token = jwtUtils.generateToken(user.getUserId(), username);

        LoginVo.UserInfo userInfo = new LoginVo.UserInfo(
            user.getUserId(),
            user.getUsername(),
            user.getNickName(),
            user.getEmail(),
            user.getAvatar()
        );

        return new LoginVo(token, jwtUtils.getExpireIn(), userInfo);
    }

    public void logout(String username, String token) {
        jwtUtils.invalidateToken(token);
        recordLogininfor(username, "0.0.0.0", "0", "退出成功");
    }

    public LoginVo.UserInfo register(RegisterBody registerBody) {
        String username = registerBody.getUsername();
        String password = registerBody.getPassword();
        String confirmPassword = registerBody.getConfirmPassword();

        if (username == null || username.trim().isEmpty()) {
            throw new BusinessException("用户名不能为空");
        }
        if (username.length() < 2 || username.length() > 30) {
            throw new BusinessException("用户名长度必须在2-30个字符之间");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new BusinessException("密码不能为空");
        }
        if (password.length() < 6 || password.length() > 30) {
            throw new BusinessException("密码长度必须在6-30个字符之间");
        }
        if (!password.equals(confirmPassword)) {
            throw new BusinessException("两次输入的密码不一致");
        }

        if (userRepository.findByUsername(username).isPresent()) {
            throw new BusinessException("用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(encryptPassword(password));
        user.setNickName(registerBody.getNickName() != null ? registerBody.getNickName() : username);
        user.setEmail(registerBody.getEmail());
        user.setStatus("0");

        SysUser savedUser = userRepository.save(user);

        return new LoginVo.UserInfo(
            savedUser.getUserId(),
            savedUser.getUsername(),
            savedUser.getNickName(),
            savedUser.getEmail(),
            savedUser.getAvatar()
        );
    }

    public LoginVo.UserInfo getUserInfo(Long userId) {
        SysUser user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("用户不存在"));
        return new LoginVo.UserInfo(
            user.getUserId(),
            user.getUsername(),
            user.getNickName(),
            user.getEmail(),
            user.getAvatar()
        );
    }

    public String encryptPassword(String rawPassword) {
        return BCrypt.withDefaults().hashToString(10, rawPassword.toCharArray());
    }

    private void checkLoginAttempts(String username) {
        LoginAttempt attempt = loginAttempts.get(username);
        if (attempt != null) {
            if (System.currentTimeMillis() - attempt.firstFailTime < LOCK_TIME_MS) {
                if (attempt.count.get() >= MAX_RETRY_COUNT) {
                    long remainingTime = (LOCK_TIME_MS - (System.currentTimeMillis() - attempt.firstFailTime)) / 1000;
                    throw new BusinessException("密码连续错误" + MAX_RETRY_COUNT + "次，账号锁定" + (remainingTime / 60) + "分钟");
                }
            } else {
                clearLoginAttempts(username);
            }
        }
    }

    private void handleLoginFail(String username, String ip) {
        LoginAttempt attempt = loginAttempts.computeIfAbsent(username, k -> new LoginAttempt());
        attempt.count.incrementAndGet();
        attempt.firstFailTime = System.currentTimeMillis();
        recordLogininfor(username, ip, "1", "密码错误");
    }

    private void clearLoginAttempts(String username) {
        loginAttempts.remove(username);
    }

    private void recordLogininfor(String username, String ip, String status, String msg) {
        logininforRepository.recordLogininfor(username, ip, status, msg);
    }

    private static class LoginAttempt {
        AtomicInteger count = new AtomicInteger(0);
        long firstFailTime;
    }
}
