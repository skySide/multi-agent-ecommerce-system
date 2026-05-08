package com.ecommerce.model.response;

import com.ecommerce.entity.Product;
import lombok.Data;

import java.util.List;

/**
 * 商品推荐结果
 * 工具返回List<Product>，LLM直接映射到此结构
 */
@Data
public class RecResult {

    /** 推荐的商品列表 */
    private List<Product> products;
}
