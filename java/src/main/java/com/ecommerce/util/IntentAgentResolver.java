package com.ecommerce.util;

import com.ecommerce.agent.ReActAgent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 意图 → Agent 解析工具类
 * 维护意图类型到 ReActAgent 的映射，供 ConversationAgent 和 AgentOrchestrator 共用。
 * 保证数据源唯一，新增意图只需修改此处的 INTENT_AGENT_MAPPING。
 */
@Component
public class IntentAgentResolver {

    private static final Logger log = LoggerFactory.getLogger(IntentAgentResolver.class);

    /** 意图 → ReActAgent 名称映射 */
    private static final Map<String, String> INTENT_AGENT_MAPPING = Map.of(
            "recommend", "recommend_intent",
            "product_query", "product_query_intent",
            "knowledge_query", "knowledge_intent",
            "compare", "compare_intent",
            "chitchat", "chitchat_intent",
            "transfer_to_human", "chitchat_intent"
    );

    @Resource
    private List<ReActAgent> reactAgents;

    /** intent → ReActAgent 实例路由表 */
    private final Map<String, ReActAgent> intentRouter = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        for (ReActAgent agent : reactAgents) {
            for (Map.Entry<String, String> entry : INTENT_AGENT_MAPPING.entrySet()) {
                if (entry.getValue().equals(agent.getName())) {
                    intentRouter.put(entry.getKey(), agent);
                    break;
                }
            }
        }
        log.info("IntentAgentResolver.init - 意图路由注册完成, mappingSize: {}, routerSize: {}",
                INTENT_AGENT_MAPPING.size(), intentRouter.size());
    }

    /**
     * 根据意图名称查找对应的 ReActAgent
     *
     * @param intent 意图名称（如 "recommend"、"compare"）
     * @return 对应的 ReActAgent 实例，未找到返回 null
     */
    public ReActAgent resolve(String intent) {
        ReActAgent agent = intentRouter.get(intent);
        if (agent == null) {
            log.warn("IntentAgentResolver.resolve - 未找到对应Agent, intent: {}", intent);
        }
        return agent;
    }

    /**
     * 批量解析意图列表
     *
     * @param intents 意图名称列表
     * @return 对应的 ReActAgent 列表（顺序与输入一致，不存在的意图对应 null）
     */
    public List<ReActAgent> resolveAll(List<String> intents) {
        return intents.stream()
                .map(this::resolve)
                .toList();
    }

    /**
     * 获取完整的意图映射（用于序列化或调试）
     */
    public Map<String, String> getIntentAgentMapping() {
        return INTENT_AGENT_MAPPING;
    }

    /**
     * 获取路由表大小（用于健康检查）
     */
    public int getRouterSize() {
        return intentRouter.size();
    }
}
