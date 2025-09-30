package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> seckillScript;
    private final RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 活动时间与初步库存仍可做快速校验（DB 或缓存），可保留你的逻辑
        Long userId = UserHolder.getUser().getId();
        String stockKey = "seckill:stock:" + voucherId;
        String purchasedKey = "seckill:order:" + voucherId;
        String streamKey = "seckill:queue:global";

        long orderId = redisIdWorker.nextId("order");
        List<String> keys = Arrays.asList(stockKey, purchasedKey, streamKey);
        List<String> argv = Arrays.asList(
                userId.toString(),
                voucherId.toString(),
                String.valueOf(orderId),
                String.valueOf(System.currentTimeMillis())
        );


        @SuppressWarnings("unchecked")
        List<Object> r = (List<Object>) redis.execute(seckillScript, keys, argv.toArray());
        Long ok = (Long) r.get(0);
        if (ok != 1L) {
            String reason = (String) r.get(1);
            if ("BOUGHT".equals(reason)) return Result.fail("请勿重复下单");
            if ("NOSTOCK".equals(reason)) return Result.fail("库存不足");
            return Result.fail("系统繁忙，请稍后再试");
        }
        // 入队成功，立即把 orderId 返回给前端
        return Result.ok(orderId);
    }
}
