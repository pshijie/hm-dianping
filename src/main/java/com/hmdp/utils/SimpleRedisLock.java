package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

// 基于Redis实现分布式锁
public class SimpleRedisLock implements ILock {

    private String name;  // 锁的名称
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    // 声明为static final是为了该类在加载时就将初始化Lua脚本
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // ----------------实现1---------------
//        // 获取线程标示
//        long threadId = Thread.currentThread().getId();
//        // 获取锁
//        Boolean success = stringRedisTemplate.opsForValue()
//                .setIfAbsent(KEY_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);
        // ----------------实现1---------------

        // ----------------实现2---------------
        // 获取线程标示
        String threadId = ID_PREFIX + Thread.currentThread().getId();  // 加上线程标识(UUID是根据线程ID生成的)
        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        // ----------------实现2---------------

        // 因为函数的返回值是boolean,如果直接返回success会自动拆箱,有可能导致空指针异常
        return Boolean.TRUE.equals(success);
    }

//    @Override
//    public void unlock() {
//        // ----------------实现1----------------
//        // 释放锁
////        stringRedisTemplate.delete(KEY_PREFIX + name);
//        // ----------------实现1----------------
//
//        // ----------------实现2----------------
//        // 获取线程标识
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        // 获取锁中的标识
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        // 判断锁中的标识和线程标识是否一致
//        if (threadId.equals(id)) {
//            // 释放锁
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//        // ----------------实现2----------------
//    }


    // ---------------实现3:使用Lua脚本-----------------
    @Override
    public void unlock() {
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),  // 单元素的集合
                ID_PREFIX + Thread.currentThread().getId()
        );
    }
    // ---------------实现3:使用Lua脚本-----------------

}
