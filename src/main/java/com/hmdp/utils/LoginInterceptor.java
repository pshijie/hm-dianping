package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginInterceptor implements HandlerInterceptor {

    // -------------------------未进行拦截器优化---------------------------
    // 因为LoginInterceptor类没有被Spring接管(即没有注解)，所以不能使用@Autowired
    // 只能使用构造器的方式将其注入属性
//    private StringRedisTemplate stringRedisTemplate;
//
//    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
//        this.stringRedisTemplate = stringRedisTemplate;
//    }
    // -------------------------未进行拦截器优化---------------------------

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // -------------------------未进行拦截器优化---------------------------

        // ----------------使用redis---------------------
//        // 1.获取请求头中的token
//        // 前端代码已经实现将token(不包括前缀)放在浏览器的header中
//        String token = request.getHeader("authorization");
//        if (StrUtil.isBlank(token)) {
//            // 不存在就拦截
//            response.setStatus(401);
//            return false;
//        }
//        // 2.基于token获取redis中的用户
//        // 使用entries返回整个Map,并且注意需要加上前缀去redis中取
//        String key = RedisConstants.LOGIN_USER_KEY + token;
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
//        if (userMap.isEmpty()) {
//            // 不存在就拦截
//            response.setStatus(401);
//            return false;
//        }
//        // 4.将查询到的Hash数据转为UserDTO对象并存储
//        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
//        UserHolder.saveUser(userDTO);
//        // 5.刷新token有效期
//        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
//        // 6.放行
//        return true;
        // ----------------使用redis---------------------

        // ----------------使用session----------------
//        //1. 获取session
//        HttpSession session = request.getSession();
//        //2.获取session中的用户
//        Object user = session.getAttribute("user");
//        //3. 判断用户是否存在
//        if (user == null){
//            //4. 不存在，拦截
//            // 401表示未授权
//            response.setStatus(401);
//            return false;
//        }
//
//        //5. 存在就保存用户信息到ThreadLocal
//        UserHolder.saveUser((UserDTO) user);
//        //6. 放行
//        return true;
        // ----------------使用session----------------

        // -------------------------未进行拦截器优化---------------------------

        // -------------------------进行拦截器优化---------------------------
        // 1.判断是否需要拦截(即判断ThreadLocal中是否存在用户)
        if (UserHolder.getUser() == null){
            // 不存在就拦截
            response.setStatus(401);
            return false;
        }
        //存在用户就放行
        return true;
        // -------------------------进行拦截器优化---------------------------

    }

    @Override
    // 登录完成后销毁用户信息，避免内存泄露
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }
}
