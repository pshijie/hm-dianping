package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;

public interface IBlogService extends IService<Blog> {

    // 查询热门Blog(根据点赞数)
    Result queryHotBlog(Integer current);

    // 根据Id查询Blog
    Result queryBlogById(Long id);

    // 对blog进行点赞
    Result likeBlog(Long id);

    // 查询对当前blog点过赞点过赞的用户
    Result queryBlogLikes(Long id);

    // 登录用户保存探店blog
    Result saveBlog(Blog blog);

    // 返回登录用户的关注用户发表的blog
    Result queryBlogOfFollow(Long max, Integer offset);
}
