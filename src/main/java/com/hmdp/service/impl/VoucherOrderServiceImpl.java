package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private SeckillVoucherServiceImpl seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //声明一个阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    //创建一个线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct  //@PostConstruct：在当前类初始化完成后执行此方法
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){
                try {
                    //1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();

                    //2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("请处理错误信息：", e);
                }
            }
        }
    }

    private IVoucherOrderService proxy;

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //获取用户信息
        Long userId = voucherOrder.getUserId();
        //创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        if (!isLock) {
            //获取失败
            log.error("不允许重复下单！");
            return ;
        }

        try {
            proxy.createVoucherOreder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();

        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        int r = result.intValue();

        //2.判断返回结果是否为0
        if(r != 0){
            //不为0，返回错误信息
            return Result.fail(r == 1 ? "库存不足！" : "您已经拥有该优惠券了！");
        }

        //3.有购买资格，将下单信息保存到阻塞队列中
        VoucherOrder voucherOrder = new VoucherOrder();
        //3.1.订单id
        long orderId = redisIdWorker.nextId("order:");
        voucherOrder.setId(orderId);
        //3.2.用户id
        voucherOrder.setUserId(userId);
        //3.3.代金券id
        voucherOrder.setVoucherId(voucherId);
        //3.4.放入阻塞队列
        orderTasks.add(voucherOrder);

        //4.获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        //5.返回订单id
        return Result.ok(orderId);

    }

    /*@Override
    public Result seckillVoucher(Long voucherId) {

        //1.查询优惠券信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        //2.判断秒杀是否开始
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀还未开始！");
        }

        //3.判断秒杀是否已经结束
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束咧！");
        }

        //4.开始，则判断库存是否充足
        Integer stock = seckillVoucher.getStock();
        if(stock < 1){
            return Result.fail("秒杀券库存不足！");
        }

        Long userID = UserHolder.getUser().getId();
        //使用intern();保证所得到的对象都是同一个
        //synchronized (userID.toString().intern()) {

        //创建锁对象
//        SimpleRedisLock redisLock = new SimpleRedisLock("order:" + userID, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userID);
        //获取锁
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        if (!isLock) {
            //获取失败
            return Result.fail("不允许重复下单！");
        }

        try {
            //获取代理对象（事务）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOreder(voucherId);
        } finally {
            lock.unlock();
        }
    }*/

    @Transactional
    public void createVoucherOreder(VoucherOrder voucherOrder) {
        //5.一人一单
        Long userID = voucherOrder.getUserId();

        //5.1.根据用户id和优惠券id查询订单id
        int count = query().eq("user_id", userID).eq("voucher_id", voucherOrder.getVoucherId()).count();

        //5.2.判断是否存在
        if (count > 0) {
            log.error("您已拥有这张券了！");
            return;
        }

        //5.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)     //添加乐观锁条件，判断库存是否 >0
                .update();

        if (!success) {
            log.error("库存不足！");
            return;
        }

        //6.创建订单
        save(voucherOrder);


    }

    /*@Transactional
    public Result createVoucherOreder(VoucherOrder voucherOrder) {
        //5.一人一单
        Long userID = UserHolder.getUser().getId();

        //5.1.根据用户id和优惠券id查询订单id
        int count = query().eq("user_id", userID).eq("voucher_id", voucherOrder).count();

        //5.2.判断是否存在
        if (count > 0) {
            return Result.fail("您已拥有这张券了！");
        }

        //5.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder)
                .gt("stock", 0)     //添加乐观锁条件，判断库存是否 >0
                .update();

        if (!success) {
            return Result.fail("库存不足！");
        }


        //6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();

        //6.1.订单id
        long orderID = redisIdWorker.nextId("order:");
        voucherOrder.setId(orderID);

        //6.2.用户id
        voucherOrder.setUserId(userID);

        //6.3.代金券id
        voucherOrder.setVoucherId(voucherOrder);

        //将优惠券订单保存到表中
        save(voucherOrder);

        //7.返回订单id

        return Result.ok(orderID);
    }*/
}
