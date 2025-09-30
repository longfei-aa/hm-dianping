package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.Outbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OutboxMapper extends BaseMapper<Outbox> {

    // 选取未发送、到达重试时间的记录
    @Select("SELECT * FROM outbox " +
            "WHERE sent = 0 AND (next_retry_at IS NULL OR next_retry_at <= NOW()) " +
            "ORDER BY id ASC " +
            "LIMIT #{limit}")
    List<Outbox> fetchUnsent(@Param("limit") int limit);

    @Update("UPDATE outbox SET sent = 1, updated_at = NOW() WHERE id = #{id}")
    int markSent(@Param("id") Long id);

    @Update("UPDATE outbox " +
            "SET retry_count = retry_count + 1, " +
            "    next_retry_at = DATE_ADD(NOW(), INTERVAL #{seconds} SECOND), " +
            "    updated_at = NOW() " +
            "WHERE id = #{id}")
    int markRetryLater(@Param("id") Long id, @Param("seconds") int seconds);
}
