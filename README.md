# 会议室预约管理系统 README

## 项目简介

会议室预约管理系统，**前后端分离架构**，面向企业内部使用，完成分布式改造，支持多实例集群部署。
后端采用 Spring Boot 3.4 + Spring Cloud Alibaba，使用 **Redisson分布式锁 + MySQL行锁** 双重机制解决多实例并发预约冲突；基于Nacos实现服务注册与发现；RocketMQ异步发送预约通知，实现业务与通知解耦。
前端基于 Vue3 + Vite + Element Plus，提供工作台、预约日历、会议室管理、预约审批、用户管理、操作日志等完整页面，支持角色权限控制，交互体验优化（侧边栏折叠、双视图展示、批量操作、二次确认、防重复提交）。

> 
> 后端工程：`admin_system_backend`
> 前端工程：`meeting-vue`

## 技术栈

### 后端

| 组件 | 版本 | 备注 |
| --- | --- | --- |
| JDK | 21 |  |
| Spring Boot | 3.4.0 | parent工程 |
| Spring Cloud | 2023.0.3 | BOM依赖管理 |
| Spring Cloud Alibaba | 2023.0.3.2 | Nacos服务注册发现 |
| MyBatis-Plus | 3.5.5 | SpringBoot3专用starter |
| MySQL | 5.7+ | 业务数据库 |
| Redisson | 3.37.0 | redisson-spring-boot-starter，自动装配RedissonClient |
| RocketMQ | 2.3.1 | rocketmq-spring-boot-starter，可选组件 |
| Nacos | 2.x | 服务注册发现中心 |
| JJWT | 0.12.6 | JWT签名鉴权 |
| BCrypt | Spring Security 6.4.1 | 用户密码加密 |
| Hutool | 5.8.34 | JSON工具、通用工具类 |

### 前端

| 类别 | 选型 |
| --- | --- |
| 框架 | Vue 3（`<script setup>` 组合式 API） |
| 构建 | Vite 8 |
| UI | Element Plus 2.14（中文 locale） |
| 图标 | @element-plus/icons-vue |
| 图表 | ECharts 6（按需引入，仅工作台使用） |
| 路由 | Vue Router 4（历史模式 + 懒加载 + 全局守卫） |
| 请求 | Axios（统一封装 `src/utils/request.js`） |

## 核心功能

### 后端能力

1. **用户权限管理**：JWT登录鉴权（`TokenInterceptor.java`）、RBAC角色控制，`UserRoleEnum`：0员工 / 1管理员；登录校验用户启用状态，禁用用户禁止登录。
2. **会议室资源管理**：会议室CRUD操作；删除会议室前置校验：存在未完结预约（待审批/已通过）时禁止删除；支持启用/禁用会议室。
3. **预约完整状态流转**：提交 → 审批(PASS/REJECT) → 取消。状态枚举`BookingStatusEnum`：0待审批 / 1通过 / 2驳回 / 3取消。
4. **分布式预约互斥**：Redisson分布式锁 + DB FOR UPDATE双重兜底，保证多实例并发预约不会出现时间重叠冲突。
5. **异步消息推送预约通知**：RocketMQ发送预约提交、审批通知；MQ为可选组件，未配置时自动跳过消息发送，不影响主业务流程。
6. **操作日志记录**：记录用户操作、请求IP、请求方法，支持管理员分页查询。

### 前端页面能力

1. **登录/注册**：账号密码登录，JWT Token存储，路由守卫拦截未授权访问。
2. **工作台 Dashboard**：统计卡片、预约状态饼图，员工与管理员展示不同统计项。
3. **预约日历 RoomCalendar**：月视图/日视图，预约状态色块展示，点击查看预约详情。
4. **员工端**
   - 可用会议室列表，提交预约表单（时间校验、字数限制、防重复提交）
   - 我的预约：多条件筛选、预约详情、取消预约（待审批/已通过可取消）
5. **管理员端**
   - 会议室管理：表格/卡片双视图，批量启用/禁用，删除前端预校验+后端最终校验
   - 预约审批：多条件日期筛选、批量审批、预约详情查看
   - 用户管理：用户分页、修改角色、启用/禁用用户、重置密码
   - 操作日志：全量操作日志分页查询
6. **布局体验**：侧边栏可折叠、自动面包屑导航；危险操作二次确认，提交按钮loading防重复点击。

## 接口清单

> 
> 统一响应体：`Result{ code, msg, data }`，成功 `code=200`；分页 `data = Page{ records, total }`。
> 所有接口统一前缀 `/api`

### 认证模块 LoginController

| 请求方法 | URL | 入参 | 返回 data | 权限 |
| --- | --- | --- | --- | --- |
| POST | `/api/login` | `{ username, password }` | `{ token, userId, username, role }` | 公开 |
| POST | `/api/register` | `{ username, password, nickname }` | - | 公开 |

### 用户模块 UserController

| 请求方法 | URL | 入参 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/user/page` | `pageNum,pageSize` | 管理员 |
| PUT | `/api/user/role` | `{ id, role }` | 管理员 |
| PUT | `/api/user/status` | `{ id, enabled }` | 管理员 |
| PUT | `/api/user/resetPassword` | `{ id, password }` | 管理员 |

### 预约模块 MeetingBookingController

基础路径：`/api/booking`

| 请求方法 | URL | 入参 DTO | 核心逻辑 | 权限 |
| --- | --- | --- | --- | --- |
| POST | `/api/booking/submit` | BookingDTO (roomId, startTime, endTime, title, reason) | 获取Redisson锁 → 参数校验 → DB FOR UPDATE锁定会议室行 → 时间段冲突校验 → 插入预约记录 | 登录用户 |
| POST | `/api/booking/approve` | ApproveBookingDTO (bookingId, status, rejectReason) | 获取Redisson锁 → DB FOR UPDATE锁定会议室+预约行 → 通过状态二次校验时间段冲突 → 更新预约状态 | 管理员 |
| DELETE | `/api/booking/cancel/{id}` | 路径参数id | DB行锁 → 预约归属校验 → 状态校验（待审批/已通过可取消） | 登录用户 |
| GET | `/api/booking/admin/page` | BookingQueryDTO (roomId, resStatus, startTime, endTime) | XML多条件分页查询预约VO | 管理员 |
| GET | `/api/booking/user/page` | 同上 + 拦截器自动注入userId | 查询当前登录用户的预约记录 | 登录用户 |
| GET | `/api/booking/admin/query` | 同adminPage | adminPage别名接口 | 管理员 |
| GET | `/api/booking/user/query` | 同userPage | userPage别名接口 | 登录用户 |

### 会议室模块 MeetingRoomController

基础路径：`/api/room`

| 请求方法 | URL | 核心逻辑 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/room/page` | 会议室全量分页查询 | 管理员 |
| POST | `/api/room/add` | 新增会议室 | 管理员 |
| PUT | `/api/room/update` | LambdaUpdateWrapper实现非空字段更新 | 管理员 |
| DELETE | `/api/room/delete/{id}` | ⚠️前置校验：若`meeting_booking`存在该roomId且状态为0/1的记录，返回400禁止删除 | 管理员 |
| GET | `/api/room/list` | 查询enabled=1可用会议室（前端预约弹窗数据源） | 登录用户 |
| PUT | `/api/room/enable/{id}` | 启用会议室 | 管理员 |
| PUT | `/api/room/disable/{id}` | 禁用会议室 | 管理员 |

### 操作日志 LogController

| 请求方法 | URL | 入参 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/log/page` | `pageNum,pageSize` | 管理员 |

## 分布式改造亮点

### 亮点一：Redisson分布式锁解决多实例预约并发冲突

**代码位置**：`MeetingBookingServiceImpl.submitBooking` / `approve`
**锁Key策略**：`booking:room:{roomId}`，采用**会议室粒度锁**，而非会议室+时间段粒度。

> 
> 原因：如果使用会议室+时间段作为锁key，两个时间重叠但起止时间不同的预约会生成不同锁key，并发请求可同时获取锁，进入数据库校验后产生时间重叠冲突。会议室粒度锁将同一会议室所有预约请求串行执行，从源头规避漏判。

tryLock代码：

```
locked = rLock.tryLock(5L, -1L, TimeUnit.SECONDS);
```

- waitTime = 5s：抢锁最大等待时间，超时返回提示：该会议室正在被他人预约，请稍后重试
- leaseTime = -1L：启用Redisson看门狗自动续期，默认每10s续期至30s，防止业务执行时间超过锁超时时间导致锁提前释放

**DB FOR UPDATE兜底**：
获取分布式锁后，对`meeting_room`和`meeting_booking`执行行锁查询。即使Redis故障导致锁丢失，数据库行锁依旧可以串行化同会议室事务，保障最终一致性。

**解锁保护**：

```
if (locked && rLock.isHeldByCurrentThread()) {
    rLock.unlock();
}
```

判断锁持有者，避免看门狗释放锁后业务线程解锁抛出`IllegalMonitorStateException`异常。

### 亮点二：Nacos多实例水平扩容

配置文件 `application.yml`

```
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

```
# 实例1
java -jar admin_system_backend.jar --server.port=8080
# 实例2，服务名相同，端口不同，自动注册至同一集群
java -jar admin_system_backend.jar --server.port=8081
```

Nacos完成服务注册发现，配合网关可基于服务名负载均衡转发请求到后端实例；Redisson锁保证跨实例下同一会议室预约串行执行。

### 亮点三：RocketMQ异步通知解耦（可选组件）

**代码位置**：`BookingNotificationProducer.java`
**可选加载机制**：Producer Bean仅当配置`rocketmq.name-server`才会创建；Service中`@Autowired(required=false)`注入，对象为null时直接跳过发送逻辑。

```
// 仅配置name-server才加载该生产者
@Component
@ConditionalOnProperty(name = "rocketmq.name-server")
public class BookingNotificationProducer { ... }
```

| Topic | 触发时机 | 消息体字段 |
| --- | --- | --- |
| `booking-notify-submit` | 预约提交成功 | bookingId, username, roomName, startTime, endTime, operatorId, ts |
| `booking-notify-approve` | 审批完成（PASS / REJECT） | bookingId, username, result(PASS/REJECT), rejectReason, operatorId, ts |
| 采用`RocketMQTemplate.asyncSend`异步回调发送，超时1000ms；主线程不阻塞，MQ发送失败try-catch捕获，不影响预约主业务流程。 |  |  |

启用配置 `application.yml`：

```
rocketmq:
  name-server: 127.0.0.1:9876     # 注释该行，Producer不会加载
  producer:
    group: booking-producer-group
```

## 核心难点与解决方案

| 难点 | 解决方案 | 代码位置 |
| --- | --- | --- |
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
| --- | --- | --- |
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
| --- | --- | --- |
| **`enabled`** | **`Integer status`** | **`@TableField("enabled")`** |

### operation_log ↔ OperationLog

| DB 列 | Java 字段 | 映射方式 | 自动填充来源 |
| --- | --- | --- | --- |
| **`operation`** | **`String operateType`** | **`@TableField("operation")`** | 业务参数 |
| **`params`** | **`String content`** | **`@TableField("params")`** | 业务参数 |
| `username` | `String username` | 自动 | ✅ request.getAttribute("username") (JWT解析) |
| `method` | `String method` | 自动 | ✅ request.getMethod() |
| `ip` | `String ip` | 自动 | ✅ X-Forwarded-For / remoteAddr |
| `cost_time` | `Long costTime` | 驼峰 | 预留字段 |

### sys_user ↔ SysUser

| DB 列 | Java 字段 | 映射方式 |
| --- | --- | --- |
| **`real_name`** | **`String nickname`** | **`@TableField("real_name")`** |
| `password` | `String password` | `@JsonIgnore` + BCrypt加密存储 |

### 枚举定义（前后端统一）

#### BookingStatusEnum 预约状态

| code | 常量 | 含义 | 前端tag类型 |
| --- | --- | --- | --- |
| 0 | PENDING | 待审批 | warning（橙） |
| 1 | PASS | 已通过 | success（绿） |
| 2 | REJECT | 已驳回 | danger（红） |
| 3 | CANCEL | 已取消 | info（灰） |

#### 会议室状态（RoomStatusEnum，映射数据库enabled）

| 值 | 含义 |
| --- | --- |
| 0 | 不可用（禁用） |
| 1 | 可用（启用） |

#### 用户角色 UserRoleEnum

| 值 | 含义 |
| --- | --- |
| 0 | 员工 |
| 1 | 管理员 |

#### 用户启用状态 enabled

| 值 | 含义 |
| --- | --- |
| 1 | 正常 |
| 0 | 禁用 |

> 
> 时间格式：**`yyyy-MM-dd HH:mm:ss`**，前后端双向一致，请求参数与返回均为字符串。
> 后端筛选语义：预约整段落在查询范围内 `start_time >= ? AND end_time <= ?`。

### 主要返回VO字段

- **BookingVO**：`id, roomId, roomName, userId, username, startTime, endTime, resStatus, rejectReason, title, reason, createTime`
- **MeetingRoom**：`id, roomName, capacity, location, description, status, createTime`
- **SysUser**：`id, username, nickname, phone, role, enabled, createTime`
- **OperationLog**：`id, userId, username, operateType, content, method, ip, costTime, createTime`

## 项目目录

### 后端 admin_system_backend

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

### 前端 meeting-vue

```
meeting-vue/
├─ index.html                     # 应用入口 HTML
├─ vite.config.js                 # Vite 配置（@ 别名、/api 代理到 8080）
├─ package.json
└─ src/
    ├─ main.js                    # 应用入口：注册 Element Plus + 路由 + 全局样式
    ├─ App.vue                    # 根组件（仅 router-view）
    ├─ style.css                  # 全局样式 + 主题变量
    ├─ router/
    │  └─ index.js                # 路由表 + 登录/角色守卫
    ├─ utils/
    │  ├─ request.js              # Axios 封装：请求拦截带token、响应拦截统一处理401/403/业务错误
    │  └─ meeting.js              # 历史遗留文件，保留不使用
    ├─ api/                       # 按模块拆分的接口封装（唯一对外调用入口）
    │  ├─ auth.js                 # 登录 / 注册
    │  ├─ room.js                 # 会议室
    │  ├─ booking.js              # 预约
    │  ├─ user.js                 # 用户管理（管理员）
    │  └─ log.js                  # 操作日志（管理员）
    └─ views/
        ├─ Login.vue              # 登录页
        ├─ Register.vue           # 注册页
        ├─ Home.vue               # 主布局（侧边栏/面包屑/顶部用户信息）
        ├─ Dashboard.vue          # 工作台（统计卡 + 状态占比饼图）
        ├─ RoomCalendar.vue       # 预约日历（月/日视图 + 状态色块 + 详情弹窗）
        ├─ employee/
        │  ├─ RoomList.vue        # 员工：可用会议室列表 + 提交预约
        │  └─ MyBookings.vue      # 员工：我的预约
        └─ admin/
            ├─ RoomManage.vue     # 管理员：会议室管理
            ├─ BookingAdmin.vue   # 管理员：预约审批
            ├─ UserManage.vue     # 管理员：用户管理
            └─ LogList.vue        # 管理员：操作日志
```

## 路由清单

| 路由 | 组件 | 可见角色 | 说明 |
| --- | --- | --- | --- |
| `/login` | Login.vue | 公开 | 登录 |
| `/register` | Register.vue | 公开 | 注册 |
| `/home` | Home.vue | 登录用户 | 主布局父路由，redirect到角色首页 |
| `/home/dashboard` | Dashboard.vue | 员工 + 管理员 | 工作台 |
| `/home/calendar` | RoomCalendar.vue | 员工 + 管理员 | 预约日历 |
| `/home/rooms` | employee/RoomList.vue | 员工 | 可用会议室 + 提交预约 |
| `/home/my-bookings` | employee/MyBookings.vue | 员工 | 我的预约 |
| `/home/room-manage` | admin/RoomManage.vue | 管理员 | 会议室管理 |
| `/home/booking-approve` | admin/BookingAdmin.vue | 管理员 | 预约审批 |
| `/home/users` | admin/UserManage.vue | 管理员 | 用户管理 |
| `/home/logs` | admin/LogList.vue | 管理员 | 操作日志 |

> 
> 员工登录默认进入 `/home/rooms`，管理员默认进入 `/home/room-manage`。

## 部署说明

### 环境准备

| 组件 | 最低要求 | 默认地址 | 配置位置 |
| --- | --- | --- | --- |
| Nacos | 2.x | 127.0.0.1:8848 | `spring.cloud.nacos.discovery.server-addr` |
| Redis | 5.x | 127.0.0.1:6379 | `spring.data.redis.*` |
| MySQL | 5.7+ | 127.0.0.1:3306 | `spring.datasource.*` |
| RocketMQ（可选） | 5.x | 127.0.0.1:9876 | `rocketmq.name-server`（注释则不加载） |
| Node.js | ≥18（推荐20+） | - | 前端工程 |

### 后端打包与启动

```
# Maven打包，跳过单元测试
mvn clean package -DskipTests
# 实例1
java -jar target/admin_system_backend.jar --server.port=8080
# 实例2，相同服务名，注册到同一集群
java -jar target/admin_system_backend.jar --server.port=8081
```

### 前端本地启动 & 构建

```
# 安装依赖
npm install
# 开发环境启动，访问 [http://localhost:5173](http://localhost:5173)
npm run dev
# 生产打包，产物输出dist
npm run build
# 本地预览打包产物
npm run preview
```

> 
> 开发环境Vite代理：`/api` 转发至 `[http://localhost:8080](http://localhost:8080)`，无需后端CORS。
> 生产部署：Nginx反向代理，将前端静态资源直接托管，`/api`转发到后端服务。

### 后端配置清单（application.yml）

| 配置项 | 开发环境 | 生产环境 |
| --- | --- | --- |
| `spring.cloud.nacos.discovery.server-addr` | `127.0.0.1:8848` | Nacos集群地址 |
| `spring.cloud.nacos.discovery.ephemeral` | `true`（单机Nacos改为false） | `true` |
| `spring.data.redis.host/port/password` | `127.0.0.1:6379` 空密码 | 生产Redis地址+密码 |
| `spring.datasource.url/username/password` | 本地MySQL | 生产MySQL实例 |
| `rocketmq.name-server` | 注释（不启用MQ） | RocketMQ NameServer地址 |
| `jwt.secret` | 固定测试字符串 | 生产环境替换为随机强密钥 |

## 已知限制（前后端对齐）

1. 员工无法做跨用户时段冲突预校验：员工端只有本人预约接口，无法查询他人占用，**冲突校验完全由后端分布式锁+数据库校验拦截**。
2. `/api/log/page` 仅管理员可访问，无用户过滤参数，无法实现员工查看本人操作记录。如需该功能需要后端新增接口参数。
3. 工作台会议室总数接口仅管理员可访问，员工工作台使用可用会议室替代。
4. 会议室卡片视图图片、设备标签为前端占位，数据库无对应字段，如需真实展示需要后端扩展实体。
5. 批量审批、批量启停会议室：前端循环调用单条接口，无后端批量接口；后端分页上限100。
6. 后端筛选语义：预约**整段落在查询时间范围内**，跨天跨边界预约可能不会被查询出来。

## 二次开发指引

### 后端

1. 新增接口统一在Controller编写，业务逻辑放入Service实现类。
2. 预约提交、审批等并发场景必须保留会议室粒度分布式锁+DB行锁兜底。
3. 状态统一使用枚举常量，禁止代码硬编码数字。
4. Mapper自定义SQL注意字段映射，避免NPE。

### 前端

1. 新增接口调用一律在`src/api/`模块新增函数，组件内引入调用，禁止组件内直接写axios。
2. 字段、枚举值严格和后端DTO/VO对齐，禁止私自修改。
3. 全局样式放入`src/style.css`；组件样式使用`<style scoped>`。
4. 新增页面使用路由懒加载，配置`meta.role`做角色权限控制。

## 注意事项

1. 生产环境务必修改`jwt.secret`密钥，避免安全风险。
2. MySQL使用`FOR UPDATE`行锁，需要保证事务隔离级别支持行锁，避免表锁。
3. Nacos集群部署时ephemeral配置调整为true；单机Nacos推荐false。
4. RocketMQ为可选组件，不配置name-server即可直接启动，不会影响预约业务。
5. 分布式锁仅用于预约提交、审批场景；其余接口无锁设计。
6. 所有状态使用枚举常量，禁止代码中直接写数字硬编码。
7. 前端生产环境使用Nginx反向代理，`/api`转发后端，保持前端baseURL不变。

## 冒烟验证清单

```
# 后端打包
mvn clean package -DskipTests
# 前端打包
npm run build
npm run dev
```

1. 未登录访问受保护路由 → 跳转登录；员工访问管理员路由 → 被拦截。
2. 员工：登录 → 工作台统计与饼图 → 侧边栏折叠/面包屑 → 预约日历色块与详情 → 提交预约（工作日/结束>开始/字数/防重复）→ 我的预约取消（0/1）。
3. 管理员：登录 → 工作台 → 预约审批（日期筛选 + 批量通过）→ 会议室管理（双视图 / 批量启停 / 删除预校验）→ 用户管理 → 操作日志。

---
