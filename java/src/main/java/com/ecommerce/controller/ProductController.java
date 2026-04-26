package com.ecommerce.controller;

import com.ecommerce.entity.Product;
import com.ecommerce.service.ProductService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 商品 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    @Resource
    private ProductService productService;

    /**
     * 获取商品详情
     */
    @GetMapping("/{productId}")
    public Product getProduct(@PathVariable String productId) {
        log.info("ProductController.getProduct productId={}", productId);
        return productService.getByProductId(productId);
    }

    /**
     * 批量查询商品
     */
    @PostMapping("/batch")
    public List<Product> getProductsBatch(@RequestBody List<String> productIds) {
        log.info("ProductController.getProductsBatch 数量={}", productIds.size());
        return productService.listByProductIds(productIds);
    }

    /**
     * 搜索商品
     */
    @GetMapping("/search")
    public List<Product> searchProducts(@RequestParam String keyword,
                                         @RequestParam(defaultValue = "10") int limit) {
        log.info("ProductController.searchProducts 关键词={} 数量={}", keyword, limit);
        return productService.searchByKeyword(keyword, limit);
    }

    /**
     * 获取热门商品
     */
    @GetMapping("/hot")
    public List<Product> getHotProducts(@RequestParam(defaultValue = "10") int limit) {
        log.info("ProductController.getHotProducts 数量={}", limit);
        return productService.listHotProducts(limit);
    }

    /**
     * 获取新品
     */
    @GetMapping("/new")
    public List<Product> getNewArrivals(@RequestParam(defaultValue = "10") int limit) {
        log.info("ProductController.getNewArrivals 数量={}", limit);
        return productService.listNewArrivals(limit);
    }

    /**
     * 按类目查询商品
     */
    @GetMapping("/category/{categoryId}")
    public List<Product> getByCategory(@PathVariable String categoryId,
                                        @RequestParam(defaultValue = "10") int limit) {
        log.info("ProductController.getByCategory 类目={} 数量={}", categoryId, limit);
        return productService.listByCategoryId(categoryId, 1, 0);
    }

    /**
     * 创建商品
     */
    @PostMapping
    public Map<String, Object> createProduct(@RequestBody Product product) {
        boolean success = productService.save(product);
        log.info("ProductController.createProduct 结果={} productId={}", success, product.getProductId());
        return Map.of("success", success, "productId", product.getProductId());
    }
}
