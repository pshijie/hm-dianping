package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * @author psj
 * @date 2022/8/6 20:55
 * @File: ScrollResult.java
 * @Software: IntelliJ IDEA
 */
@Data
public class ScrollResult {
    private List<?> list;  // 小于指定时间戳的blog集合
    private Long minTime;  // 当前查询推送的blog集合对应的最小时间戳(即Redis中的score)
    private Integer offset;  // 偏移量
}
