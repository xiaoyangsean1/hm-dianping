package com.hmdp;


import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

   @Test
    void testSaveShop() throws InterruptedException {
       Shop shop = shopService.getById(1l);
       cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1l, shop, 10l, TimeUnit.SECONDS);
   }

   @Test
    void testRedisId(){
       for (int i = 0; i < 100; i++) {
           System.out.println(redisIdWorker.nextId("shop"));
       }
   }

   @Test
   void loadShopData(){
       //1.查询店铺信息
       List<Shop> list = shopService.list();

       //2.将店铺分组，按照typeId分组，typeId一致的放到一个集合中
       Map<Long, List<Shop>> map = list
               .stream()
               .collect(Collectors.groupingBy(Shop::getTypeId));
       //3.分批完成写入Redis

       for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
           //3.1.获取类型id
           Long typeId = entry.getKey();
           String key = SHOP_GEO_KEY + typeId;

           //3.2.获取同类型的店铺的集合
           List<Shop> value = entry.getValue();
           List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());

           //3.3.写入redis
           for (Shop shop : value) {
               //stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
               locations.add(new RedisGeoCommands.GeoLocation<>(
                       shop.getId().toString(),
                       new Point(shop.getX(), shop.getY())
               ));
           }

           stringRedisTemplate.opsForGeo().add(key, locations);
       }
   }
}
