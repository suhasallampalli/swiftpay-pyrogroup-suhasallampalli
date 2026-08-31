package com.swiftpay.gateway.config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@TestConfiguration
public class TestRedisConfig {

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, String> redisTemplate() {
        ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);

        Mockito.when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenAnswer(inv -> store.putIfAbsent(inv.getArgument(0), inv.getArgument(1)) == null);

        Mockito.when(valueOps.setIfAbsent(anyString(), anyString()))
            .thenAnswer(inv -> store.putIfAbsent(inv.getArgument(0), inv.getArgument(1)) == null);

        Mockito.when(valueOps.get(any()))
            .thenAnswer(inv -> store.get(inv.getArgument(0)));

        RedisTemplate<String, String> template = Mockito.mock(RedisTemplate.class);
        Mockito.when(template.opsForValue()).thenReturn(valueOps);

        return template;
    }
}
