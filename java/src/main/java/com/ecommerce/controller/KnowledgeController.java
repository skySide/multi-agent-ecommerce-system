package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.service.DocumentVectorService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库管理控制器
 * 支持上传文件更新知识库（如退换货政策、FAQ等）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    @Resource
    private DocumentVectorService documentVectorService;

    @Value("${app.knowledge.upload-path:./knowledge}")
    private String uploadPath;

    /**
     * 上传文件到知识库
     *
     * @param file    上传的文件（支持 txt, md, json 格式）
     * @param docType 文档类型（如：refund_policy, faq, product_guide）
     * @return 上传结果
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadKnowledge(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "docType", defaultValue = "general") String docType) {

        log.info("KnowledgeController.uploadKnowledge 开始上传知识库文件, 文件名={}, docType={}",
                file.getOriginalFilename(), docType);

        // 1. 校验文件
        if (file.isEmpty()) {
            log.error("KnowledgeController.uploadKnowledge 文件为空");
            return Result.error(400, "文件不能为空");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".txt") && !filename.endsWith(".md")
                && !filename.endsWith(".json") && !filename.endsWith(".csv"))) {
            log.error("KnowledgeController.uploadKnowledge 文件格式不支持: {}", filename);
            return Result.error(400, "仅支持 txt, md, json, csv 格式文件");
        }

        try {
            // 2. 读取文件内容
            String content = new BufferedReader(
                    new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            if (content.isEmpty()) {
                log.error("KnowledgeController.uploadKnowledge 文件内容为空");
                return Result.error(400, "文件内容不能为空");
            }

            // 3. 保存文件到本地（备份）
            String fileId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String savedFilename = fileId + "_" + filename;
            Path saveDir = Paths.get(uploadPath);
            if (!Files.exists(saveDir)) {
                Files.createDirectories(saveDir);
            }
            Path savePath = saveDir.resolve(savedFilename);
            file.transferTo(savePath.toFile());

            log.info("KnowledgeController.uploadKnowledge 文件已保存: {}", savePath);

            // 4. 添加到向量知识库
            String source = docType + "_" + fileId;
            documentVectorService.addDocumentToKnowledgeBase(content, source, docType);

            // 5. 返回结果
            Map<String, Object> data = new HashMap<>();
            data.put("fileId", fileId);
            data.put("filename", filename);
            data.put("docType", docType);
            data.put("contentLength", content.length());
            data.put("source", source);

            log.info("KnowledgeController.uploadKnowledge 上传成功, fileId={}, contentLength={}",
                    fileId, content.length());

            return Result.success(data);

        } catch (Exception e) {
            log.error("KnowledgeController.uploadKnowledge 上传失败", e);
            return Result.error(500, "上传失败: " + e.getMessage());
        }
    }

    /**
     * 直接添加文本内容到知识库
     *
     * @param content 文本内容
     * @param docType 文档类型
     * @return 添加结果
     */
    @PostMapping("/add-text")
    public Result<Map<String, Object>> addText(
            @RequestParam("content") String content,
            @RequestParam(value = "docType", defaultValue = "general") String docType) {

        log.info("KnowledgeController.addText 开始添加文本到知识库, docType={}, contentLength={}",
                docType, content.length());

        if (content == null || content.trim().isEmpty()) {
            log.error("KnowledgeController.addText 内容为空");
            return Result.error(400, "内容不能为空");
        }

        try {
            String fileId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String source = docType + "_" + fileId;

            documentVectorService.addDocumentToKnowledgeBase(content, source, docType);

            Map<String, Object> data = new HashMap<>();
            data.put("fileId", fileId);
            data.put("docType", docType);
            data.put("contentLength", content.length());
            data.put("source", source);

            log.info("KnowledgeController.addText 添加成功, fileId={}", fileId);

            return Result.success(data);

        } catch (Exception e) {
            log.error("KnowledgeController.addText 添加失败", e);
            return Result.error(500, "添加失败: " + e.getMessage());
        }
    }

    /**
     * 搜索知识库
     *
     * @param query 查询内容
     * @param topK  返回数量
     * @return 搜索结果
     */
    @GetMapping("/search")
    public Result<List<Map<String, Object>>> searchKnowledge(
            @RequestParam("query") String query,
            @RequestParam(value = "topK", defaultValue = "5") int topK) {

        log.info("KnowledgeController.searchKnowledge 搜索知识库, query={}, topK={}", query, topK);

        if (query == null || query.trim().isEmpty()) {
            log.error("KnowledgeController.searchKnowledge 查询内容为空");
            return Result.error(400, "查询内容不能为空");
        }

        try {
            List<org.springframework.ai.document.Document> docs =
                    documentVectorService.searchKnowledgeBase(query, topK);

            List<Map<String, Object>> results = docs.stream()
                    .map(doc -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("content", doc.getText());
                        item.put("metadata", doc.getMetadata());
                        return item;
                    })
                    .collect(Collectors.toList());

            log.info("KnowledgeController.searchKnowledge 搜索完成, 结果数量={}", results.size());

            return Result.success(results);

        } catch (Exception e) {
            log.error("KnowledgeController.searchKnowledge 搜索失败", e);
            return Result.error(500, "搜索失败: " + e.getMessage());
        }
    }

    /**
     * 批量上传知识库文件
     *
     * @param files   上传的文件列表
     * @param docType 文档类型
     * @return 上传结果
     */
    @PostMapping("/batch-upload")
    public Result<Map<String, Object>> batchUpload(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "docType", defaultValue = "general") String docType) {

        log.info("KnowledgeController.batchUpload 批量上传知识库文件, 文件数量={}, docType={}",
                files.length, docType);

        int successCount = 0;
        int failCount = 0;
        StringBuilder errorMsg = new StringBuilder();

        for (MultipartFile file : files) {
            try {
                Result<Map<String, Object>> result = uploadKnowledge(file, docType);
                if (result.getCode() == 200) {
                    successCount++;
                } else {
                    failCount++;
                    errorMsg.append(file.getOriginalFilename()).append(": ")
                            .append(result.getMessage()).append("; ");
                }
            } catch (Exception e) {
                failCount++;
                errorMsg.append(file.getOriginalFilename()).append(": ")
                        .append(e.getMessage()).append("; ");
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("total", files.length);
        data.put("success", successCount);
        data.put("fail", failCount);
        if (failCount > 0) {
            data.put("errors", errorMsg.toString());
        }

        log.info("KnowledgeController.batchUpload 批量上传完成, 成功={}, 失败={}",
                successCount, failCount);

        return Result.success(data);
    }
}
