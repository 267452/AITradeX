package com.controller;

import com.common.api.ApiResponse;
import com.system.domain.request.LoginBody;
import com.system.domain.request.RegisterBody;
import com.system.domain.response.LoginVo;
import com.system.service.SysLoginService;
import com.util.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SysLoginService loginService;
    private final JwtUtils jwtUtils;

    public AuthController(SysLoginService loginService, JwtUtils jwtUtils) {
        this.loginService = loginService;
        this.jwtUtils = jwtUtils;
    }

    @PostMapping("/login")
    public ApiResponse<LoginVo> login(@RequestBody LoginBody loginBody, HttpServletRequest request) {
        String ip = getClientIp(request);
        LoginVo loginVo = loginService.login(loginBody, ip);
        return ApiResponse.success(loginVo);
    }

    @PostMapping("/register")
    public ApiResponse<LoginVo.UserInfo> register(@RequestBody RegisterBody registerBody) {
        LoginVo.UserInfo userInfo = loginService.register(registerBody);
        return ApiResponse.success(userInfo);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            String username = jwtUtils.getUsernameFromToken(token.substring(7));
            loginService.logout(username, token);
        }
        return ApiResponse.success();
    }

    @GetMapping("/userinfo")
    public ApiResponse<LoginVo.UserInfo> getUserInfo(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            return ApiResponse.error("未登录");
        }
        Long userId = jwtUtils.getUserIdFromToken(token.substring(7));
        LoginVo.UserInfo userInfo = loginService.getUserInfo(userId);
        return ApiResponse.success(userInfo);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
