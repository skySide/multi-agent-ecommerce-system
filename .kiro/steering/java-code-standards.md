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
