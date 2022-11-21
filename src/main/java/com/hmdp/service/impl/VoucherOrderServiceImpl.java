package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private SeckillVoucherServiceImpl seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Override
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
    }

    @Transactional
    public Result createVoucherOreder(Long voucherId) {
        //5.一人一单
        Long userID = UserHolder.getUser().getId();

        //5.1.根据用户id和优惠券id查询订单id
        int count = query().eq("user_id", userID).eq("voucher_id", voucherId).count();

        //5.2.判断是否存在
        if (count > 0) {
            return Result.fail("您已拥有这张券了！");
        }

        //5.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
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
        voucherOrder.setVoucherId(voucherId);

        //将优惠券订单保存到表中
        save(voucherOrder);

        //7.返回订单id

        return Result.ok(orderID);
    }
}
