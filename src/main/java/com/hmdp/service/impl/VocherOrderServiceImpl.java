package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * @author psj
 * @date 2022/7/30 17:24
 * @File: VocherOrderServiceImpl.java
 * @Software: IntelliJ IDEA
 */

@Service
public class VocherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    /**
     * 实现优惠卷的下单
     *
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒数是否已经开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始!");
        }
        // 3.判断秒数是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束!");
        }
        // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足!");
        }
        Long userId = UserHolder.getUser().getId();
        // -------------实现一人一单(单机模式)---------------
        // 在实现一人一单时需要保证线程安全，且只能使用悲观锁，所以将代码进行封装，并使用synchronized
        // 给用户ID加锁即可
        // 由于toString底层是创建一个新对象,即使是同一个userId使用该方法后产生的对象也是不一样的
        // 所以使用intern方法，保证userId一样时，使用的都是同一个对象
//        synchronized (userId.toString().intern()) {
//            // 如果直接使用createVoucherOrder，调用的目标对象的createVoucherOrder方法,并不是代理对象的
//            // 事务并不会生效(事务的生效是Spring对当前类做了动态代理,然后使用代理对象进行事务处理)
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }
        // -------------实现一人一单(单机模式)---------------

        // -------------实现一人一单(集群模式)---------------
        // 创建锁对象:对于一个用户的下单(不管是单机还是集群)才需要上锁,所以设置的key需要加上userId
        // ---------------使用setnx方式---------------
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        // 获取锁
//        boolean isLock = lock.tryLock(1200);
        // ---------------使用setnx方式---------------

        // --------------使用Redisson方式----------------
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        // --------------使用Redisson方式----------------

        // 获取锁失败
        if (!isLock) {
            return Result.fail("一个用户不允许重复下单");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            // 释放锁
            lock.unlock();
        }
        // -------------实现一人一单(集群模式)---------------

    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // -------------实现一人一单---------------
        Long userId = UserHolder.getUser().getId();

        int count = query().eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();  // where user_id = userId and voucher_id = voucherId
        // 用户已经购买过一单,则不允许下单
        if (count > 0) {
            return Result.fail("用户已经购买过一次!");
        }
        // -------------实现一人一单---------------

        // 5.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")  // set stock = stock - 1
                .eq("voucher_id", voucherId)  // where voucher_id = voucherId
                // 使用乐观锁中CAS的方式防止超卖(如果判断stock=当前stock,则会导致很多线程都失败)
                .gt("stock", 0)  // and stock > 0
                .update();
        // 6.创建订单
        if (!success) {
            // 扣减失败
            return Result.fail("库存不足!");
        }

        // 7.返回订单ID(不是返回订单)
        VoucherOrder voucherOrder = new VoucherOrder();
        // 其他字段有默认值
        // 7.1 订单ID
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 7.2 用户ID
        voucherOrder.setUserId(userId);
        // 7.3 代金卷ID
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        return Result.ok(orderId);
    }
}
