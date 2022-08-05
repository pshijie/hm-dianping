package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.baomidou.mybatisplus.core.toolkit.IdWorker.getId;
import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    // 查询热门Blog(根据点赞数)
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            // 查询blog是否被点过赞
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    // 根据Id查询Blog
    @Override
    public Result queryBlogById(Long id) {
        // 1.查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在!");
        }
        // 2.查询blog相关的用户
        queryBlogUser(blog);
        // 3.查询blog是否被点过赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    // 判断blog是否被点过赞,并且将点过该blog的用户保存到Redis中
    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录则不查询blog是否被点过赞
            return;
        }
        // 1.判断当前登录用户是否点过赞
        Long userId = user.getId();
        String key = BLOG_LIKED_KEY + blog.getId();
        // --------------未实现点赞排行榜---------------
//        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
//        blog.setIsLike(BooleanUtil.isTrue(isMember));
        // --------------未实现点赞排行榜---------------

        // --------------实现点赞排行榜(按点赞时间)---------------
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
        // --------------实现点赞排行榜(按点赞时间)---------------
    }

    // 点赞Blog(取消点赞blog)
    @Override
    public Result likeBlog(Long id) {
        // 1.判断当前登录用户是否点过赞
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        // --------------未实现点赞排行榜---------------
//        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
//        // 2.未点过赞
//        if (BooleanUtil.isFalse(isMember)) {
//            // 2.1 数据库点赞数+1
//            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
//            // 2.2 保存用户到Redis的set集合
//            if (isSuccess) {
//                stringRedisTemplate.opsForSet().add(key, userId.toString());
//            }
//        } else {  // 3.已经点过赞，点击后则取消点赞
//            // 3.1 数据库点赞数-1
//            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
//            // 3.2 将用户从Redis中的set集合移除
//            if (isSuccess) {
//                stringRedisTemplate.opsForSet().remove(key, userId.toString());
//            }
//        }
        // --------------未实现点赞排行榜---------------

        // --------------实现点赞排行榜(按点赞时间)---------------
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        // 2.未点过赞
        if (score == null) {
            // 2.1 数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 2.2 保存用户到Redis的SortedSet集合 zadd key value score
            if (isSuccess) {
                // 使用时间戳作为分数
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }

        } else {  // 3.已经点过赞，点击后则取消点赞
            // 3.1 数据库点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 3.2 将用户从Redis中的SortedSet集合移除
            if (isSuccess) {
                // 使用时间戳作为分数
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        // --------------实现点赞排行榜(按点赞时间)---------------

        return Result.ok();
    }

    // 点赞排行榜
    @Override
    public Result queryBlogLikes(Long id) {
        // 1.查询top5(点赞时间)的点赞用户
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5Id = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5Id == null || top5Id.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2.解析其中的用户Id
        List<Long> ids = top5Id.stream().map(Long::valueOf).collect(Collectors.toList());
        // 3.根据用户Id查询用户 where id in (5,1) order by Field(id, 5,1)
        // 上述SQL如果不加order by Field(id, 5,1),预期结果是先输出id=5的用户，但是在in(5,1)后是先输出id=1的用户
        String idStr = StrUtil.join(",", ids);  // 将ids中的所有Id进行拼接
        List<UserDTO> userDTOS =
                userService.query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                        .stream()
                        .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                        .collect(Collectors.toList());

        return Result.ok(userDTOS);
    }

    // 查询Blog的相关用户
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
