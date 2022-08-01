package com.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)  // 暴露代理对象(在VocherOrderServiceImpl中就使用了需要使用代理对象)
@MapperScan("com.hmdp.mapper")
@SpringBootApplication
public class HmDianpingApplication {

    public static void main(String[] args) {
        SpringApplication.run(HmDianpingApplication.class, args);
    }

}
