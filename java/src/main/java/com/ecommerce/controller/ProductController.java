package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.common.enums.ErrorCode;
import com.ecommerce.dto.ProductCreateDTO;
import com.ecommerce.entity.Product;
import com.ecommerce.service.ProductService;
import com.ecommerce.vo.ProductCreateVO;
import com.ecommerce.vo.ProductVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

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
    public Result<ProductVO> getProduct(@PathVariable String productId,
                                         @RequestParam(required = false) String userId) {
        log.info("ProductController.getProduct, productId: {}", productId);
        ProductVO vo = productService.getProductVOByProductId(productId, userId);
        if (vo == null) {
            return Result.notFound("商品不存在");
        }
        return Result.success(vo);
    }

    /**
     * 批量查询商品
     */
    @PostMapping("/batch")
    public Result<List<ProductVO>> getProductsBatch(@RequestBody List<String> productIds) {
        log.info("ProductController.getProductsBatch, 数量: {}", productIds.size());
        List<Product> products = productService.listByProductIds(productIds);
        // 转换为VO列表
        List<ProductVO> productVOs = products.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Result.success(productVOs);
    }

    /**
     * 搜索商品
     */
    @GetMapping("/search")
    public Result<List<ProductVO>> searchProducts(@RequestParam String keyword,
                                                  @RequestParam(defaultValue = "10") int limit,
                                                  @RequestParam(required = false) String userId) {
        log.info("ProductController.searchProducts, 关键词: {}, 数量: {}", keyword, limit);
        return Result.success(productService.searchProductVOs(keyword, limit, userId));
    }

    /**
     * 获取热门商品
     */
    @GetMapping("/hot")
    public Result<List<ProductVO>> getHotProducts(@RequestParam(defaultValue = "10") int limit,
                                                   @RequestParam(required = false) String userId) {
        log.info("ProductController.getHotProducts, 数量: {}", limit);
        return Result.success(productService.listHotProductVOs(limit, userId));
    }

    /**
     * 获取新品
     */
    @GetMapping("/new")
    public Result<List<ProductVO>> getNewArrivals(@RequestParam(defaultValue = "10") int limit,
                                                   @RequestParam(required = false) String userId) {
        log.info("ProductController.getNewArrivals, 数量: {}", limit);
        return Result.success(productService.listNewArrivalVOs(limit, userId));
    }

    /**
     * 按类目查询商品
     */
    @GetMapping("/category/{categoryId}")
    public Result<List<ProductVO>> getByCategory(@PathVariable String categoryId,
                                                  @RequestParam(defaultValue = "10") int limit,
                                                  @RequestParam(required = false) String userId) {
        log.info("ProductController.getByCategory, 类目: {}, 数量: {}", categoryId, limit);
        return Result.success(productService.listByCategoryVOs(categoryId, limit, userId));
    }

    /**
     * 创建商品
     */
    @PostMapping
    public Result<ProductCreateVO> createProduct(@RequestBody @Valid ProductCreateDTO dto) {
        log.info("ProductController.createProduct, 商品名称: {}", dto.getProductName());
        // 转换为实体
        Product product = Product.builder()
                .productId("P" + System.currentTimeMillis())
                .productName(dto.getProductName())
                .productDescription(dto.getProductDescription())
                .price(dto.getPrice())
                .originalPrice(dto.getOriginalPrice())
                .stock(dto.getStock())
                .salesCount(dto.getSalesCount())
                .rating(dto.getRating())
                .brand(dto.getBrand())
                .categoryId(dto.getCategoryId())
                .categoryName(dto.getCategoryName())
                .mainImage(dto.getMainImage())
                .images(dto.getImages() != null ? String.join(",", dto.getImages()) : null)
                .productStatus(dto.getProductStatus())
                .build();

        boolean success = productService.save(product);
        log.info("ProductController.createProduct, 结果: {}, productId: {}", success, product.getProductId());
        if (success) {
            return Result.success(ProductCreateVO.builder()
                    .productId(product.getProductId())
                    .build());
        }
        return Result.error(ErrorCode.PRODUCT_ERROR, "创建商品失败");
    }

    /**
     * 转换Product为ProductVO
     */
    private ProductVO convertToVO(Product product) {
        return ProductVO.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .productDescription(product.getProductDescription())
                .price(product.getPrice())
                .originalPrice(product.getOriginalPrice())
                .stock(product.getStock() != null ? product.getStock() : 0)
                .salesCount(product.getSalesCount() != null ? product.getSalesCount() : 0)
                .rating(product.getRating() != null ? product.getRating() : java.math.BigDecimal.ZERO)
                .brand(product.getBrand())
                .categoryId(product.getCategoryId())
                .categoryName(product.getCategoryName())
                .mainImage(product.getMainImage())
                .images(product.getImages() != null ? List.of(product.getImages().split(",")) : null)
                .productStatus(product.getProductStatus() != null ? product.getProductStatus() : 0)
                .build();
    }
}