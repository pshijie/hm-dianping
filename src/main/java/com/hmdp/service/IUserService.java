package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpSession;

public interface IUserService extends IService<User> {

    // 用户签到
    Result login(LoginFormDTO loginForm, HttpSession session);

    // 发送验证码
    Result sendCode(String phone, HttpSession session);

    // 用户签到
    Result sign();

    // 统计用户连续签到天数
    Result signCount();
}
