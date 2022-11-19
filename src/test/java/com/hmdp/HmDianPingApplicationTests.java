package com.hmdp;


import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

   @Test
    void testSaveShop() throws InterruptedException {
       Shop shop = shopService.getById(1l);
       cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1l, shop, 10l, TimeUnit.SECONDS);
   }
}
