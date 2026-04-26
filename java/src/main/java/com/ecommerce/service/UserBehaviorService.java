package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.entity.UserBehavior;

import java.util.List;

/**
 * 用户行为日志服务接口
 */
public interface UserBehaviorService extends IService<UserBehavior> {

    /**
     * 查询用户最近的行为记录（默认最近30天）
     */
    default List<UserBehavior> listRecentByUserId(String userId, int limit) {
        return listRecentByUserId(userId, 30, limit);
    }

    /**
     * 查询用户最近的行为记录（可指定天数）
     */
    List<UserBehavior> listRecentByUserId(String userId, int days, int limit);

    /**
     * 查询用户的特定行为类型记录（默认最近30天）
     */
    default List<UserBehavior> listByUserIdAndType(String userId, String behaviorType, int limit) {
        return listByUserIdAndType(userId, behaviorType, 30, limit);
    }

    /**
     * 查询用户的特定行为类型记录（可指定天数）
     */
    List<UserBehavior> listByUserIdAndType(String userId, String behaviorType, int days, int limit);

    /**
     * 记录用户行为
     */
    boolean recordBehavior(String userId, String productId, String behaviorType, String searchKeyword, String referrer);
}
