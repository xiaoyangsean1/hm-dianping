package com.hmdp;

import cn.hutool.core.util.RandomUtil;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @小羊肖恩
 * @2022/11/19
 * @10:09
 * @Describe：
 */


public class Test {

    @org.junit.Test
    public void test(){
        for (int i = 0; i < 10; i++) {

            long l = RandomUtil.randomLong(1, 5);
            System.out.println(l);
        }
    }

}
