package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_LIST;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> queryTypeList() {
        // 1. 读缓存
        String json = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_LIST);
        if (StrUtil.isNotBlank(json)) {
            List<ShopType> cached = JSONUtil.toList(json, ShopType.class);
            return cached;
        }

        // 2. 查库（按 sort 升序）
        List<ShopType> typeList = query().orderByAsc("sort").list();
        if (typeList == null || typeList.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 回写缓存（整段 JSON）
        stringRedisTemplate.opsForValue().set(
                CACHE_SHOP_TYPE_LIST,
                JSONUtil.toJsonStr(typeList),
                30, TimeUnit.MINUTES
        );
        return typeList;
    }
}
