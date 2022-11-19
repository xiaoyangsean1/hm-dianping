package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

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
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result getTypeList() {
        //1.从redis中查询商铺缓存
        String typeJSON = stringRedisTemplate.opsForValue().get(CACHE_SHOPTYPE_KEY);
        //2.判断是否命中
        if (StrUtil.isNotBlank(typeJSON)) {
            //3.命中，直接返回
            List<ShopType> shopTypes = JSONUtil.toList(typeJSON, ShopType.class);
            return Result.ok(shopTypes);
        }

        //判断命中的是否是空值
        if (typeJSON != null) {
            return Result.fail("该商铺类型不存在");
        }


        //4.未命中，根据id查询数据库
        List<ShopType> shopTypes = query().list();

        //5.判断是否存在
        if (shopTypes == null) {
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOPTYPE_KEY, "", CACHE_NULL_TTL + RandomUtil.randomLong(1, 5), TimeUnit.MINUTES);

            //6.不存在，返回错误信息
            return Result.fail("该商铺类型不存在！");
        }

        //7.存在，将商铺数据写入redis中
        stringRedisTemplate.opsForValue().set(CACHE_SHOPTYPE_KEY, JSONUtil.toJsonStr(shopTypes), CACHE_SHOP_TTL + RandomUtil.randomLong(1, 5), TimeUnit.MINUTES);

        //8.返回
        return Result.ok(shopTypes);
    }
}
