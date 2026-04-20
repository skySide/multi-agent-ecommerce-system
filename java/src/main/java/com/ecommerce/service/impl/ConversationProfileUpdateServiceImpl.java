package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.entity.ConversationProfileUpdate;
import com.ecommerce.mapper.ConversationProfileUpdateMapper;
import com.ecommerce.service.ConversationProfileUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 对话画像更新记录服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationProfileUpdateServiceImpl extends ServiceImpl<ConversationProfileUpdateMapper, ConversationProfileUpdate> implements ConversationProfileUpdateService {

    private final ConversationProfileUpdateMapper conversationProfileUpdateMapper;

    @Override
    public List<ConversationProfileUpdate> listByUserId(String userId) {
        return conversationProfileUpdateMapper.findByUserId(userId);
    }

    @Override
    public List<ConversationProfileUpdate> listBySessionId(String sessionId) {
        return conversationProfileUpdateMapper.findBySessionId(sessionId);
    }

    @Override
    public boolean recordUpdate(String userId, String sessionId, String updateType, String oldValue, String newValue, double confidence) {
        ConversationProfileUpdate update = ConversationProfileUpdate.builder()
                .userId(userId)
                .sessionId(sessionId)
                .updateType(updateType)
                .oldValue(oldValue)
                .newValue(newValue)
                .confidence(BigDecimal.valueOf(confidence))
                .build();
        return save(update);
    }
}
