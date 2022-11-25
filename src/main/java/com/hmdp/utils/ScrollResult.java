package com.hmdp.utils;

import lombok.Data;

import java.util.List;

/**
 * @小羊肖恩
 * @2022/11/25
 * @10:05
 * @Describe：
 */

@Data
public class ScrollResult {

    private List<?> lit;

    private Long minTime;

    private Integer offset;
}
