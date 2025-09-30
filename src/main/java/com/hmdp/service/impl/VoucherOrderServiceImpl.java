package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 *
 */
@Service
public class VoucherOrderServiceImpl
        extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private RedissonClient redissonClient;

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        // 基本校验（开始/结束/初步库存）——快速失败
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) return Result.fail("优惠券不存在");
        LocalDateTime now = LocalDateTime.now();
        if (voucher.getBeginTime().isAfter(now)) return Result.fail("秒杀尚未开始！");
        if (voucher.getEndTime().isBefore(now)) return Result.fail("秒杀已经结束！");
        if (voucher.getStock() < 1) return Result.fail("库存不足！");

        Long userId = UserHolder.getUser().getId();
        String lockKey = "lock:order:user:" + userId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            // waitTime=0 立即返回；leaseTime=10s 自动释放，避免忘记unlock导致死锁
            locked = lock.tryLock(0, 10, TimeUnit.SECONDS);
            if (!locked) {
                return Result.fail("请勿重复下单");
            }

            // ======= 临界区：查单→扣库存→创建订单 =======
            // 5. 查是否已下单
            int count = lambdaQuery()
                    .eq(VoucherOrder::getUserId, userId)
                    .eq(VoucherOrder::getVoucherId, voucherId)
                    .count();
            if (count > 0) {
                return Result.fail("用户已经购买过一次！");
            }

            // 6. 扣减库存（乐观更新，保证 stock > 0）
            boolean ok = seckillVoucherService.lambdaUpdate()
                    .setSql("stock = stock - 1")
                    .eq(SeckillVoucher::getVoucherId, voucherId)
                    .gt(SeckillVoucher::getStock, 0)
                    .update();
            if (!ok) {
                return Result.fail("库存不足！");
            }

            // 7. 创建订单
            long orderId = redisIdWorker.nextId("order");
            VoucherOrder order = new VoucherOrder();
            order.setId(orderId);
            order.setUserId(userId);
            order.setVoucherId(voucherId);
            save(order);

            return Result.ok(orderId);
            // ======= 临界区结束 =======

        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 触发唯一键冲突：说明另一并发已成功下单
            return Result.fail("请勿重复下单");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return Result.fail("系统繁忙，请稍后再试");
        } catch (Exception ex) {
            // 让 @Transactional 回滚
            throw ex;
        } finally {
            if (locked) {
                try { lock.unlock(); } catch (Exception ignore) {}
            }
        }
    }
}
