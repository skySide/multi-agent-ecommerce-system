package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.entity.ConversationProfileUpdate;

import java.util.List;

/**
 * 对话画像更新记录服务接口
 */
public interface ConversationProfileUpdateService extends IService<ConversationProfileUpdate> {

    /**
     * 查询用户的画像更新记录
     */
    List<ConversationProfileUpdate> listByUserId(String userId);

    /**
     * 查询会话的画像更新记录
     */
    List<ConversationProfileUpdate> listBySessionId(String sessionId);

    /**
     * 记录画像更新
     */
    boolean recordUpdate(String userId, String sessionId, String updateType, String oldValue, String newValue, double confidence);
}
