# 会议室预约管理系统 - 后端 README
# 会议室预约管理系统
## 项目简介
企业内部会议室预约管理后端，前后端分离架构。项目完成分布式改造，支持多实例集群部署。
采用 **Redisson分布式锁 + MySQL行锁** 双重机制，解决多实例并发预约冲突；
基于Nacos实现服务注册与发现；RocketMQ异步发送预约相关通知，实现业务与通知解耦。

## 技术栈（版本）
| 组件 | 版本 | 备注 |
|---|---|---|
| JDK | 21 | |
| Spring Boot | 3.4.0 | parent工程 |
| Spring Cloud | 2023.0.3 | BOM依赖管理 |
| Spring Cloud Alibaba | 2023.0.3.2 | Nacos服务注册发现 |
| MyBatis-Plus | 3.5.5 | SpringBoot3专用starter |
| MySQL | 5.7+ | 业务数据库 |
| Redisson | 3.37.0 | redisson-spring-boot-starter，自动装配RedissonClient |
| RocketMQ | 2.3.1 | rocketmq-spring-boot-starter |
| Nacos | 2.x | 服务注册发现中心 |
| JJWT | 0.12.6 | JWT签名鉴权 |
| BCrypt | Spring Security 6.4.1 | 用户密码加密 |
| Hutool | 5.8.34 | JSON工具、通用工具类 |
| Vue3 + Element Plus | — | 前端项目，本后端不包含前端代码 |

## 核心功能
1. **用户权限管理** — JWT登录鉴权（`TokenInterceptor.java`）、RBAC角色控制，`UserRoleEnum`：0员工 / 1管理员
2. **会议室资源管理** — 会议室CRUD操作（`MeetingRoomController.java`）；删除会议室前置校验：存在未完结预约（待审批/已通过）时禁止删除
3. **预约完整状态流转** — 提交 → 审批(PASS/REJECT) → 取消。状态枚举`BookingStatusEnum`：0待审批 / 1通过 / 2驳回 / 3取消
4. **分布式预约互斥** — Redisson分布式锁 + DB FOR UPDATE双重兜底（`MeetingBookingServiceImpl.java`的`submitBooking`、`approve`方法），保证多实例并发预约不会出现时间重叠冲突
5. **异步消息推送预约通知** — `BookingNotificationProducer.java`实现消息发送；MQ为可选组件，未配置时自动跳过消息发送，不影响主业务流程

## 接口清单
### 预约模块 `MeetingBookingController`
基础路径：`/api/booking`
| 请求方法 | URL | 入参 DTO | 核心逻辑 | 权限 |
|---|---|---|---|---|
| POST | `/api/booking/submit` | BookingDTO (roomId, startTime, endTime, title, reason) | 获取Redisson锁 → 参数校验 → DB FOR UPDATE锁定会议室行 → 时间段冲突校验 → 插入预约记录 | 登录用户 |
| POST | `/api/booking/approve` | ApproveBookingDTO (bookingId, status, rejectReason) | 获取Redisson锁 → DB FOR UPDATE锁定会议室+预约行 → 通过状态二次校验时间段冲突 → 更新预约状态 | 管理员 |
| DELETE | `/api/booking/cancel/{id}` | 路径参数id | DB行锁 → 预约归属校验 → 状态校验（待审批/已通过可取消） | 登录用户 |
| GET | `/api/booking/admin/page` | BookingQueryDTO (roomId, resStatus, startTime, endTime) | XML多条件分页查询预约VO | 管理员 |
| GET | `/api/booking/user/page` | 同上 + 拦截器自动注入userId | 查询当前登录用户的预约记录 | 登录用户 |
| GET | `/api/booking/admin/query` | 同adminPage | adminPage别名接口 | 管理员 |
| GET | `/api/booking/user/query` | 同userPage | userPage别名接口 | 登录用户 |

### 会议室模块 `MeetingRoomController`
基础路径：`/api/room`
| 请求方法 | URL | 核心逻辑 | 权限 |
|---|---|---|---|
| GET | `/api/room/page` | 会议室全量分页查询 | 管理员 |
| POST | `/api/room/add` | 新增会议室 | 管理员 |
| PUT | `/api/room/update` | LambdaUpdateWrapper实现非空字段更新 | 管理员 |
| DELETE | `/api/room/delete/{id}` | ⚠️前置校验：若`meeting_booking`存在该roomId且状态为0/1的记录，返回400禁止删除 | 管理员 |
| GET | `/api/room/list` | 查询enabled=1可用会议室（前端预约弹窗数据源） | 登录用户 |
| PUT | `/api/room/enable/{id}` | 启用会议室 | 管理员 |
| PUT | `/api/room/disable/{id}` | 禁用会议室 | 管理员 |

### 其他模块
| Controller | 请求方法 | URL |
|---|---|---|
| LoginController | POST | `/api/login`, `/api/register` |
| UserController | GET/PUT | `/api/user/page`, `/api/user/role`, `/api/user/status`, `/api/user/resetPassword` |
| LogController | GET | `/api/log/page` |

## 分布式改造亮点
### 亮点一：Redisson分布式锁解决多实例预约并发冲突
**代码位置**：`MeetingBookingServiceImpl.submitBooking` / `approve`
**锁Key策略**：`booking:room:{roomId}`，采用**会议室粒度锁**，而非会议室+时间段粒度。
> 原因：如果使用会议室+时间段作为锁key，两个时间重叠但起止时间不同的预约会生成不同锁key，并发请求可同时获取锁，进入数据库校验后产生时间重叠冲突。会议室粒度锁将同一会议室所有预约请求串行执行，从源头规避漏判。

tryLock代码：
```java
locked = rLock.tryLock(5L, -1L, TimeUnit.SECONDS);
```
- waitTime = 5s：抢锁最大等待时间，超时返回提示：该会议室正在被他人预约，请稍后重试
- leaseTime = -1L：启用Redisson看门狗自动续期，默认每10s续期至30s，防止业务执行时间超过锁超时时间导致锁提前释放

**DB FOR UPDATE兜底**：
获取分布式锁后，对`meeting_room`和`meeting_booking`执行行锁查询。即使Redis故障导致锁丢失，数据库行锁依旧可以串行化同会议室事务，保障最终一致性。

**解锁保护**：
```java
if (locked && rLock.isHeldByCurrentThread()) {
    rLock.unlock();
}
```
判断锁持有者，避免看门狗释放锁后业务线程解锁抛出`IllegalMonitorStateException`异常。

### 亮点二：Nacos多实例水平扩容
配置文件 `application.yml`
```yaml
spring:
  application:
    name: admin-system-backend
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
        service: ${spring.application.name}   # 多实例使用相同服务名，组成集群
        ephemeral: true                       # 单机Nacos使用false；Nacos集群环境true
```
**多实例启动命令**
```bash
# 实例1
java -jar admin_system_backend.jar --server.port=8080
# 实例2，服务名相同，端口不同，自动注册至同一集群
java -jar admin_system_backend.jar --server.port=8081
```
Nacos完成服务注册发现，配合网关可基于服务名负载均衡转发请求到后端实例；Redisson锁保证跨实例下同一会议室预约串行执行。

### 亮点三：RocketMQ异步通知解耦（可选组件）
**代码位置**：`BookingNotificationProducer.java`
**可选加载机制**：Producer Bean仅当配置`rocketmq.name-server`才会创建；Service中`@Autowired(required=false)`注入，对象为null时直接跳过发送逻辑。
```java
// 仅配置name-server才加载该生产者
@Component
@ConditionalOnProperty(name = "rocketmq.name-server")
public class BookingNotificationProducer { ... }
```

| Topic | 触发时机 | 消息体字段 |
|---|---|---|
| `booking-notify-submit` | 预约提交成功 | bookingId, username, roomName, startTime, endTime, operatorId, ts |
| `booking-notify-approve` | 审批完成（PASS / REJECT） | bookingId, username, result(PASS/REJECT), rejectReason, operatorId, ts |

采用`RocketMQTemplate.asyncSend`异步回调发送，超时1000ms；主线程不阻塞，MQ发送失败try-catch捕获，不影响预约主业务流程。

启用配置 `application.yml`：
```yaml
rocketmq:
  name-server: 127.0.0.1:9876      # 注释该行，Producer不会加载
  producer:
    group: booking-producer-group
```

## 核心难点与解决方案
| 难点 | 解决方案 | 代码位置 |
|---|---|---|
| 多实例并发同一会议室时间段预约冲突 | Redisson会议室粒度锁（第一道）+ DB FOR UPDATE行锁（兜底）+ 锁内时间段冲突校验 | `MeetingBookingServiceImpl` submitBooking / approve |
| 分布式锁自动续期，防止业务超时锁提前释放 | Redisson leaseTime=-1 启用看门狗，每10s自动续期至30s | LOCK_LEASE_TIME = -1L |
| Redis主从切换、锁丢失风险 | 分布式锁基础上增加DB FOR UPDATE作为最终串行保障；看门狗自动续期防止业务中途锁失效 | selectByIdForUpdate + isHeldByCurrentThread() |
| MQ可选加载，未部署MQ时不阻塞主流程 | Producer增加`@ConditionalOnProperty`；Service使用`@Autowired(required=false)`，null判断跳过发送 | BookingNotificationProducer + Service注入 |
| MyBatis自定义@Select SQL，字段映射异常 | @Results显式声明字段映射（status→resStatus、enabled→status） | `MeetingBookingMapper.selectByIdForUpdate` / `MeetingRoomMapper.selectByIdForUpdate` |
| 用户禁用状态仍可登录 | 登录校验用户enabled状态，非1则拒绝登录 | `SysUserServiceImpl.login` |
| 删除会议室未拦截未完结预约 | 查询该会议室下状态0/1预约记录，存在则禁止删除 | `MeetingRoomController.delete` |
| updateTime自动填充 | MyMetaObjectHandler实现更新字段自动填充 | `MyMetaObjectHandler` |
| 操作日志缺少username/method/ip | TokenInterceptor将用户名存入request；日志工具从RequestContextHolder获取上下文 | TokenInterceptor + saveOperateLog |
| FOR UPDATE查询后booking.resStatus为null引发NPE | @Results显式映射status→resStatus | MeetingBookingMapper |

## 数据库表 ↔ 实体字段对照
### meeting_booking ↔ MeetingBooking
| DB 列 | Java 字段 | 映射方式 |
|---|---|---|
| `id` | `Long id` | 自动 |
| `room_id` | `Long roomId` | 驼峰命名自动转换 |
| `user_id` | `Long userId` | 驼峰命名自动转换 |
| `start_time` | `LocalDateTime startTime` | 驼峰命名自动转换 |
| `end_time` | `LocalDateTime endTime` | 驼峰命名自动转换 |
| **`status`** | **`Integer resStatus`** | **`@TableField("status")`** |
| `reject_reason` | `String rejectReason` | 驼峰命名自动转换 |
| `title` | `String title` | 自动 |
| `reason` | `String reason` | 自动 |
| `create_time` | `LocalDateTime createTime` | 驼峰 + `FieldFill.INSERT` |
| `update_time` | `LocalDateTime updateTime` | 驼峰 + `FieldFill.INSERT_UPDATE` |

### meeting_room ↔ MeetingRoom
| DB 列 | Java 字段 | 映射方式 |
|---|---|---|
| **`enabled`** | **`Integer status`** | **`@TableField("enabled")`** |

### operation_log ↔ OperationLog
| DB 列 | Java 字段 | 映射方式 | 自动填充来源 |
|---|---|---|---|
| **`operation`** | **`String operateType`** | **`@TableField("operation")`** | 业务参数 |
| **`params`** | **`String content`** | **`@TableField("params")`** | 业务参数 |
| `username` | `String username` | 自动 | ✅ request.getAttribute("username") (JWT解析) |
| `method` | `String method` | 自动 | ✅ request.getMethod() |
| `ip` | `String ip` | 自动 | ✅ X-Forwarded-For / remoteAddr |
| `cost_time` | `Long costTime` | 驼峰 | 预留字段 |

### sys_user ↔ SysUser
| DB 列 | Java 字段 | 映射方式 |
|---|---|---|
| **`real_name`** | **`String nickname`** | **`@TableField("real_name")`** |
| `password` | `String password` | `@JsonIgnore` + BCrypt加密存储 |

### BookingStatusEnum 枚举定义（代码统一使用，禁止硬编码）
| code | 常量 | 含义 |
|---|---|---|
| 0 | PENDING | 待审批 |
| 1 | PASS | 已通过 |
| 2 | REJECT | 已驳回 |
| 3 | CANCEL | 已取消 |

## 部署说明
### 环境准备
| 组件 | 最低要求 | 默认地址 | 配置位置 |
|---|---|---|---|
| Nacos | 2.x | 127.0.0.1:8848 | `spring.cloud.nacos.discovery.server-addr` |
| Redis | 5.x | 127.0.0.1:6379 | `spring.data.redis.*` |
| MySQL | 5.7+ | 127.0.0.1:3306 | `spring.datasource.*` |
| RocketMQ（可选） | 5.x | 127.0.0.1:9876 | `rocketmq.name-server`（注释则不加载） |

### 多实例打包与启动
```bash
# Maven打包，跳过单元测试
mvn clean package -DskipTests

# 实例1
java -jar target/admin_system_backend.jar --server.port=8080
# 实例2，相同服务名，注册到同一集群
java -jar target/admin_system_backend.jar --server.port=8081
```

### 环境配置清单（application.yml）
| 配置项 | 开发环境 | 生产环境 |
|---|---|---|
| `spring.cloud.nacos.discovery.server-addr` | `127.0.0.1:8848` | Nacos集群地址 |
| `spring.cloud.nacos.discovery.ephemeral` | `true`（单机Nacos改为false） | `true` |
| `spring.data.redis.host/port/password` | `127.0.0.1:6379` 空密码 | 生产Redis地址+密码 |
| `spring.datasource.url/username/password` | 本地MySQL | 生产MySQL实例 |
| `rocketmq.name-server` | 注释（不启用MQ） | RocketMQ NameServer地址 |
| `jwt.secret` | 固定测试字符串 | 生产环境替换为随机强密钥 |

## 项目目录简要说明
```
admin_system_backend
└── src/main/java/com/example/adminsystem
    ├── controller        // 接口控制器
    ├── service           // 业务接口
    │   └── impl          // 业务实现类
    ├── mapper            // MyBatis Mapper接口 + XML映射文件
    ├── entity            // 数据库实体
    ├── dto / vo          // 入参DTO、返回VO
    ├── enum_             // 枚举类
    ├── interceptor       // Token拦截器、请求上下文
    ├── config            // 配置类（MyMetaObjectHandler、Redisson、Nacos等）
    └── util              // 工具类
```

## 注意事项
1. 生产环境务必修改`jwt.secret`密钥，避免安全风险
2. MySQL使用`FOR UPDATE`行锁，需要保证事务隔离级别支持行锁，避免表锁
3. Nacos集群部署时ephemeral配置调整为true；单机Nacos推荐false
4. RocketMQ为可选组件，不配置name-server即可直接启动，不会影响预约业务
5. 分布式锁仅用于预约提交、审批场景；其余接口无锁设计
6. 所有状态使用枚举常量，禁止代码中直接写数字硬编码

---