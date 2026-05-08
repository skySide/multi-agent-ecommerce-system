package com.ecommerce.tool;

import com.ecommerce.vo.MarketingCopyVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 敏感词过滤工具
 * 用于 AI Agent 对营销文案进行广告法合规校验
 */
@Component
public class SensitiveWordTool {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordTool.class);

    /** 违禁词列表 */
    private static final List<String> FORBIDDEN_WORDS = List.of(
            "最好", "第一", "国家级", "全球首", "绝对", "100%", "永久", "万能"
    );

    /**
     * 对营销文案进行敏感词过滤，替换违禁词为***
     *
     * @param copies 营销文案列表的JSON字符串，格式：[{"productId":"xxx","copy":"文案内容"}]
     * @return 过滤后的营销文案JSON字符串
     */
    @Tool(name = "filterSensitiveWords",
            description = "对营销文案进行广告法合规过滤，替换违禁词(最好、第一、国家级、绝对等)为***")
    public List<MarketingCopyVO> filterSensitiveWords(
            @ToolParam(description = "营销文案列表，包含productId和copy字段。格式：[{\"productId\":\"P001\",\"copy\":\"文案内容\"}]") List<MarketingCopyVO> copies) {
        // 步骤1: 参数校验
        if (copies == null || copies.isEmpty()) {
            log.warn("SensitiveWordTool.filterSensitiveWords - 文案列表为空");
            return copies;
        }

        log.info("SensitiveWordTool.filterSensitiveWords - 开始过滤敏感词, 共{}条文案", copies.size());

        // 步骤2: 逐条过滤
        List<MarketingCopyVO> filtered = new ArrayList<>();
        for (int i = 0; i < copies.size(); i++) {
            MarketingCopyVO copy = copies.get(i);
            String text = copy.getCopy();
            for (String word : FORBIDDEN_WORDS) {
                text = text.replace(word, "***");
            }
            filtered.add(MarketingCopyVO.builder()
                    .productId(copy.getProductId())
                    .copy(text)
                    .build());
        }

        log.info("SensitiveWordTool.filterSensitiveWords - 过滤完成, 共{}条文案", filtered.size());
        return filtered;
    }
}
