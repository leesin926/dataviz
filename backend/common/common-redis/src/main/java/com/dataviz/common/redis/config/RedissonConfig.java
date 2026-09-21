package com.dataviz.common.redis.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.TransportMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@AutoConfigureAfter(RedisAutoConfiguration.class)
public class RedissonConfig {

    private static final Logger log = LoggerFactory.getLogger(RedissonConfig.class);

    private final RedisProperties redisProperties;

    public RedissonConfig(RedisProperties redisProperties) {
        this.redisProperties = redisProperties;
        log.info("RedisProperties loaded: host={}, port={}, database={}, hasPassword={}",
                redisProperties.getHost(), redisProperties.getPort(),
                redisProperties.getDatabase(),
                redisProperties.getPassword() != null && !redisProperties.getPassword().isEmpty());
    }

    @Bean(destroyMethod = "shutdown")
    @Primary
    public RedissonClient redissonClient() {
        String host = redisProperties.getHost();
        int port = redisProperties.getPort();
        int database = redisProperties.getDatabase();
        String password = redisProperties.getPassword();

        log.info("Redisson connecting to {}:{}, database={}, hasPassword={}",
                host, port, database, password != null && !password.isEmpty());

        Config config = new Config();
        config.setTransportMode(TransportMode.NIO);

        String address = String.format("redis://%s:%d", host, port);

        config.useSingleServer()
                .setAddress(address)
                .setDatabase(database)
                .setPassword(password != null && !password.trim().isEmpty() ? password : null)
                .setConnectionMinimumIdleSize(8)
                .setConnectionPoolSize(32)
                .setIdleConnectionTimeout(10000)
                .setConnectTimeout(3000)
                .setTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(500);

        return Redisson.create(config);
    }
}
