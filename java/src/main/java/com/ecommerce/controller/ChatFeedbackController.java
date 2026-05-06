package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.dto.FeedbackRequestDTO;
import com.ecommerce.service.ChatFeedbackService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI回复反馈控制器
 * 支持用户对AI回复点赞/点踩
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
public class ChatFeedbackController {

    @Resource
    private ChatFeedbackService chatFeedbackService;

    /**
     * 提交反馈（点赞/点踩）
     *
     * @param dto 反馈请求DTO
     * @return 操作结果
     */
    @PostMapping("/feedback")
    public Result<Map<String, Object>> submitFeedback(@RequestBody @Valid FeedbackRequestDTO dto) {

        log.info("ChatFeedbackController.submitFeedback 用户={} 会话={} 索引={} 评分={}",
                dto.getUserId(), dto.getSessionId(), dto.getMessageIndex(), dto.getRating());

        boolean success = chatFeedbackService.submitFeedback(
                dto.getUserId(), 
                dto.getSessionId(), 
                dto.getMessageIndex(),
                dto.getUserMessage(), 
                dto.getAiMessage(), 
                dto.getRating());

        if (success) {
            return Result.success(Map.of(
                    "message", dto.getRating() == 1 ? "感谢您的认可！" : "感谢您的反馈，我们会持续改进！",
                    "rating", dto.getRating()
            ));
        } else {
            return Result.error(500, "反馈提交失败");
        }
    }

    /**
     * 获取满意度统计
     *
     * @return 统计结果
     */
    @GetMapping("/feedback/stats")
    public Result<Map<String, Object>> getSatisfactionStats() {
        log.info("ChatFeedbackController.getSatisfactionStats 获取满意度统计");
        Map<String, Object> stats = chatFeedbackService.getSatisfactionStats();
        return Result.success(stats);
    }

    /**
     * 获取用户反馈历史
     *
     * @param userId 用户ID
     * @return 反馈统计
     */
    @GetMapping("/feedback/user/{userId}")
    public Result<Map<String, Object>> getUserFeedbackStats(@PathVariable String userId) {
        log.info("ChatFeedbackController.getUserFeedbackStats 用户={}", userId);
        Map<String, Object> stats = chatFeedbackService.getUserFeedbackStats(userId);
        return Result.success(stats);
    }
}
