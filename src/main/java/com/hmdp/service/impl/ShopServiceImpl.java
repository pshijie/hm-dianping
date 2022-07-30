package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    // 使用Redis查询商铺
    @Override
    public Result queryById(Long id) {
        // ---------解决缓存穿透--------
//        Shop shop = queryWithPassThrough(id);
        // ---------解决缓存穿透--------

        // ---------互斥锁解决缓存击穿(包括了解决缓存穿透的代码)----------
//        Shop shop = queryWithMutex(id);
//        if (shop == null) {
//            return Result.fail("店铺不存在!");
//        }
        // ---------互斥锁解决缓存击穿(包括了解决缓存穿透的代码)----------


        // ---------逻辑过期解决缓存击穿(不包括了解决缓存穿透的代码)----------
//        Shop shop = queryWithLoginExpire(id);
//        if (shop == null) {
//            return Result.fail("店铺不存在!");
//        }
        // ---------逻辑过期解决缓存击穿(不包括了解决缓存穿透的代码)----------


        // ---------解决缓存穿透(使用封装好的工具类)----------
        Shop shop = cacheClient.queryWithPassThrough(
                CACHE_SHOP_KEY,
                id,
                Shop.class,
                this::getById,
                CACHE_SHOP_TTL,
                TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在!");
        }
        // ---------解决缓存穿透(使用封装好的工具类)----------

        // ---------逻辑过期解决缓存击穿(使用封装好的工具类)----------
//        Shop shop = cacheClient.queryWithLogicalExpire(
//                CACHE_SHOP_KEY,
//                id,
//                Shop.class,
//                this::getById,
//                CACHE_SHOP_TTL,
//                TimeUnit.MINUTES);
//        if (shop == null) {
//            return Result.fail("店铺不存在!");
//        }
        // ---------逻辑过期解决缓存击穿(使用封装好的工具类)----------

        return Result.ok(shop);
    }

//    public Shop queryWithMutex(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//        // 1. 使用redis查询商铺缓存
//        // 取出的值为字符串，后续需要转为对象
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        // 2.判断是否存在于redis中
//        if (StrUtil.isNotBlank(shopJson)) {
//            // 3.存在redis中就将Json转换为对象返回
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//
//        // -------解决缓存穿透---------
//        // 上述步骤2中，因为isNotBlank方法会把""判为false，所以需要再判断命中的是否为空值
//        if (shopJson != null) {
//            return null;
//        }
//        // -------解决缓存穿透---------
//
//        // 4.不存在redis中,进行缓存重建
//        // 4.1 获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;  // 每个店铺有自己的互斥锁
//        Shop shop = null;
//        try {
//            boolean isLock = tryLock(lockKey);
//            // 4.2 判断是否获取成功
//            if (!isLock) {
//                // 4.3 失败则休眠并重试
//                Thread.sleep(50);
//                return queryWithMutex(id);  // 递归
//            }
//            // 4.4 成功则根据Id查询数据库
//            shop = getById(id);
//            // 模拟创建的延时
//            Thread.sleep(200);
//            // 5.不存在数据库中返回错误
//            if (shop == null) {
//                // -------解决缓存穿透---------
//                // 将空值写入redis(TTL只设置为2分钟)
//                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                // -------解决缓存穿透---------
//                return null;
//            }
//            // 6.存在就写入redis
//            // 保存为字符串
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException();
//        } finally {
//            // 7.释放互斥锁
//            unlock(lockKey);
//        }
//
//        // 8.返回
//        return shop;
//    }


    // 创建线程池
//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

//    public Shop queryWithLoginExpire(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//        // 1. 使用redis查询商铺缓存
//        // 取出的值为字符串，后续需要转为对象
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        // 2.判断是否存在于redis中
//        if (StrUtil.isBlank(shopJson)) {
//            // 3.存在则返回null
//            return null;
//        }
//
//        // 4.命中则先把json反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        JSONObject data = (JSONObject) redisData.getData();  // 得到的data是json对象
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        // 5.判断是否过期
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            // 5.1 未过期，直接返回店铺信息
//            return shop;
//        }
//        // 5.2 已过期则进行缓存重建
//
//        // 6.缓存重建
//        String lockKey = LOCK_SHOP_KEY + id;
//        // 6.1 获取互斥锁
//        boolean isLock = tryLock(lockKey);
//        // 6.2 判断是否获取锁成功
//        if (isLock) {
//            // 6.3 成功，开启独立线程，实现缓存重建
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                try {
//                    this.saveShop2Redis(id, CACHE_SHOP_TTL);
//                } catch (Exception e) {
//                    throw new RuntimeException();
//                } finally {
//                    // 释放锁
//                    unlock(lockKey);
//                }
//            });
//        }
//        // 6.4 返回过期的店铺信息
//        return shop;
//    }

//    public Shop queryWithPassThrough(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//        // 1. 使用redis查询商铺缓存
//        // 取出的值为字符串，后续需要转为对象
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        // 2.判断是否存在于redis中
//        if (StrUtil.isNotBlank(shopJson)) {
//            // 3.存在redis中就将Json转换为对象返回
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//
//        // -------解决缓存穿透---------
//        // 上述步骤2中，因为isNotBlank方法会把""判为flase，所以需要再判断命中的是否为空值
//        if (shopJson != null) {
//            return null;
//        }
//        // -------解决缓存穿透---------
//
//        // 4.不存在redis中,根据id查询数据库
//        Shop shop = getById(id);
//        // 5.不存在数据库中返回错误
//        if (shop == null) {
//            // -------解决缓存穿透---------
//            // 将空值写入redis(TTL只设置为2分钟)
//            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//            // -------解决缓存穿透---------
//
//            return null;
//        }
//        // 6.存在就写入redis并返回
//        // 保存为字符串
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        return shop;
//    }

    // 尝试获取锁
//    private boolean tryLock(String key) {
//        // 这里的代码就是setnx，即如果该key已经存在，则无法将新的值复制给该key(相当于获取不到锁)
//        // 实现了互斥锁的效果
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
//        // 如果直接返回flag,会进行拆箱，可能返回null
//        return BooleanUtil.isTrue(flag);
//    }

    // 尝试释放锁
//    private void unlock(String key) {
//        // 删除该key相当于将互斥锁释放
//        stringRedisTemplate.delete(key);
//    }

    // 提前将热点店铺添加到redis中
//    public void saveShop2Redis(Long id, Long expireSeconds) {
//        // 1.查询店铺数据
//        Shop shop = getById(id);
//        // 2.封装逻辑过期时间
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        // 3.写入redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//    }

    // 使用Redis时更新商铺
    @Override
    @Transactional  // 加上事务，如果delete方法出现异常，则updateById也不会执行成功
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }

        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());

        return Result.ok();
    }
}
