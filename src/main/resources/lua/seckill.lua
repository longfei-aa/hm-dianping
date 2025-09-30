-- KEYS[1] = stockKey        seckill:stock:{voucherId}
-- KEYS[2] = purchasedKey    seckill:order:{voucherId}
-- KEYS[3] = streamKey       seckill:queue:{voucherId}  (Redis Stream)
-- ARGV[1] = userId
-- ARGV[2] = voucherId
-- ARGV[3] = orderId
-- ARGV[4] = nowMillis (optional)

-- 已买过
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return {0, 'BOUGHT'}
end

-- 库存
local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
if stock <= 0 then
    return {0, 'NOSTOCK'}
end

-- 扣减 & 标记
redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])

-- 入队（与扣减同原子，防消息丢）
redis.call('XADD', KEYS[3], '*',
        'orderId',   ARGV[3],
        'userId',    ARGV[1],
        'voucherId', ARGV[2],
        'ts',        ARGV[4]
)

return {1, ARGV[3]}
