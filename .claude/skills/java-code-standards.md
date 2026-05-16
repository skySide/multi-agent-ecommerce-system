# Java 代码规范 Skill

## 概述
本技能定义了 Java 项目开发过程中需要遵循的代码规范，确保代码质量、可维护性和一致性。

---

## 0. if 语句规范

### 0.1 花括号强制要求
**所有 if 语句必须加花括号 `{}`，即使只有一行代码。**

```java
// 正确示例
if (user == null) {
    return null;
}

if (list.isEmpty()) {
    log.warn("ProductService.getList - 列表为空");
    return Collections.emptyList();
}

// 错误示例
if (user == null) return null;  // 缺少花括号

if (list.isEmpty())
    return Collections.emptyList();  // 缺少花括号
```

### 0.2 else 和 else if 同样要求
`else` 和 `else if` 也必须加花括号：

```java
// 正确示例
if (status == 1) {
    return "激活";
} else if (status == 0) {
    return "未激活";
} else {
    return "未知";
}

// 错误示例
if (status == 1)
    return "激活";
else
    return "未激活";
```

### 0.3 原因
- 避免"悬空 else"问题
- 防止后续添加代码时忘记加花括号导致逻辑错误
- 提高代码可读性和一致性
- 符合主流 Java 编码规范（阿里巴巴、Google 等）

---

## 1. 日志规范

### 1.1 日志格式要求
打印日志时必须包含以下信息：
- **类名.方法名** - 定位日志来源
- **日志信息** - 描述当前操作
- **参数** - 相关参数值

```java
// 正确示例
log.info("UserService.login - 用户登录请求, username: {}, password: ***", username);

// 错误示例
log.info("用户登录");  // 缺少类名、方法名和参数信息
```

### 1.2 异常日志处理
以下场景必须打印 error 日志并抛出自定义异常：
- 参数为空
- 查询数据库无数据
- 抛出异常时

### 1.3 Error 日志堆栈信息
打印 error 日志时，**必须**将异常对象作为最后一个参数传入，以打印完整的堆栈信息：

```java
// 正确示例 - 打印完整堆栈
log.error("ProductService.getProductById - 查询商品异常, id: {}", id, e);

// 错误示例 - 只打印消息，丢失堆栈信息
log.error("ProductService.getProductById - 查询商品异常, id: {}, error: {}", id, e.getMessage());
```

```java
// 完整示例
public Product getProductById(Long id) {
    if (Objects.isNull(id)) {
        log.error("ProductService.getProductById - 参数为空, id: null");
        throw new BusinessException(ErrorCode.PARAM_IS_NULL);
    }
    
    try {
        Product product = productMapper.selectById(id);
        if (Objects.isNull(product)) {
            log.error("ProductService.getProductById - 商品不存在, id: {}", id);
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return product;
    } catch (Exception e) {
        log.error("ProductService.getProductById - 查询商品异常, id: {}", id, e);
        throw new BusinessException(ErrorCode.DB_ERROR);
    }
}
```

---

## 2. DTO/VO/实体类规范

### 2.1 序列化要求
- 必须实现 `Serializable` 接口
- 必须定义 `serialVersionUID`

```java
// 正确示例
public class ProductDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /** 商品ID */
    private Long id;
    
    /** 商品名称 */
    private String name;
    
    /** 商品价格（单位：分） */
    private Integer price;
}
```

### 2.2 属性注释要求
- DTO、VO、数据库实体的每个属性都必须有注释说明
- 枚举类、常量类同样需要注释

```java
// 枚举类示例
public enum OrderStatus {
    /** 待支付 */
    PENDING_PAYMENT(0, "待支付"),
    
    /** 已支付 */
    PAID(1, "已支付"),
    
    /** 已发货 */
    SHIPPED(2, "已发货");
    
    private final Integer code;
    private final String desc;
}
```

---

## 3. Controller 层规范

### 3.1 职责划分
- Controller 层**不可**包含复杂业务逻辑
- 业务逻辑必须交给对应的 Service 层处理

### 3.2 返回值规范
- 接口返回必须使用 `Result<XXX>` 格式
- XXX 应该是 VO 对象，而非数据库实体
- 如返回 `List<Product>`，应转换为 `Result<List<ProductVO>>`

```java
// 正确示例
@RestController
@RequestMapping("/product")
public class ProductController {
    
    @Resource
    private ProductService productService;
    
    @GetMapping("/{id}")
    public Result<ProductVO> getProduct(@PathVariable Long id) {
        ProductVO productVO = productService.getProductById(id);
        return Result.success(productVO);
    }
    
    @GetMapping("/list")
    public Result<List<ProductVO>> listProducts() {
        List<ProductVO> productVOList = productService.listProducts();
        return Result.success(productVOList);
    }
}
```

---

## 4. Bean 注入规范

### 4.1 注解选择
- **禁止**使用 `@Autowired`
- **必须**使用 `@Resource` 注解

### 4.2 多 Bean 区分
当存在多个相同类型的 Bean 时，必须使用 `name` 属性进行区分：

```java
// 正确示例
@Resource
private UserService userService;

@Resource(name = "orderServiceImpl")
private OrderService orderService;

@Resource(name = "paymentServiceImpl")
private OrderService paymentService;
```

---

## 5. 线程池规范

### 5.1 线程池定义要求
- **禁止**使用 `Executors` 创建线程池
- **必须**配置 7 大参数：
  1. `corePoolSize` - 核心线程数
  2. `maximumPoolSize` - 最大线程数
  3. `keepAliveTime` - 空闲线程存活时间
  4. `unit` - 时间单位
  5. `workQueue` - 工作队列
  6. `threadFactory` - 线程工厂（必须自定义线程名）
  7. `handler` - 拒绝策略

```java
// 正确示例
@Bean
public ThreadPoolExecutor orderProcessExecutor() {
    return new ThreadPoolExecutor(
        5,                                      // corePoolSize
        10,                                     // maximumPoolSize
        60L,                                    // keepAliveTime
        TimeUnit.SECONDS,                       // unit
        new LinkedBlockingQueue<>(100),         // workQueue
        new ThreadFactory() {                   // threadFactory
            private final AtomicInteger counter = new AtomicInteger(1);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("order-process-" + counter.getAndIncrement());
                thread.setDaemon(false);
                return thread;
            }
        },
        new ThreadPoolExecutor.CallerRunsPolicy()  // handler
    );
}
```

---

## 6. 业务逻辑日志规范

### 6.1 关键操作前后日志
在以下场景的前后必须打印日志：
- 发送 Kafka 消息
- 调用外部服务
- 执行关键业务逻辑

```java
// 正确示例
public void sendOrderMessage(Order order) {
    log.info("OrderService.sendOrderMessage - 开始发送订单消息, orderId: {}", order.getId());
    try {
        kafkaTemplate.send("order-topic", order);
        log.info("OrderService.sendOrderMessage - 订单消息发送成功, orderId: {}", order.getId());
    } catch (Exception e) {
        log.error("OrderService.sendOrderMessage - 订单消息发送失败, orderId: {}", order.getId(), e);
        throw new BusinessException(ErrorCode.KAFKA_SEND_FAILED);
    }
}
```

### 6.2 循环处理日志
遍历处理元素时，必须打印下标信息：

```java
// 正确示例
public void processOrders(List<Order> orders) {
    if (CollectionUtils.isEmpty(orders)) {
        log.warn("OrderService.processOrders - 订单列表为空");
        return;
    }
    
    for (int i = 0; i < orders.size(); i++) {
        Order order = orders.get(i);
        log.info("OrderService.processOrders - 开始处理第{}个订单, orderId: {}", i + 1, order.getId());
        try {
            processOrder(order);
            log.info("OrderService.processOrders - 第{}个订单处理成功, orderId: {}", i + 1, order.getId());
        } catch (Exception e) {
            log.error("OrderService.processOrders - 第{}个订单处理失败, orderId: {}", i + 1, order.getId(), e);
            throw new BusinessException(ErrorCode.ORDER_PROCESS_FAILED);
        }
    }
}
```

---

## 7. 异常捕获规范

### 7.1 异常捕获顺序
- 必须先捕获子类异常
- 最后捕获父类异常
- error 日志必须打印完整堆栈信息

```java
// 正确示例
try {
    // 业务逻辑
} catch (BusinessException e) {
    log.error("业务异常: {}", e.getMessage(), e);  // e 作为最后参数，打印堆栈
    throw e;
} catch (SQLException e) {
    log.error("数据库异常: {}", e.getMessage(), e);  // e 作为最后参数，打印堆栈
    throw new BusinessException(ErrorCode.DB_ERROR);
} catch (Exception e) {
    log.error("未知异常: {}", e.getMessage(), e);  // e 作为最后参数，打印堆栈
    throw new BusinessException(ErrorCode.SYSTEM_ERROR);
}
```

---

## 8. 事务规范

### 8.1 事务失效场景注意
需要避免以下事务失效场景：

| 场景 | 说明 | 解决方案 |
|------|------|----------|
| 方法非 public | 只对 public 方法生效 | 确保方法为 public |
| 同类调用 | 内部调用不走代理 | 注入自身或使用 AopContext |
| 异常被捕获 | 异常未抛出 | 捕获后重新抛出或手动回滚 |
| 错误异常类型 | 默认只回滚 RuntimeException | 指定 rollbackFor = Exception.class |

```java
// 正确示例
@Transactional(rollbackFor = Exception.class)
public void createOrder(OrderDTO orderDTO) {
    // 业务逻辑
}

// 同类调用解决方案
@Service
public class OrderService {
    
    @Resource
    private OrderService self;  // 注入自身
    
    public void processOrder(OrderDTO orderDTO) {
        self.createOrder(orderDTO);  // 通过代理调用
    }
    
    @Transactional(rollbackFor = Exception.class)
    public void createOrder(OrderDTO orderDTO) {
        // 业务逻辑
    }
}
```

---

## 9. 对象转换规范

### 9.1 使用 BeanUtils
- 使用 `org.springframework.beans.BeanUtils.copyProperties` 进行对象转换
- 注意源对象和目标对象的属性类型必须一致

```java
// 正确示例
public ProductVO convertToVO(Product product) {
    if (Objects.isNull(product)) {
        log.error("ProductService.convertToVO - 商品对象为空");
        throw new BusinessException(ErrorCode.PRODUCT_IS_NULL);
    }
    
    ProductVO productVO = new ProductVO();
    BeanUtils.copyProperties(product, productVO);
    return productVO;
}

// 批量转换
public List<ProductVO> convertToVOList(List<Product> products) {
    if (CollectionUtils.isEmpty(products)) {
        return Collections.emptyList();
    }
    
    return products.stream()
        .map(product -> {
            ProductVO vo = new ProductVO();
            BeanUtils.copyProperties(product, vo);
            return vo;
        })
        .collect(Collectors.toList());
}
```

### 9.2 类型不一致处理
当属性类型不一致时，需要手动处理：

```java
// 类型不一致示例
public OrderVO convertToVO(Order order) {
    OrderVO vo = new OrderVO();
    BeanUtils.copyProperties(order, vo);
    
    // 手动处理类型不一致的字段
    vo.setCreateTimeStr(DateUtil.format(order.getCreateTime()));
    vo.setAmount(order.getAmount() / 100.0);  // 分转元
    
    return vo;
}
```

---

## 10. 集合判空规范

### 10.1 使用 CollectionUtils
判断列表是否为空必须使用 `org.springframework.util.CollectionUtils`：

```java
// 正确示例
import org.springframework.util.CollectionUtils;

public void processProducts(List<Product> products) {
    if (CollectionUtils.isEmpty(products)) {
        log.info("ProductService.processProducts - 商品列表为空");
        return;
    }
    
    // 处理逻辑
}

// 判断非空
if (!CollectionUtils.isEmpty(products)) {
    // 处理逻辑
}
```

---

## 11. 空值判断规范

### 11.1 字符串判空
使用 `org.apache.commons.lang3.StringUtils.isBlank`：

```java
// 正确示例
import org.apache.commons.lang3.StringUtils;

public void processName(String name) {
    if (StringUtils.isBlank(name)) {
        log.error("ProductService.processName - 商品名称为空");
        throw new BusinessException(ErrorCode.NAME_IS_NULL);
    }
    
    // 处理逻辑
}
```

### 11.2 对象判空
使用 `java.util.Objects.isNull`：

```java
// 正确示例
import java.util.Objects;

public void processProduct(Product product) {
    if (Objects.isNull(product)) {
        log.error("ProductService.processProduct - 商品对象为空");
        throw new BusinessException(ErrorCode.PRODUCT_IS_NULL);
    }
    
    // 处理逻辑
}

// 判断非空
if (Objects.nonNull(product)) {
    // 处理逻辑
}
```

---

## 快速检查清单

在代码审查时，请检查以下项目：

- [ ] 日志是否包含类名.方法名和参数信息
- [ ] 异常场景是否打印 error 日志并抛出自定义异常
- [ ] error 日志是否打印完整堆栈信息（异常对象作为最后参数）
- [ ] DTO/VO/实体是否实现 Serializable 并定义 serialVersionUID
- [ ] 所有属性是否有注释说明
- [ ] Controller 是否只做参数校验和结果封装
- [ ] 接口返回是否使用 Result<VO> 格式
- [ ] Bean 注入是否使用 @Resource 而非 @Autowired
- [ ] 线程池是否配置了完整的 7 大参数
- [ ] 关键操作前后是否打印日志
- [ ] 循环处理是否打印下标信息
- [ ] 异常捕获是否遵循子类到父类的顺序
- [ ] 事务使用是否避免了失效场景
- [ ] 对象转换是否使用 BeanUtils.copyProperties
- [ ] 集合判空是否使用 CollectionUtils
- [ ] 字符串判空是否使用 StringUtils.isBlank
- [ ] 对象判空是否使用 Objects.isNull


---

## 12. Controller 参数规范

### 12.1 POST 请求使用请求体
POST 请求的参数必须放在请求体中，使用 `@RequestBody` 接收，**禁止**使用 `@RequestParam`：

```java
// 正确示例
@PostMapping("/feedback")
public Result<Map<String, Object>> submitFeedback(@RequestBody @Valid FeedbackRequestDTO dto) {
    // 业务逻辑
}

// 错误示例
@PostMapping("/feedback")
public Result<Map<String, Object>> submitFeedback(
        @RequestParam("userId") String userId,
        @RequestParam("sessionId") String sessionId) {
    // 业务逻辑
}
```

### 12.2 参数超过5个时定义DTO
当接口参数超过5个时，**必须**定义专门的 DTO 类：

```java
// 正确示例 - 使用DTO
@Data
public class FeedbackRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String userId;
    private String sessionId;
    private Integer messageIndex;
    private String userMessage;
    private String aiMessage;
    private Integer rating;
}

@PostMapping("/feedback")
public Result<Map<String, Object>> submitFeedback(@RequestBody @Valid FeedbackRequestDTO dto) {
    // 业务逻辑
}

// 错误示例 - 参数过多
@PostMapping("/feedback")
public Result<Map<String, Object>> submitFeedback(
        @RequestBody String userId,
        @RequestBody String sessionId,
        @RequestBody Integer messageIndex,
        @RequestBody String userMessage,
        @RequestBody String aiMessage,
        @RequestBody Integer rating) {
    // 业务逻辑
}
```

### 12.3 DTO 规范要求
- DTO 必须实现 `Serializable` 接口
- DTO 必须定义 `serialVersionUID`
- 所有属性必须添加注释说明

---

## 13. 字符串判空规范（补充）

### 13.1 禁止直接使用 == null || isEmpty()
判断字符串是否为空，**必须**使用 `StringUtils.isBlank()` 或 `StringUtils.isBlank()`，禁止直接拼接判断：

```java
// 正确示例
import org.apache.commons.lang3.StringUtils;

public void processName(String name) {
    if (StringUtils.isBlank(name)) {
        log.error("ProductService.processName - 商品名称为空");
        throw new BusinessException(ErrorCode.NAME_IS_NULL);
    }
    
    // 处理逻辑
}

// 错误示例
public void processName(String name) {
    if (name == null || name.isEmpty()) {  // 禁止使用
        throw new BusinessException(ErrorCode.NAME_IS_NULL);
    }
    
    // 错误示例
    if (name == null || name.length() == 0) {  // 禁止使用
        throw new BusinessException(ErrorCode.NAME_IS_NULL);
    }
}
```

### 13.2 StringUtils.isBlank() vs StringUtils.isEmpty()
- `StringUtils.isBlank()` - 判断是否为 null、空字符串、或全为空白字符（推荐使用）
- `StringUtils.isEmpty()` - 仅判断是否为 null 或空字符串

```java
StringUtils.isBlank(null)      = true
StringUtils.isBlank("")        = true
StringUtils.isBlank(" ")       = true   // 空白字符也返回 true
StringUtils.isBlank("bob")     = false
StringUtils.isBlank("  bob  ") = false

StringUtils.isEmpty(null)      = true
StringUtils.isEmpty("")        = true
StringUtils.isEmpty(" ")       = false  // 空白字符返回 false
StringUtils.isEmpty("bob")     = false
```

---

## 14. AI 框架使用规范

### 14.1 框架选择
- **优先使用 Spring AI 框架**进行 LLM 集成
- 可选 LangChain4j 作为替代方案
- 禁止直接使用 HTTP 客户端调用 LLM API，必须通过框架封装

### 14.2 Tool 定义规范
当 AI Agent 需要调用外部工具时，**必须**遵循以下规范：

1. **Tool 必须定义在独立的类中**，不能定义在 Agent 类内部
2. Tool 类应该放在 `tool` 包下，与 `agent` 包平级
3. Tool 通过依赖注入调用 Service 层获取数据
4. Agent 通过 Spring AI 的 `@Tool` 注解或 Function Calling 机制使用 Tool

```java
// 正确示例 - Tool 定义在独立类中
package com.ecommerce.tool;

/**
 * 商品检索工具
 * 用于 AI Agent 检索商品信息
 */
@Component
public class ProductSearchTool {
    
    @Resource
    private ProductService productService;
    
    /**
     * 根据关键词搜索商品
     * @param keyword 搜索关键词
     * @return 商品列表
     */
    @Tool("根据关键词搜索商品")
    public List<ProductVO> searchProducts(String keyword) {
        // 步骤1: 参数校验
        if (StringUtils.isBlank(keyword)) {
            return Collections.emptyList();
        }
        // 步骤2: 调用服务层检索
        return productService.searchByKeyword(keyword);
    }
}

// Agent 使用 Tool - 通过构造器注入
@Service
public class ProductRecAgent extends BaseAgent {
    
    private final ProductSearchTool productSearchTool;
    
    public ProductRecAgent(ProductSearchTool productSearchTool) {
        this.productSearchTool = productSearchTool;
        // 步骤1: 注册 Tool 到 Agent
        // Spring AI 会自动处理 Tool 的调用
    }
}
```

```java
// 错误示例 - Tool 定义在 Agent 内部
@Service
public class ProductRecAgent extends BaseAgent {
    
    // 错误：Tool 方法定义在 Agent 内部
    @Tool("搜索商品")
    public List<ProductVO> searchProducts(String keyword) {
        // Agent 本身不具备这个能力，需要调用 Service
        // 这样会导致职责混乱
        return productService.searchByKeyword(keyword);
    }
}
```

### 14.3 Agent 与 Tool 的职责划分

| 组件 | 职责 | 依赖 |
|------|------|------|
| Agent | 协调、编排、决策 | Tool、其他 Agent |
| Tool | 执行具体操作、调用 Service | Service 层 |
| Service | 业务逻辑、数据访问 | Mapper、外部服务 |

### 14.4 Spring AI Tool 注册方式

```java
/**
 * 商品推荐 Agent
 * 负责协调多个 Tool 完成商品推荐
 */
@Service
public class ProductRecAgent extends BaseAgent {
    
    @Resource
    private ProductSearchTool productSearchTool;
    
    @Resource
    private UserProfileTool userProfileTool;
    
    @Override
    public Map<String, Object> execute(Map<String, Object> params) {
        // 步骤1: 提取用户ID
        String userId = (String) params.get("userId");
        
        // 步骤2: 构建提示词
        String prompt = buildPrompt(params);
        
        // 步骤3: 调用 LLM，Tool 会自动被触发
        ChatResponse response = chatClient.call(prompt);
        
        // 步骤4: 解析结果
        return parseResponse(response);
    }
}
```

---

## 15. 注释与日志保留规范

### 15.1 已存在注释和日志不可删除
**已存在的日志信息和注释必须保留，不可删除或修改**。只允许：
- 添加新的注释
- 添加新的日志
- 修正注释或日志中的错误信息

```java
// 正确示例 - 保留原有注释，添加新注释
public class ProductServiceImpl implements ProductService {
    
    /**
     * 商品服务实现类
     * 负责商品相关的业务逻辑处理
     * 
     * @author team
     * @since 1.0.0
     */
    
    /**
     * 根据ID获取商品详情
     * 新增：支持缓存加速
     */
    public ProductVO getProductById(Long id) {
        // 步骤1: 参数校验（原有日志保留）
        if (Objects.isNull(id)) {
            log.error("ProductService.getProductById - 参数为空, id: null");
            throw new BusinessException(ErrorCode.PARAM_IS_NULL);
        }
        
        // 步骤2: 尝试从缓存获取（新增步骤）
        ProductVO cached = redisTemplate.opsForValue().get("product:" + id);
        if (Objects.nonNull(cached)) {
            // 步骤3: 缓存命中，直接返回
            return cached;
        }
        
        // 步骤4: 查询数据库（原有逻辑）
        Product product = productMapper.selectById(id);
        // ...
    }
}
```

### 15.2 方法内每个步骤必须添加行注释
在方法内部，每个逻辑步骤**必须**添加行注释说明：
- 使用 `// 步骤N: xxx` 格式
- 注释必须简洁明了，说明该步骤的作用

```java
/**
 * 处理订单支付流程
 */
public void processOrderPayment(OrderPaymentDTO dto) {
    // 步骤1: 参数校验
    if (Objects.isNull(dto) || Objects.isNull(dto.getOrderId())) {
        log.error("OrderService.processOrderPayment - 参数为空");
        throw new BusinessException(ErrorCode.PARAM_IS_NULL);
    }
    
    // 步骤2: 查询订单信息
    Order order = orderMapper.selectById(dto.getOrderId());
    if (Objects.isNull(order)) {
        log.error("OrderService.processOrderPayment - 订单不存在, orderId: {}", dto.getOrderId());
        throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
    }
    
    // 步骤3: 校验订单状态
    if (order.getStatus() != OrderStatus.PENDING_PAYMENT.getCode()) {
        log.error("OrderService.processOrderPayment - 订单状态异常, orderId: {}, status: {}", 
                  order.getId(), order.getStatus());
        throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR);
    }
    
    // 步骤4: 调用支付服务
    PaymentResult result = paymentService.pay(order);
    
    // 步骤5: 更新订单状态
    order.setStatus(OrderStatus.PAID.getCode());
    orderMapper.updateById(order);
    
    // 步骤6: 发送订单消息
    kafkaTemplate.send("order-topic", order);
    
    // 步骤7: 记录操作日志
    log.info("OrderService.processOrderPayment - 订单支付成功, orderId: {}", order.getId());
}
```

### 15.3 类必须有文档注释
每个类**必须**有 JavaDoc 文档注释，说明：
- 类的作用和职责
- 重要说明（如有）
- 作者和版本（可选）

```java
/**
 * 商品推荐服务实现类
 * 负责商品推荐的核心业务逻辑，包括召回、排序、重排等环节
 * 
 * <p>支持多种推荐策略：
 * <ul>
 *   <li>协同过滤</li>
 *   <li>向量相似度</li>
 *   <li>热门推荐</li>
 * </ul>
 *
 * @author ecommerce-team
 * @since 1.0.0
 */
@Service
public class RecommendServiceImpl implements RecommendService {
    // ...
}

/**
 * 用户画像工具类
 * 用于 AI Agent 获取用户画像信息
 */
@Component
public class UserProfileTool {
    // ...
}

/**
 * 订单状态枚举
 * 定义订单的所有可能状态
 */
public enum OrderStatus {
    // ...
}
```

---

## 16. 常量使用规范（禁止硬编码）

### 16.1 禁止硬编码
**所有硬编码的字符串、数字必须定义为常量**，通过常量类引用。

```java
// 错误示例 - 硬编码
public void processOrder(Order order) {
    if (order.getStatus() == 1) {  // 硬编码：1 表示什么？
        // ...
    }
    
    redisTemplate.opsForValue().set("order:" + order.getId(), order, 3600, TimeUnit.SECONDS);  // 硬编码：3600秒
}

// 正确示例 - 使用常量
public void processOrder(Order order) {
    if (order.getStatus() == OrderStatus.PAID.getCode()) {  // 使用枚举常量
        // ...
    }
    
    redisTemplate.opsForValue().set(
        RedisKeyConstants.ORDER_KEY_PREFIX + order.getId(), 
        order, 
        RedisKeyConstants.ORDER_EXPIRE_SECONDS, 
        TimeUnit.SECONDS
    );
}
```

### 16.2 常量类定义规范

常量类应该按功能模块划分，放在 `common/constants` 包下：

```java
package com.ecommerce.common.constants;

/**
 * Redis Key 常量类
 * 定义所有 Redis 相关的 Key 前缀和过期时间
 */
public class RedisKeyConstants {
    
    /** 用户信息 Key 前缀 */
    public static final String USER_KEY_PREFIX = "user:";
    
    /** 商品信息 Key 前缀 */
    public static final String PRODUCT_KEY_PREFIX = "product:";
    
    /** 订单信息 Key 前缀 */
    public static final String ORDER_KEY_PREFIX = "order:";
    
    /** 购物车 Key 前缀 */
    public static final String CART_KEY_PREFIX = "cart:";
    
    /** 用户缓存过期时间（秒） */
    public static final long USER_EXPIRE_SECONDS = 7200L;
    
    /** 商品缓存过期时间（秒） */
    public static final long PRODUCT_EXPIRE_SECONDS = 3600L;
    
    /** 订单缓存过期时间（秒） */
    public static final long ORDER_EXPIRE_SECONDS = 86400L;
    
    private RedisKeyConstants() {
        // 工具类，禁止实例化
    }
}
```

```java
package com.ecommerce.common.constants;

/**
 * LLM 相关常量
 * 定义 LLM 调用相关的配置常量
 */
public class LLMConstants {
    
    /** 默认温度参数 */
    public static final double DEFAULT_TEMPERATURE = 0.7;
    
    /** 最大 Token 数 */
    public static final int MAX_TOKENS = 4096;
    
    /** 请求超时时间（毫秒） */
    public static final long REQUEST_TIMEOUT_MS = 30000L;
    
    /** 重试次数 */
    public static final int MAX_RETRY_TIMES = 3;
    
    /** 重试间隔（毫秒） */
    public static final long RETRY_INTERVAL_MS = 1000L;
    
    private LLMConstants() {
        // 工具类，禁止实例化
    }
}
```

```java
package com.ecommerce.common.constants;

/**
 * 消息队列 Topic 常量
 * 定义所有 Kafka Topic 名称
 */
public class KafkaTopicConstants {
    
    /** 订单消息 Topic */
    public static final String ORDER_TOPIC = "order-topic";
    
    /** 用户行为消息 Topic */
    public static final String USER_BEHAVIOR_TOPIC = "user-behavior-topic";
    
    /** 商品更新消息 Topic */
    public static final String PRODUCT_UPDATE_TOPIC = "product-update-topic";
    
    private KafkaTopicConstants() {
        // 工具类，禁止实例化
    }
}
```

### 16.3 枚举类作为常量

对于有限集合的状态码、类型等，**优先使用枚举类**：

```java
/**
 * 订单状态枚举
 * 定义订单的所有可能状态
 */
public enum OrderStatus {
    
    /** 待支付 */
    PENDING_PAYMENT(0, "待支付"),
    
    /** 已支付 */
    PAID(1, "已支付"),
    
    /** 已发货 */
    SHIPPED(2, "已发货"),
    
    /** 已完成 */
    COMPLETED(3, "已完成"),
    
    /** 已取消 */
    CANCELLED(4, "已取消");
    
    private final Integer code;
    private final String desc;
    
    OrderStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    
    public Integer getCode() {
        return code;
    }
    
    public String getDesc() {
        return desc;
    }
}
```

---

## 快速检查清单（更新版）

在代码审查时，请检查以下项目：

- [ ] 日志是否包含类名.方法名和参数信息
- [ ] 异常场景是否打印 error 日志并抛出自定义异常
- [ ] error 日志是否打印完整堆栈信息（异常对象作为最后参数）
- [ ] DTO/VO/实体是否实现 Serializable 并定义 serialVersionUID
- [ ] 所有属性是否有注释说明
- [ ] Controller 是否只做参数校验和结果封装
- [ ] 接口返回是否使用 Result<VO> 格式
- [ ] Bean 注入是否使用 @Resource 而非 @Autowired
- [ ] 线程池是否配置了完整的 7 大参数
- [ ] 关键操作前后是否打印日志
- [ ] 循环处理是否打印下标信息
- [ ] 异常捕获是否遵循子类到父类的顺序
- [ ] 事务使用是否避免了失效场景
- [ ] 对象转换是否使用 BeanUtils.copyProperties
- [ ] 集合判空是否使用 CollectionUtils
- [ ] 字符串判空是否使用 StringUtils.isBlank
- [ ] 对象判空是否使用 Objects.isNull
- [ ] **AI Tool 是否定义在独立类中，而非 Agent 内部**
- [ ] **已存在的日志和注释是否保留未删除**
- [ ] **方法内每个步骤是否有行注释说明**
- [ ] **类是否有 JavaDoc 文档注释**
- [ ] **是否使用常量类或枚举替代硬编码**
