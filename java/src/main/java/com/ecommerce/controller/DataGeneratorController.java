package com.ecommerce.controller;

import com.ecommerce.bootstrap.ProductDataGenerator;
import com.ecommerce.common.Result;
import com.ecommerce.vo.DataGeneratorResultVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/**
 * 商品数据生成控制器
 * 用于触发商品数据生成（非真实爬虫，而是模拟数据生成）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/data-generator")
public class DataGeneratorController {

    @Resource
    private ProductDataGenerator productDataGenerator;

    @Value("${app.data-generator.enabled:true}")
    private boolean generatorEnabled;

    /**
     * 生成商品数据
     *
     * @param countPerCategory 每个类目生成的商品数量
     * @return 生成结果
     */
    @PostMapping("/generate-products")
    public Result<DataGeneratorResultVO> generateProducts(
            @RequestParam(value = "countPerCategory", defaultValue = "20") int countPerCategory) {

        log.info("DataGeneratorController.generateProducts 触发商品数据生成, countPerCategory={}", countPerCategory);

        if (!generatorEnabled) {
            log.warn("DataGeneratorController.generateProducts 数据生成功能未启用");
            return Result.error(403, "数据生成功能未启用，请在配置中设置 app.data-generator.enabled=true");
        }

        try {
            int totalGenerated = productDataGenerator.generateProducts(countPerCategory);

            log.info("DataGeneratorController.generateProducts 生成完成, 共{}件商品", totalGenerated);

            return Result.success(DataGeneratorResultVO.builder()
                    .count(totalGenerated)
                    .message("成功生成 " + totalGenerated + " 件商品")
                    .build());

        } catch (Exception e) {
            log.error("DataGeneratorController.generateProducts 生成失败", e);
            return Result.error(500, "生成失败: " + e.getMessage());
        }
    }

    /**
     * 更新所有商品图片
     * 根据商品类目设置对应类型的图片URL
     *
     * @return 更新结果
     */
    @PostMapping("/update-images")
    public Result<DataGeneratorResultVO> updateProductImages() {

        log.info("DataGeneratorController.updateProductImages 开始更新商品图片");

        if (!generatorEnabled) {
            log.warn("DataGeneratorController.updateProductImages 数据生成功能未启用");
            return Result.error(403, "数据生成功能未启用，请在配置中设置 app.data-generator.enabled=true");
        }

        try {
            int updatedCount = productDataGenerator.updateAllProductImages();

            log.info("DataGeneratorController.updateProductImages 更新完成, 共{}件商品", updatedCount);

            return Result.success(DataGeneratorResultVO.builder()
                    .count(updatedCount)
                    .message("成功更新 " + updatedCount + " 件商品图片")
                    .build());

        } catch (Exception e) {
            log.error("DataGeneratorController.updateProductImages 更新失败", e);
            return Result.error(500, "更新失败: " + e.getMessage());
        }
    }

    /**
     * 检查数据生成器状态
     *
     * @return 数据生成器配置状态
     */
    @GetMapping("/status")
    public Result<DataGeneratorResultVO> getStatus() {
        return Result.success(DataGeneratorResultVO.builder()
                .enabled(generatorEnabled)
                .message(generatorEnabled ? "数据生成功能已启用" : "数据生成功能未启用")
                .build());
    }
}
