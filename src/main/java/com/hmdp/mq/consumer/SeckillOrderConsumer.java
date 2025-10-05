package com.hmdp.mq.consumer;

import com.hmdp.mq.config.SeckillMQ;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.OutboxMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.var;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Component
public class SeckillOrderConsumer {

    @Resource
    private VoucherOrderMapper orderMapper;

    @Transactional
    @RabbitListener(queues = SeckillMQ.QUEUE_CREATE)
    public void onCreateOrder(String payload) {
        var o = cn.hutool.json.JSONUtil.parseObj(payload);
        long orderId   = o.getLong("orderId");
        long userId    = o.getLong("userId");
        long voucherId = o.getLong("voucherId");

        // 幂等兜底：可以用唯一键 (user_id, voucher_id) 为主键
        VoucherOrder vo = new VoucherOrder();
        vo.setId(orderId);
        vo.setUserId(userId);
        vo.setVoucherId(voucherId);
        orderMapper.insert(vo);
    }
}

