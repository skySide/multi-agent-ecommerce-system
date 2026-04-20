package com.ecommerce.service.impl;

import com.ecommerce.service.MilvusService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Milvus 向量数据库服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusServiceImpl implements MilvusService {

    private final MilvusServiceClient milvusClient;

    private boolean isMilvusAvailable() {
        return milvusClient != null;
    }

    @Override
    public void initProductCollection() {
        if (!isMilvusAvailable()) {
            log.warn("Milvus 不可用，跳过商品向量 Collection 初始化");
            return;
        }
        try {
            // 定义字段
            FieldType productIdField = FieldType.newBuilder()
                    .withName("product_id")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(32)
                    .withPrimaryKey(true)
                    .withAutoID(false)
                    .build();

            FieldType embeddingField = FieldType.newBuilder()
                    .withName("embedding")
                    .withDataType(DataType.FloatVector)
                    .withDimension(VECTOR_DIM)
                    .build();

            FieldType categoryIdField = FieldType.newBuilder()
                    .withName("category_id")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(32)
                    .build();

            FieldType priceField = FieldType.newBuilder()
                    .withName("price")
                    .withDataType(DataType.Float)
                    .build();

            FieldType salesCountField = FieldType.newBuilder()
                    .withName("sales_count")
                    .withDataType(DataType.Int32)
                    .build();

            FieldType statusField = FieldType.newBuilder()
                    .withName("status")
                    .withDataType(DataType.Int8)
                    .build();

            // 创建 Collection
            CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(PRODUCT_COLLECTION)
                    .withDescription("商品向量集合")
                    .withShardsNum(2)
                    .addFieldType(productIdField)
                    .addFieldType(embeddingField)
                    .addFieldType(categoryIdField)
                    .addFieldType(priceField)
                    .addFieldType(salesCountField)
                    .addFieldType(statusField)
                    .build();

            R<RpcStatus> response = milvusClient.createCollection(createParam);
            if (response.getStatus() == R.Status.Success.getCode()) {
                log.info("创建商品向量 Collection 成功");

                // 创建向量索引
                createIndex(PRODUCT_COLLECTION, "embedding");

                // 加载 Collection
                milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                        .withCollectionName(PRODUCT_COLLECTION)
                        .build());
            } else {
                log.warn("创建商品向量 Collection 失败: {}", response.getMessage());
            }
        } catch (Exception e) {
            log.error("初始化商品向量 Collection 异常", e);
        }
    }

    @Override
    public void initUserCollection() {
        if (!isMilvusAvailable()) {
            log.warn("Milvus 不可用，跳过用户向量 Collection 初始化");
            return;
        }
        try {
            FieldType userIdField = FieldType.newBuilder()
                    .withName("user_id")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(32)
                    .withPrimaryKey(true)
                    .withAutoID(false)
                    .build();

            FieldType embeddingField = FieldType.newBuilder()
                    .withName("embedding")
                    .withDataType(DataType.FloatVector)
                    .withDimension(VECTOR_DIM)
                    .build();

            FieldType updateTimeField = FieldType.newBuilder()
                    .withName("update_time")
                    .withDataType(DataType.Int64)
                    .build();

            CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(USER_COLLECTION)
                    .withDescription("用户向量集合")
                    .withShardsNum(2)
                    .addFieldType(userIdField)
                    .addFieldType(embeddingField)
                    .addFieldType(updateTimeField)
                    .build();

            R<RpcStatus> response = milvusClient.createCollection(createParam);
            if (response.getStatus() == R.Status.Success.getCode()) {
                log.info("创建用户向量 Collection 成功");
                createIndex(USER_COLLECTION, "embedding");
                milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                        .withCollectionName(USER_COLLECTION)
                        .build());
            }
        } catch (Exception e) {
            log.error("初始化用户向量 Collection 异常", e);
        }
    }

    /**
     * 创建向量索引
     */
    private void createIndex(String collectionName, String fieldName) {
        CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName(fieldName)
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.COSINE)
                .withExtraParam("{\"nlist\":128}")
                .build();

        milvusClient.createIndex(indexParam);
    }

    @Override
    public void insertProductVectors(List<String> productIds,
                                      List<List<Float>> embeddings,
                                      List<String> categoryIds,
                                      List<Float> prices,
                                      List<Integer> salesCounts) {
        if (!isMilvusAvailable()) {
            log.warn("Milvus 不可用，跳过商品向量插入");
            return;
        }
        try {
            List<InsertParam.Field> fields = new ArrayList<>();
            fields.add(new InsertParam.Field("product_id", productIds));
            fields.add(new InsertParam.Field("embedding", embeddings));
            fields.add(new InsertParam.Field("category_id", categoryIds));
            fields.add(new InsertParam.Field("price", prices));
            fields.add(new InsertParam.Field("sales_count", salesCounts));

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(PRODUCT_COLLECTION)
                    .withFields(fields)
                    .build();

            milvusClient.insert(insertParam);
            log.info("插入 {} 条商品向量", productIds.size());
        } catch (Exception e) {
            log.error("插入商品向量失败", e);
        }
    }

    @Override
    public List<String> searchSimilarProducts(List<Float> queryVector, int topK) {
        if (!isMilvusAvailable()) {
            log.warn("Milvus 不可用，返回空结果");
            return new ArrayList<>();
        }
        try {
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(PRODUCT_COLLECTION)
                    .withVectors(Arrays.asList(queryVector))
                    .withVectorFieldName("embedding")
                    .withMetricType(MetricType.COSINE)
                    .withTopK(topK)
                    .withParams("{\"nprobe\":16}")
                    .addOutField("product_id")
                    .build();

            R<SearchResults> response = milvusClient.search(searchParam);
            SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());

            List<String> productIds = new ArrayList<>();
            for (int i = 0; i < wrapper.getRowRecords().size(); i++) {
                String productId = (String) wrapper.getFieldData("product_id", i).get(0);
                productIds.add(productId);
            }

            return productIds;
        } catch (Exception e) {
            log.error("搜索相似商品失败", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<String> searchSimilarUsers(List<Float> userVector, int topK) {
        if (!isMilvusAvailable()) {
            log.warn("Milvus 不可用，返回空结果");
            return new ArrayList<>();
        }
        try {
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(USER_COLLECTION)
                    .withVectors(Arrays.asList(userVector))
                    .withVectorFieldName("embedding")
                    .withMetricType(MetricType.COSINE)
                    .withTopK(topK)
                    .withParams("{\"nprobe\":16}")
                    .addOutField("user_id")
                    .build();

            R<SearchResults> response = milvusClient.search(searchParam);
            SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());

            List<String> userIds = new ArrayList<>();
            for (int i = 0; i < wrapper.getRowRecords().size(); i++) {
                String userId = (String) wrapper.getFieldData("user_id", i).get(0);
                userIds.add(userId);
            }

            return userIds;
        } catch (Exception e) {
            log.error("搜索相似用户失败", e);
            return new ArrayList<>();
        }
    }
}
