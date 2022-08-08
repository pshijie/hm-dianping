package com.hmdp.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

    /**
     * 根据id查询商铺信息
     *
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    // 不使用Redis，直接查询数据库
//    public Result queryShopById(@PathVariable("id") Long id) {
//        return Result.ok(shopService.getById(id));
//    }
    // 使用Redis
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

    /**
     * 新增商铺信息
     *
     * @param shop 商铺数据
     * @return 商铺id
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        // 写入数据库
        shopService.save(shop);
        // 返回店铺id
        return Result.ok(shop.getId());
    }

    /**
     * 更新商铺信息
     *
     * @param shop 商铺数据
     * @return 无
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        // ---------不使用redis时，直接更新数据库---------------
        // 写入数据库
//        shopService.updateById(shop);
//        return Result.ok();
        // ---------不使用redis时，直接更新数据库---------------

        // ----------使用redis时-------------
        return shopService.update(shop);
        // ----------使用redis时-------------
    }

    /**
     * 根据商铺类型分页查询商铺信息
     *
     * @param typeId  商铺类型
     * @param current 页码
     * @param x 用户坐标x
     * @param y 用户坐标y
     * @return 商铺列表
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,  // 用户位置x(以用户为圆心)
            @RequestParam(value = "y", required = false) Double y  // 用户位置y(以用户为圆心)
    ) {
        // ------------------未实现附近商户功能-------------
//        // 根据类型分页查询
//        Page<Shop> page = shopService.query()
//                .eq("type_id", typeId)
//                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
//        // 返回数据
//        return Result.ok(page.getRecords());
        // ------------------未实现附近商户功能-------------

        // ------------------实现附近商户功能-------------
        return shopService.queryShopByType(typeId, current, x, y);
        // ------------------实现附近商户功能-------------
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     *
     * @param name    商铺名称关键字
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
}
