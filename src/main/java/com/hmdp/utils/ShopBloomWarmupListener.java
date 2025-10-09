package com.hmdp.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopBloomWarmupListener {

    private final RedissonClient redissonClient;
    private final ShopMapper shopMapper;

    @Value("${bloom.shop.name:shopBloomFilter}")
    private String bloomName;

    @Value("${bloom.shop.expectedInsertions:200000}")
    private long expectedInsertions;

    @Value("${bloom.shop.falsePositiveRate:0.01}")
    private double fpp;

    @Value("${bloom.shop.pageSize:2000}")
    private long pageSize;

    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        RLock lock = redissonClient.getLock("lock:bloom:shop:warmup");
        boolean locked = false;
        try {
            locked = lock.tryLock();
            if (!locked) {
                log.info("[Bloom] another node is warming up, skip.");
                return;
            }
            long t0 = System.currentTimeMillis();

            RBloomFilter<Long> bf = redissonClient.getBloomFilter(bloomName);
            if (!bf.isExists()) {
                bf.tryInit(expectedInsertions, fpp);
                log.info("[Bloom] {} initialized, expected={}, fpp={}", bloomName, expectedInsertions, fpp);
            }

            // 统计总量（如果没有 isDeleted 字段，可去掉 eq 条件）
            long total = shopMapper.selectCount(new LambdaQueryWrapper<Shop>()
                    //.eq(Shop::getIsDeleted, 0) // 视你的表结构决定要不要
            );
            if (total == 0) {
                log.info("[Bloom] no shop to warm.");
                return;
            }

            long pages = (total + pageSize - 1) / pageSize;
            LambdaQueryWrapper<Shop> qw = new LambdaQueryWrapper<Shop>()
                    .select(Shop::getId)        // 只查 id，省内存
                    .orderByAsc(Shop::getId);

            for (long current = 1; current <= pages; current++) {
                Page<Shop> page = new Page<>(current, pageSize);
                Page<Shop> result = shopMapper.selectPage(page, qw);
                for (Shop s : result.getRecords()) {
                    bf.add(s.getId());
                }
                if (current % 10 == 0 || current == pages) {
                    log.info("[Bloom] progress {}/{} (~{}%)", current, pages, current * 100 / pages);
                }
            }
            log.info("[Bloom] warmup done, total={}, cost={}ms", total, System.currentTimeMillis() - t0);
        } catch (Exception e) {
            log.error("[Bloom] warmup error", e);
        } finally {
            if (locked) lock.unlock();
        }
    }
}
