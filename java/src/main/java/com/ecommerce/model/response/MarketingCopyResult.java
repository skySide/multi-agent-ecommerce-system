package com.ecommerce.model.response;

import com.ecommerce.vo.MarketingCopyVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 营销文案生成结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketingCopyResult {

    /** 生成的营销文案列表 */
    private List<MarketingCopyVO> copies;
}
