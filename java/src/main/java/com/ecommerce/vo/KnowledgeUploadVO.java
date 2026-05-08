package com.ecommerce.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 知识库上传结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeUploadVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 文件ID */
    private String fileId;
    
    /** 原始文件名 */
    private String filename;
    
    /** 文档类型 */
    private String docType;
    
    /** 内容长度 */
    private Integer contentLength;
    
    /** 数据来源标识 */
    private String source;
    
    /** 成功数量（批量上传时使用） */
    private Integer success;
    
    /** 失败数量（批量上传时使用） */
    private Integer fail;
    
    /** 总数（批量上传时使用） */
    private Integer total;
    
    /** 错误信息（批量上传时使用） */
    private String errors;
}
