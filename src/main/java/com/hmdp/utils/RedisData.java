package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData {
    private LocalDateTime expireTime;  // 为了解决缓存击穿设置的逻辑过期时间
    private Object data;  // 存入redis的数据，这样就不需要在Shop类中继承该类,进而获取expireTime属性
}
