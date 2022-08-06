package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;

/**
 * @author psj
 * @date 2022/8/6 15:30
 * @File: IFollowService.java
 * @Software: IntelliJ IDEA
 */
public interface IFollowService extends IService<Follow> {
    // 关注或取关用户
    Result follow(Long followUserId, Boolean isFollow);

    // 判断是否关注用户
    Result isFollow(Long followUserId);

    // 查询当前登录用户和查询用户的共同关注
    Result followCommons(Long id);

}
