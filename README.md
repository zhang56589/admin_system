# 会议室预约管理系统

## 项目简介
企业内部会议室预约管理后端，前后端分离。分布式改造后支持多实例集群部署，
使用 Redisson 分布式锁 + DB 行锁双重保障，解决跨实例并发预约冲突；
Nacos 实现服务注册发现；RocketMQ 异步推送预约通知。

## 技术栈（版本）
| 组件 | 版本 | 备注 |
|---|---|---|
| JDK | 21 | |
| Spring Boot | 3.4.0 | parent |
| Spring Cloud | 2023.0.3 | BOM import |
| Spring Cloud Alibaba | 2023.0.3.2 | Nacos discovery |
| MyBatis-Plus | 3.5.5 | spring-boot3-starter |
| MySQL | 5.7+ | 数据库 |
| Redisson | 3.37.0 | redisson-spring-boot-starter，自动装配 RedissonClient |
| RocketMQ | 2.3.1 | rocketmq-spring-boot-starter |
| Nacos | 2.x | 服务注册发现 |
| JJWT | 0.12.6 | JWT 签名 |
| BCrypt | Spring Security 6.4.1 | 密码加密 |
| Hutool | 5.8.34 | JSONUtil 等工具 |
| Vue3 + Element Plus | — | 前端，未改 |

## 核心功能
1. **用户权限管理** — JWT 登录鉴权（[TokenInterceptor.java](admin_system_backend/src/main/java/com/example/adminsystem/interceptor/TokenInterceptor.java)）、RBAC 角色控制（`UserRoleEnum`：0员工 / 1管理员）
2. **会议室资源管理** — CRUD（[MeetingRoomController.java](admin_system_backend/src/main/java/com/example/adminsystem/controller/MeetingRoomController.java)），删除前置拦截"存在未完结预约"（待审批/已通过）
3. **预约完整状态流转** — 提交 → 审批(PASS/REJECT) → 取消，状态常量 `BookingStatusEnum`：0待审批 / 1通过 / 2驳回 / 3取消
4. **分布式预约互斥** — Redisson 分布式锁 + DB FOR UPDATE 双重防线（[MeetingBookingServiceImpl.java](admin_system_backend/src/main/java/com/example/adminsystem/service/impl/MeetingBookingServiceImpl.java) 的 `submitBooking` / `approve`）
5. **异步消息推送预约通知** — [BookingNotificationProducer.java](admin_system_backend/src/main/java/com/example/adminsystem/service/BookingNotificationProducer.java) 可选，未配置 MQ 时自动跳过

## 接口清单

### 预约模块 `MeetingBookingController` (@RequestMapping `/api/booking`)

| 方法 | URL | 入参 DTO | 核心逻辑 | 权限 |
|---|---|---|---|---|
| POST | `/api/booking/submit` | BookingDTO (roomId, startTime, endTime, title, reason) | Redisson 锁 → 参数校验 → DB FOR UPDATE 会议室行 → hasTimeConflict → insert | 登录 |
| POST | `/api/booking/approve` | ApproveBookingDTO (bookingId, status, rejectReason) | Redisson 锁 → DB FOR UPDATE 会议室+预约行 → PASS 时二次 hasTimeConflict → updateById | 管理员 |
| DELETE | `/api/booking/cancel/{id}` | 路径参数 id | DB FOR UPDATE → 归属校验 → 状态校验（待审批/已通过可取消） | 登录 |
| GET | `/api/booking/admin/page` | BookingQueryDTO (roomId, resStatus, startTime, endTime) | XML 多条件分页查询 BookingVO | 管理员 |
| GET | `/api/booking/user/page` | 同上 + userId 拦截器注入 | XML 查询当前用户预约 | 登录 |
| GET | `/api/booking/admin/query` | 同 adminPage | 同 adminPage（别名接口） | 管理员 |
| GET | `/api/booking/user/query` | 同 userPage | 同 userPage（别名接口） | 登录 |

### 会议室模块 `MeetingRoomController` (@RequestMapping `/api/room`)

| 方法 | URL | 核心逻辑 | 权限 |
|---|---|---|---|
| GET | `/api/room/page` | 全部分页 | 管理员 |
| POST | `/api/room/add` | 新增 | 管理员 |
| PUT | `/api/room/update` | LambdaUpdateWrapper 非空字段更新 | 管理员 |
| DELETE | `/api/room/delete/{id}` | ⚠️ 前置校验：`meeting_booking` 中该 roomId 存在 status=0/1 记录时返回 400 | 管理员 |
| GET | `/api/room/list` | 查询 enabled=1 可用会议室（前端预约弹窗数据源） | 登录 |
| PUT | `/api/room/enable/{id}` | 启用 | 管理员 |
| PUT | `/api/room/disable/{id}` | 禁用 | 管理员 |

### 其他模块
| Controller | 方法 | URL |
|---|---|---|
| LoginController | POST | `/api/login`, `/api/register` |
| UserController | GET/PUT | `/api/user/page`, `/api/user/role`, `/api/user/status`, `/api/user/resetPassword` |
| LogController | GET | `/api/log/page` |

## 分布式改造亮点（代码位置 + 设计理由）

### 亮点一：Redisson 分布式锁解决多实例预约冲突

**代码位置**：`MeetingBookingServiceImpl.submitBooking` / `approve`

**锁 Key 策略**：`booking:room:{roomId}`（会议室粒度，非"会议室+时间段"）

**为什么用会议室粒度？**
- 用"会议室+时间段"做锁 key，两个时间重叠但起止点不同的预约（如 10:00-12:00 与 11:00-13:00）会生成不同锁 key → 两个并发请求同时获取到各自的锁 → 同时进入 DB 时间冲突校验 → 可能同时通过后 insert 产生时间重叠
- 会议室粒度让同一会议室的所有并发预约请求串行化，彻底避免漏判

**tryLock 参数**：
```java
locked = rLock.tryLock(5L, -1L, TimeUnit.SECONDS);
```
- waitTime = 5s：抢锁等待上限，超时返回友好提示"该会议室正在被他人预约，请稍后重试"
- leaseTime = -1L：**让 Redisson watch dog 自动续期**（默认每 10s 续到 30s），防止业务执行时间（查 DB + insert）超过 leaseTime 锁提前释放

**DB FOR UPDATE 兜底**：
锁内先 `selectByIdForUpdate(roomId)` 对 `meeting_room` 行加行锁，再 `selectByIdForUpdate(bookingId)` 对 `meeting_booking` 行加行锁。即使 Redis 故障锁丢失，DB 行锁仍能串行化同会议室事务，保证最终一致性。

**unlock 保护**：
```java
if (locked && rLock.isHeldByCurrentThread()) {
    rLock.unlock();
}
```
防止 Redis 故障锁被 watch dog 自动释放后，业务线程再调 unlock 抛 `IllegalMonitorStateException`。

### 亮点二：Nacos 多实例水平扩容

**配置位置**：`application.yml`
```yaml
spring:
  application:
    name: admin-system-backend
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
        service: ${spring.application.name}   # 多实例相同 service 名形成集群
        ephemeral: true                       # 单机 Nacos 用 false；集群用 true
```

**多实例启动命令**：
```bash
# 实例 1
java -jar admin_system_backend.jar --server.port=8080

# 实例 2 —— 相同 service name，不同 port，自动注册到同一集群
java -jar admin_system_backend.jar --server.port=8081
```

Nacos 自动完成服务注册发现，前端（需网关）通过 `admin-system-backend` 服务名负载均衡到任一实例；Redisson 锁在 Redis 层串行化跨实例同一会议室的并发。

### 亮点三：RocketMQ 异步通知解耦（可选）

**代码位置**：`BookingNotificationProducer.java`

**可选加载机制**（未配置 MQ 时自动跳过）：
```java
// Producer Bean 仅当 rocketmq.name-server 存在时才加载
@Component
@ConditionalOnProperty(name = "rocketmq.name-server")
public class BookingNotificationProducer { ... }

// Service 里 required=false 注入，null 时跳过发送
@Autowired(required = false)
private BookingNotificationProducer bookingNotificationProducer;
```

**Topic 常量**：
| Topic | 触发时机 | 消息体字段 |
|---|---|---|
| `booking-notify-submit` | 预约提交成功 | bookingId, username, roomName, startTime, endTime, operatorId, ts |
| `booking-notify-approve` | 审批完成（PASS / REJECT） | bookingId, username, result(PASS/REJECT), rejectReason, operatorId, ts |

**发送方式**：`RocketMQTemplate.asyncSend`（异步回调，timeout=1000ms），主线程只等 callback 不阻塞；内部 try-catch 兜底，MQ 故障不影响预约主流程。

**启用方式**：`application.yml` 配置：
```yaml
rocketmq:
  name-server: 127.0.0.1:9876          # 注释则 Producer Bean 不加载
  producer:
    group: booking-producer-group
```

## 核心难点与解决方案

| 难点 | 解决方案 | 代码位置 |
|---|---|---|
| 多实例并发同一会议室时间段预约冲突 | Redisson 会议室粒度锁（第一道）+ DB FOR UPDATE 行锁（兜底）+ 锁内 hasTimeConflict 校验 | `MeetingBookingServiceImpl` submitBooking / approve |
| 分布式锁自动续期，防止业务超时锁提前释放 | Redisson leaseTime=-1 启用 watch dog，每 10s 自动续到 30s | `LOCK_LEASE_TIME = -1L` |
| 锁失效 / 主从切换锁丢失 | 锁内保留 DB FOR UPDATE 作为最终串行化保障；watch dog 自动续期防止业务中途锁失效 | `selectByIdForUpdate` + `isHeldByCurrentThread()` |
| MQ 可选加载，未装时不阻塞主流程 | Producer 加 `@ConditionalOnProperty`；Service `@Autowired(required=false)` null 检查跳过 | BookingNotificationProducer + Service 注入 |
| 自定义 @Select SQL 不走 MyBatis-Plus 字段映射 | @Results 显式声明 status→resStatus、enabled→status 等映射 | `MeetingBookingMapper.selectByIdForUpdate` / `MeetingRoomMapper.selectByIdForUpdate` |
| 禁用状态用户仍可登录 | login 校验 `enabled != 1` 返回 null | `SysUserServiceImpl.login` |
| 删除会议室未拦截未完结预约 | 新增 status IN (0,1) 的 selectCount 校验 | `MeetingRoomController.delete` |
| updateTime 自动更新 | MyMetaObjectHandler.updateFill 补 strictUpdateFill | `MyMetaObjectHandler` |
| 操作日志缺失 username/method/ip | TokenInterceptor 放 username 到 request；saveOperateLog 从 RequestContextHolder 取 | TokenInterceptor + saveOperateLog |
| SELECT FOR UPDATE 后 booking.resStatus 为 null 导致 NPE | @Results 显式映射 status→resStatus | MeetingBookingMapper |

## 数据库表 ↔ 实体字段对照

### meeting_booking ↔ MeetingBooking
| DB 列 | Java 字段 | 映射方式 |
|---|---|---|
| `id` | `Long id` | 自动 |
| `room_id` | `Long roomId` | 驼峰 |
| `user_id` | `Long userId` | 驼峰 |
| `start_time` | `LocalDateTime startTime` | 驼峰 |
| `end_time` | `LocalDateTime endTime` | 驼峰 |
| **`status`** | **`Integer resStatus`** | **`@TableField("status")`** |
| `reject_reason` | `String rejectReason` | 驼峰 |
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
| `username` | `String username` | 自动 | ✅ request.getAttribute("username") (从 JWT 解析) |
| `method` | `String method` | 自动 | ✅ request.getMethod() |
| `ip` | `String ip` | 自动 | ✅ X-Forwarded-For / remoteAddr |
| `cost_time` | `Long costTime` | 驼峰 | 预留 |

### sys_user ↔ SysUser
| DB 列 | Java 字段 | 映射方式 |
|---|---|---|
| **`real_name`** | **`String nickname`** | **`@TableField("real_name")`** |
| `password` | `String password` | `@JsonIgnore` + BCrypt |

### BookingStatusEnum 枚举值（代码中统一使用，禁止硬编码）
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

### 启动多实例
```bash
# 打包
mvn clean package -DskipTests

# 实例 1
java -jar target/admin_system_backend.jar --server.port=8080

# 实例 2（相同 service name，自动注册同一集群）
java -jar target/admin_system_backend.jar --server.port=8081
```

### 配置修改清单（按环境调整 application.yml）
| 配置项 | 开发环境 | 生产环境 |
|---|---|---|
| `spring.cloud.nacos.discovery.server-addr` | `127.0.0.1:8848` | Nacos 集群地址 |
| `spring.cloud.nacos.discovery.ephemeral` | `true`（单机 Nacos 写 false） | `true` |
| `spring.data.redis.host/port/password` | `127.0.0.1:6379` 空密码 | 生产 Redis 地址 + 密码 |
| `spring.datasource.url/username/password` | 本地 MySQL | 生产 MySQL |
| `rocketmq.name-server` | 注释（不启用） | RocketMQ 地址 |
| `jwt.secret` | 固定字符串 | 生产请更换为随机强密钥 |
