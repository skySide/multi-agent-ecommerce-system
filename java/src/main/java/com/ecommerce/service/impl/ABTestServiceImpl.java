package com.ecommerce.service.impl;

import com.ecommerce.service.ABTestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * A/B测试服务实现类 — 流量分桶 + Thompson Sampling
 */
@Slf4j
@Service
public class ABTestServiceImpl implements ABTestService {

    private static final int BUCKET_COUNT = 100;

    @Override
    public Map<String, Object> assign(String userId) {
        return assign(userId, "rec_strategy");
    }

    @Override
    public Map<String, Object> assign(String userId, String experimentId) {
        int bucket = hashBucket(userId, experimentId);
        String group = bucket < 50 ? "control" : "treatment_llm";
        return Map.of("group", group, "bucket", bucket);
    }

    private int hashBucket(String userId, String experimentId) {
        try {
            String raw = userId + ":" + experimentId;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            int value = ((hash[0] & 0xFF) << 24) | ((hash[1] & 0xFF) << 16)
                    | ((hash[2] & 0xFF) << 8) | (hash[3] & 0xFF);
            return Math.abs(value) % BUCKET_COUNT;
        } catch (Exception e) {
            return Math.abs(userId.hashCode()) % BUCKET_COUNT;
        }
    }
}
