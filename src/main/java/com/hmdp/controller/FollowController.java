package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;
    // 关注或取关用户
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId,
                         @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }

    // 判断是否关注用户
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId){
        return followService.isFollow(followUserId);
    }

    // 查询当前登录用户和查询用户的共同关注
    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long id){
        return followService.followCommons(id);
    }
}
