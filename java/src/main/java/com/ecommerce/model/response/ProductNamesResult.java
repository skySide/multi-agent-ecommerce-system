package com.ecommerce.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 商品名称提取结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductNamesResult {

    /** 从用户消息中提取的商品名称列表 */
    private List<String> products;
}
