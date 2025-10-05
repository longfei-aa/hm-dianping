package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.hmdp.dto.Result;
import com.hmdp.entity.CacheEntry;
import com.hmdp.entity.Outbox;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.OutboxMapper;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.concurrent.*;

import static com.hmdp.utils.RedisConstants.*;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private OutboxMapper outboxMapper;
    @Resource
    private RedissonClient redissonClient;

    @Value("${bloom.shop.name:shopBloomFilter}")
    private String bloomName;

    private final ExecutorService rebuildPool = new ThreadPoolExecutor(
            2, 8, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            r -> new Thread(r, "shop-cache-rebuild")
    );

    // 原始分钟TTL
    private long baseTtlMinutes() { return CACHE_SHOP_TTL; }

    // 生成带抖动的TTL（-10% ~ +20%），错峰避免雪崩
    private Duration randomizedTtl() {
        long base = baseTtlMinutes();
        int jitter = ThreadLocalRandom.current().nextInt(
                (int) (-base * 0.10), (int) (base * 0.20) + 1
        );
        long ttl = Math.max(1, base + jitter);
        return Duration.ofMinutes(ttl);
    }

    // 简单分布式锁（SETNX + EX）
    private boolean tryLock(String key, long seconds) {
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(seconds));
        return Boolean.TRUE.equals(ok);
    }
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    // 将数据写入 Redis（使用逻辑过期JSON；硬TTL给 1.5x 作为兜底）
    private void setCacheJson(String key, Shop shop) {
        Duration ttl = randomizedTtl();
        long expireAt = System.currentTimeMillis() + ttl.toMillis();
        CacheEntry<Shop> entry = new CacheEntry<>(shop, expireAt);
        String json = cn.hutool.json.JSONUtil.toJsonStr(entry);
        stringRedisTemplate.opsForValue().set(key, json, ttl.multipliedBy(3).dividedBy(2));
    }

    // 同步回源并回填缓存（仅单飞线程执行）
    private Shop loadFromDBAndFill(Long id, String key) {
        Shop shop = getById(id);
        if (shop == null) {
            // 空值短缓存，进一步抗穿透（3~5分钟）
            CacheEntry<Shop> entry = new CacheEntry<>(null, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(3));
            stringRedisTemplate.opsForValue().set(key, cn.hutool.json.JSONUtil.toJsonStr(entry), Duration.ofMinutes(5));
            return null;
        }
        setCacheJson(key, shop);
        return shop;
    }

    @Override
    public Result queryById(Long id) {
        // 0.布隆过滤器拦截无效ID
        if (!redissonClient.getBloomFilter(bloomName).contains(id)) {
            return Result.fail("店铺不存在！");
        }

        String key = CACHE_SHOP_KEY + id;

        // 1.读缓存（String(JSON) 携带 expireAt 元数据）
        String val = stringRedisTemplate.opsForValue().get(key);
        long now = System.currentTimeMillis();

        if (val != null) {
            // 解析 JSON（注意泛型擦除，二次 toBean 更稳）
            CacheEntry<?> raw = cn.hutool.json.JSONUtil.toBean(val, CacheEntry.class);
            Shop cached = raw.data == null ? null : cn.hutool.json.JSONUtil.parseObj(raw.data).toBean(Shop.class);

            // 1.1 未逻辑过期：直接返回，最快路径
            if (raw.expireAt > now) {
                return cached == null ? Result.fail("店铺不存在！") : Result.ok(cached);
            }

            // 1.2 已逻辑过期：返回旧值 + 触发异步单飞重建
            String lockKey = CACHE_SHOP_LOCK + id;
            if (tryLock(lockKey, REBUILD_LOCK_SECONDS)) {
                rebuildPool.submit(() -> {
                    try {
                        loadFromDBAndFill(id, key);
                    } catch (Exception ignore) {
                    } finally {
                        unlock(lockKey);
                    }
                });
            }
            return cached == null ? Result.fail("店铺不存在！") : Result.ok(cached);
        }

        // 2.缓存完全未命中（冷启动/被动淘汰）：做“互斥回源”，避免第一波击穿
        String lockKey = CACHE_SHOP_LOCK + id;
        if (tryLock(lockKey, REBUILD_LOCK_SECONDS)) {
            try {
                Shop shop = loadFromDBAndFill(id, key);
                if (shop == null) return Result.fail("店铺不存在！");
                return Result.ok(shop);
            } finally {
                unlock(lockKey);
            }
        } else {
            // 未拿到锁：短暂等待再查一次缓存（大多已被别的线程填充）
            try { Thread.sleep(80); } catch (InterruptedException ignored) {}
            String again = stringRedisTemplate.opsForValue().get(key);
            if (again != null) {
                CacheEntry<?> raw = cn.hutool.json.JSONUtil.toBean(again, CacheEntry.class);
                Shop cached = raw.data == null ? null : cn.hutool.json.JSONUtil.parseObj(raw.data).toBean(Shop.class);
                return cached == null ? Result.fail("店铺不存在！") : Result.ok(cached);
            }
            // 兜底：直查DB并回填
            Shop shop = getById(id);
            if (shop == null) return Result.fail("店铺不存在！");
            setCacheJson(key, shop);
            return Result.ok(shop);
        }
    }


    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) return Result.fail("店铺id不能为空！");

        // 1. 更新数据库
        updateById(shop);

        // 2. 写 outbox（与更新同事务）
        String payload = cn.hutool.json.JSONUtil.createObj()
                .set("id", id)
                .toString();
        Outbox msg = Outbox.of("SHOP_UPDATED", String.valueOf(id), payload);
        outboxMapper.insert(msg);

        // 不在这里直接删缓存，交给消费者删
        return Result.ok();
    }

    @Override
    @Transactional
    public Result saveShop(Shop shop) {
        // 1.写数据库
        boolean ok = this.save(shop);
        if (!ok) {
            return Result.fail("新增店铺失败");
        }
        Long id = shop.getId();

        // 2.写布隆过滤器
        RBloomFilter<Long> bf = redissonClient.getBloomFilter(bloomName);
        bf.add(id);

        // 3.返回店铺id
        return Result.ok(id);
    }
}
