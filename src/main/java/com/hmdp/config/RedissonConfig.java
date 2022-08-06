package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author psj
 * @date 2022/8/2 17:28
 * @File: RedissonConfig.java
 * @Software: IntelliJ IDEA
 */
@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("xxxx");
        return Redisson.create(config);
    }
}
