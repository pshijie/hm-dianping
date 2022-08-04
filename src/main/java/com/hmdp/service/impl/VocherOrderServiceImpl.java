package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author psj
 * @date 2022/7/30 17:24
 * @File: VocherOrderServiceImpl.java
 * @Software: IntelliJ IDEA
 */
@Slf4j  // 用于记录异常
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
    // -----------------------秒杀功能未优化--------------------
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 1.查询优惠卷
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        // 2.判断秒数是否已经开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始!");
//        }
//        // 3.判断秒数是否已经结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束!");
//        }
//        // 4.判断库存是否充足
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足!");
//        }
//        Long userId = UserHolder.getUser().getId();
//        // -------------实现一人一单(单机模式)---------------
//        // 在实现一人一单时需要保证线程安全，且只能使用悲观锁，所以将代码进行封装，并使用synchronized
//        // 给用户ID加锁即可
//        // 由于toString底层是创建一个新对象,即使是同一个userId使用该方法后产生的对象也是不一样的
//        // 所以使用intern方法，保证userId一样时，使用的都是同一个对象
////        synchronized (userId.toString().intern()) {
////            // 如果直接使用createVoucherOrder，调用的目标对象的createVoucherOrder方法,并不是代理对象的
////            // 事务并不会生效(事务的生效是Spring对当前类做了动态代理,然后使用代理对象进行事务处理)
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }
//        // -------------实现一人一单(单机模式)---------------
//
//        // -------------实现一人一单(集群模式)---------------
//        // 创建锁对象:对于一个用户的下单(不管是单机还是集群)才需要上锁,所以设置的key需要加上userId
//        // ---------------使用setnx方式---------------
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        // 获取锁
////        boolean isLock = lock.tryLock(1200);
//        // ---------------使用setnx方式---------------
//
//        // --------------使用Redisson方式----------------
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        boolean isLock = lock.tryLock();
//        // --------------使用Redisson方式----------------
//
//        // 获取锁失败
//        if (!isLock) {
//            return Result.fail("一个用户不允许重复下单");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            // 释放锁
//            lock.unlock();
//        }
//        // -------------实现一人一单(集群模式)---------------
//    }
    // -----------------------秒杀功能未优化--------------------

    // -----------------------秒杀功能优化--------------------
    // 声明为static final是为了该类在加载时就将初始化Lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 一个线程尝试从队列中获取元素时，如果队列中没有元素则线程被阻塞
    private ArrayBlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    // 创建线程池(处理订单只需要单线程即可)
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // Spring的注解，表示当前类初始化完毕后执行的方法
    @PostConstruct
    private void init() {
        // VoucherOrderHandler中定义的任务需要类一初始化就开始执行
        // 因为队列中一有订单信息存入就需要去执行任务
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // 获取了订单信息后需要执行的任务(即保存到数据库)
    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2.创建订单
                    handlerVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    private void handlerVoucherOrder(VoucherOrder voucherOrder) {
        // 获取用户Id(不能从原userHolder中获取,因为此时是另一个线程在执行)
        Long userId = voucherOrder.getUserId();
        // 创建锁对象:对于一个用户的下单(不管是单机还是集群)才需要上锁,所以设置的key需要加上userId
        // --------------使用Redisson方式----------------
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        // --------------使用Redisson方式----------------

        // 获取锁失败
        if (!isLock) {
            log.error("不允许重复下单");
            return;
        }
        try {
            // 该处的proxy是主线程创建的，如果在当前方法创建proxy并使用,则proxy是线程池中线程创建的
            // 而AopContext.currentProxy()底层使用的是ThreadLocal,意味着创建的proxy与创建该对象的线程相关联
            // 执行的proxy.createVoucherOrder方法和seckillVoucher方法都不在一个事务中,就保持不了在同一个事务中
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.执行Lua脚本，判断有无抢购资格(为0即有购买资格)
        // 获取用户Id(用于传入Lua脚本)
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),  // 因为没有key,只有argv
                voucherId.toString(), userId.toString()
        );
        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 2.执行到该步骤说明有购买资格,把下单信息保存到阻塞队列
        long orderId = redisIdWorker.nextId("order");
        // 2.1 封装订单信息
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        // 2.2 将信息放入阻塞队列
        orderTasks.add(voucherOrder);

        // 3.初始化代理对象给另一个线程使用(这样保证另一个线程和主线程使用相同的代理对象)
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        return Result.ok(orderId);
    }

    // 因为在handlerVoucherOrder方法中对传入参数进行了修改，所以对该方法进行重构
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // -------------实现一人一单---------------
        Long userId = voucherOrder.getUserId();

        int count = query().eq("user_id", userId)
                .eq("voucher_id", voucherOrder.getVoucherId())
                .count();  // where user_id = userId and voucher_id = voucherId
        // 用户已经购买过一单,则不允许下单
        if (count > 0) {
            log.error("用户已经购买过一次");
            return;
        }
        // -------------实现一人一单---------------

        // 5.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")  // set stock = stock - 1
                .eq("voucher_id", voucherOrder.getVoucherId())  // where voucher_id = voucherId
                // 使用乐观锁中CAS的方式防止超卖(如果判断stock=当前stock,则会导致很多线程都失败)
                .gt("stock", 0)  // and stock > 0
                .update();
        // 6.创建订单
        if (!success) {
            // 扣减失败
            log.error("库存不足!");
            return;
        }

        // 7.保持订单到数据库中
        save(voucherOrder);
    }
    // -----------------------秒杀功能优化--------------------

//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        // -------------实现一人一单---------------
//        Long userId = UserHolder.getUser().getId();
//
//        int count = query().eq("user_id", userId)
//                .eq("voucher_id", voucherId)
//                .count();  // where user_id = userId and voucher_id = voucherId
//        // 用户已经购买过一单,则不允许下单
//        if (count > 0) {
//            return Result.fail("用户已经购买过一次!");
//        }
//        // -------------实现一人一单---------------
//
//        // 5.扣减库存
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock - 1")  // set stock = stock - 1
//                .eq("voucher_id", voucherId)  // where voucher_id = voucherId
//                // 使用乐观锁中CAS的方式防止超卖(如果判断stock=当前stock,则会导致很多线程都失败)
//                .gt("stock", 0)  // and stock > 0
//                .update();
//        // 6.创建订单
//        if (!success) {
//            // 扣减失败
//            return Result.fail("库存不足!");
//        }
//
//        // 7.返回订单ID(不是返回订单)
//        VoucherOrder voucherOrder = new VoucherOrder();
//        // 其他字段有默认值
//        // 7.1 订单ID
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        // 7.2 用户ID
//        voucherOrder.setUserId(userId);
//        // 7.3 代金卷ID
//        voucherOrder.setVoucherId(voucherId);
//        save(voucherOrder);
//
//        return Result.ok(orderId);
//    }
}
