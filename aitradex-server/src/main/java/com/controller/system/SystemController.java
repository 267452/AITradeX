package com.controller.system;

import com.common.api.ApiResponse;
import com.config.AppProperties;
import com.repository.SystemSettingRepository;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.JedisPooled;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final SystemSettingRepository systemSettingRepository;
    private final AppProperties properties;

    public SystemController(SystemSettingRepository systemSettingRepository, AppProperties properties) {
        this.systemSettingRepository = systemSettingRepository;
        this.properties = properties;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        boolean pgOk = systemSettingRepository.pingPostgres();
        boolean redisOk;
        try (JedisPooled jedis = new JedisPooled(java.net.URI.create(properties.getRedisUrl()))) {
            redisOk = "PONG".equalsIgnoreCase(jedis.ping());
        } catch (Exception e) {
            redisOk = false;
        }
        boolean ok = pgOk && redisOk;
        return ApiResponse.success(Map.of("status", ok ? "ok" : "degraded", "dependencies", Map.of("postgres", pgOk, "redis", redisOk)));
    }
}
