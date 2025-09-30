package com.hmdp.mq;

import com.hmdp.config.SeckillMQ;
import com.hmdp.entity.Outbox;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.OutboxMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.var;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final VoucherOrderMapper orderMapper;
    private final OutboxMapper outboxMapper;

    @Transactional
    @RabbitListener(queues = SeckillMQ.QUEUE_CREATE)
    public void onCreateOrder(String payload) {
        var o = cn.hutool.json.JSONUtil.parseObj(payload);
        long orderId   = o.getLong("orderId");
        long userId    = o.getLong("userId");
        long voucherId = o.getLong("voucherId");

        // 幂等兜底：唯一键 (user_id, voucher_id) 或 orderId 为主键
        try {
            VoucherOrder vo = new VoucherOrder();
            vo.setId(orderId);
            vo.setUserId(userId);
            vo.setVoucherId(voucherId);
            orderMapper.insert(vo);
        } catch (org.springframework.dao.DuplicateKeyException ignore) {
            // 已处理过，直接视作成功
        }
    }
}

