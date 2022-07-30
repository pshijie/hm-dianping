package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1. 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }

        //3. 符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        // --------------将验证码从保存到session改为保存到redis--------------
        //4. 保存验证码到session
//        session.setAttribute("code",code);

        // 4. 保存验证码到redis
        // 加上静态变量LOGIN_CODE_KEY(即"login:code:")前缀让数据更加清晰
        // 并且为验证码设置两分钟(即静态变量LOGIN_CODE_TTL)的有效期
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // --------------将验证码从保存到session改为保存到redis--------------

        //5. 发送验证码
        log.debug("发送短信验证码成功，验证码:{}", code);
        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1. 校验手机号(尽管在发送验证码时已经进行了校验，但是在登录的时候依旧要进行一次)
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        //2. 校验验证码
        // ------------将从session中获取验证码改为从redis获取---------
//        Object cacheCode = session.getAttribute("code");
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        // ------------将从session中获取验证码改为从redis获取---------

        String code = loginForm.getCode();

//        if (cacheCode == null || !cacheCode.toString().equals(code)) {
        if (cacheCode == null || !cacheCode.equals(code)) {
            //3. 不一致，报错
            return Result.fail("验证码错误");
        }

        //4.一致，根据手机号查询用户
        // mybatisPlus
        User user = query().eq("phone", phone).one();

        //5. 判断用户是否存在
        if (user == null) {
            //6. 不存在，创建新用户
            // 只传phone的原因是其他信息都可以使用默认值(昵称可以随机初始化)
            user = createUserWithPhone(phone);
        }

        // --------------将用户信息从保存到session改为保存到redis--------------
        //7.保存用户信息到session
        // 为了保存到session的信息是部分信息，所以将User的完整信息提取部分信息保存到UserDTO
        // session保存在服务器端，太多信息会给服务器造成太大压力
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        // 7.保存用户信息到redis
        // 7.1 随机生成token
        String token = UUID.randomUUID().toString(true);
        // 7.2 将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 将对象转换为Map要保证Map中的每一个key都是String类型(在UserDTO类中id是Long)
        // 不是的话无法保存到redis中
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        String tokenKey = LOGIN_USER_KEY + token;  // 同样加上前缀
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 7.3 设置token有效期(刷新在LoginInterceptor中实现)
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // --------------将用户信息从session改为保存到redis--------------

        return Result.ok(token);
    }


    private User createUserWithPhone(String phone) {
        // 1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 2.保存用户
        save(user);
        return user;
    }
}
