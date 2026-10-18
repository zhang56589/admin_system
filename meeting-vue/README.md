# 会议室预约管理系统 · 前端（meeting-vue）

基于 **Vue 3 + Vite + Element Plus** 的会议室预约管理系统前端，前后端分离，对接 Spring Boot 3.4 + MyBatis-Plus 后端（`/api` 前缀）。

本仓库为**纯前端**工程，后端源码位于同级目录 `../admin_system_backend`。所有接口调用、字段名、枚举值均与后端严格对齐，**不允许**私自改动。

---

## 一、技术栈

| 类别 | 选型 |
|---|---|
| 框架 | Vue 3（`<script setup>` 组合式 API） |
| 构建 | Vite 8 |
| UI | Element Plus 2.14（中文 locale） |
| 图标 | @element-plus/icons-vue |
| 图表 | ECharts 6（**按需引入**，仅 `Dashboard.vue` 使用） |
| 路由 | Vue Router 4（历史模式 + 懒加载 + 全局守卫） |
| 请求 | Axios（统一封装 `src/utils/request.js`） |

---

## 二、目录结构

```
meeting-vue/
├─ index.html                     # 应用入口 HTML
├─ vite.config.js                 # Vite 配置（@ 别名、/api 代理到 8080）
├─ package.json
└─ src/
   ├─ main.js                     # 应用入口：注册 Element Plus + 路由 + 全局样式
   ├─ App.vue                     # 根组件（仅 router-view）
   ├─ style.css                   # 全局样式 + 主题变量（本次升级新增主题/圆角/栅格/卡片/表格样式）
   ├─ router/
   │  └─ index.js                 # 路由表 + 登录/角色守卫（本次新增 dashboard、calendar 两条子路由）
   ├─ utils/
   │  ├─ request.js               # Axios 封装：请求拦截带 token、响应拦截统一处理 401/403/业务错误
   │  └─ meeting.js               # ⚠️ 历史遗留文件（旧版接口路径，未被引用，保留但请勿使用）
   ├─ api/                        # 按模块拆分的接口封装（唯一对外调用入口）
   │  ├─ auth.js                  # 登录 / 注册
   │  ├─ room.js                  # 会议室
   │  ├─ booking.js               # 预约
   │  ├─ user.js                  # 用户管理（管理员）
   │  └─ log.js                   # 操作日志（管理员）
   └─ views/
      ├─ Login.vue                # 登录页
      ├─ Register.vue             # 注册页
      ├─ Home.vue                 # 主布局（侧边栏/面包屑/顶部用户信息，本次升级为可折叠）
      ├─ Dashboard.vue            # ★新增：工作台（统计卡 + 状态占比饼图）
      ├─ RoomCalendar.vue         # ★新增：预约日历（月/日视图 + 状态色块 + 详情弹窗）
      ├─ employee/
      │  ├─ RoomList.vue          # 员工：可用会议室列表 + 提交预约（本次新增表单校验）
      │  └─ MyBookings.vue        # 员工：我的预约（本次新增多条件筛选/取消/详情）
      └─ admin/
         ├─ RoomManage.vue        # 管理员：会议室管理（本次新增双视图/批量启停/删除预校验）
         ├─ BookingAdmin.vue      # 管理员：预约审批（本次新增日期筛选/批量审批）
         ├─ UserManage.vue        # 管理员：用户管理
         └─ LogList.vue           # 管理员：操作日志
```

---

## 三、快速开始

### 1. 环境要求
- Node.js ≥ 18（建议 20+）
- 后端服务运行在 `http://localhost:8080`

### 2. 安装依赖
```bash
npm install
```

### 3. 启动开发服务
```bash
npm run dev
```
访问 `http://localhost:5173`。

### 4. 生产构建
```bash
npm run build      # 产物输出到 dist/
npm run preview    # 本地预览构建产物
```

### 5. 跨域说明（开发环境）
前端统一以 `/api` 作为 Axios `baseURL`，由 Vite 开发代理转发到后端，**无需后端额外配置 CORS**：

`vite.config.js`
```js
server: {
  port: 5173,
  proxy: {
    '/api': { target: 'http://localhost:8080', changeOrigin: true }
  }
}
```

> 生产部署时请由 Nginx 等反向代理将 `/api` 转发到后端服务，保持前端 `baseURL=/api` 不变。

---

## 四、鉴权与权限

### 1. 登录态
- 登录成功后将后端返回的 `token / userId / username / role` 存入 `localStorage`。
- `request.js` 请求拦截器自动在请求头携带：
  - `token`（后端 `TokenInterceptor` 读取的字段）
  - `Authorization: Bearer <token>`（兼容通用规范）

### 2. 响应拦截（`src/utils/request.js`）
- 统一响应体 `Result{ code, msg, data }`，成功后返回整个 `Result`（业务代码取 `res.data`）。
- `code === 200`：正常返回。
- `code === 401`（或 HTTP 401）：清除登录态并跳转登录页（附带 `redirect`）。
- `code !== 200`：`ElMessage.error(msg)` 统一提示业务异常。
- HTTP 403：提示「权限不足」。
- 网络异常：提示「网络异常，请确认后端服务已启动」。

### 3. 角色与路由守卫（`src/router/index.js`）
- `role = 0` 员工，`role = 1` 管理员。
- 未登录访问受保护路由 → 跳转 `/login`。
- 路由 `meta.role` 限定角色，普通员工访问管理员页面将被拦截并跳回其首页。
- 登录页/注册页标记 `meta.public`。

---

## 五、后端接口契约对照（只读，前端不得改动）

> 统一响应体：`Result{ code, msg, data }`，成功 `code=200`；分页 `data = Page{ records, total }`。

### 认证 `LoginController`
| 方法 | 路径 | 入参 | 返回 data |
|---|---|---|---|
| POST | `/api/login` | `{ username, password }` | `{ token, userId, username, role }` |
| POST | `/api/register` | `{ username, password, nickname }` | - |

### 预约 `MeetingBookingController`
| 方法 | 路径 | 入参 / 说明 |
|---|---|---|
| POST | `/api/booking/submit` | `{ roomId, startTime, endTime, title, reason }`（员工提交，后端含分布式锁 + 时段冲突校验） |
| POST | `/api/booking/approve` | `{ bookingId, status, rejectReason }`，`status`：`1` 通过 / `2` 驳回（仅管理员，仅待审批可审批） |
| DELETE | `/api/booking/cancel/{id}` | 路径参数 `id`（本人预约；支持 待审批0 / 已通过1，且会议未开始） |
| GET | `/api/booking/admin/page` | 管理员分页；`pageNum,pageSize,roomId,resStatus,startTime,endTime` |
| GET | `/api/booking/user/page` | 员工本人分页；参数同上 |
| GET | `/api/booking/admin/query` | 管理员条件查询分页；参数同上 |
| GET | `/api/booking/user/query` | 员工本人条件查询分页；参数同上 |

### 会议室 `MeetingRoomController`
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/room/page` | 管理员分页全部会议室（`pageNum,pageSize`） |
| GET | `/api/room/list` | 员工可用（启用中）会议室列表 |
| POST | `/api/room/add` | 新增 |
| PUT | `/api/room/update` | 编辑（仅更新非 null 字段） |
| DELETE | `/api/room/delete/{id}` | 删除（**后端已校验**：存在待审批/已通过预约则拒绝，返回 `code=400`） |
| PUT | `/api/room/enable/{id}` | 启用 |
| PUT | `/api/room/disable/{id}` | 禁用 |

### 用户 `UserController`（均 `@RequireAdmin`）
| 方法 | 路径 | 入参 |
|---|---|---|
| GET | `/api/user/page` | `pageNum,pageSize` |
| PUT | `/api/user/role` | `{ id, role }` |
| PUT | `/api/user/status` | `{ id, enabled }`（`1` 正常 / `0` 禁用） |
| PUT | `/api/user/resetPassword` | `{ id, password }` |

### 操作日志 `LogController`（`@RequireAdmin`）
| 方法 | 路径 | 入参 |
|---|---|---|
| GET | `/api/log/page` | `pageNum,pageSize` |

---

## 六、字段与枚举对照（严禁改动）

### 预约状态 `resStatus`（`BookingStatusEnum`）
| 值 | 含义 | 前端 tag |
|---|---|---|
| `0` | 待审批 | 橙（`warning`） |
| `1` | 已通过 | 绿（`success`） |
| `2` | 已驳回 | 红（`danger`） |
| `3` | 已取消 | 灰（`info`） |

### 会议室状态 `status`（`RoomStatusEnum`，映射数据库 `enabled`）
| 值 | 含义 |
|---|---|
| `0` | 不可用（禁用） |
| `1` | 可用（启用） |

### 其他
- 角色 `role`：`0` 员工 / `1` 管理员（`UserRoleEnum`）
- 用户启用 `enabled`：`1` 正常 / `0` 禁用
- 时间格式：**`yyyy-MM-dd HH:mm:ss`**（前后端双向一致，请求参数与返回均为字符串）

### 主要返回对象字段
- **BookingVO**：`id, roomId, roomName, userId, username, startTime, endTime, resStatus, rejectReason, title, reason, createTime`
- **MeetingRoom**：`id, roomName, capacity, location, description, status, createTime`
- **SysUser**：`id, username, nickname, phone, role, enabled, createTime`
- **OperationLog**：`id, userId, username, operateType, content, method, ip, costTime, createTime`

> 后端 `MeetingBookingMapper.xml` 中，`startTime/endTime` 筛选语义为「**预约整段落在查询范围内**」（`start_time >= ? AND end_time <= ?`），前端筛选与日历查询均基于此语义。

---

## 七、页面与路由对照

| 路由 | 组件 | 可见角色 | 说明 |
|---|---|---|---|
| `/login` | Login.vue | 公开 | 登录 |
| `/register` | Register.vue | 公开 | 注册 |
| `/home` | Home.vue | 登录用户 | 主布局（父级，`redirect` 到角色首页） |
| `/home/dashboard` | Dashboard.vue | 员工 + 管理员 | ★新增 工作台 |
| `/home/calendar` | RoomCalendar.vue | 员工 + 管理员 | ★新增 预约日历 |
| `/home/rooms` | employee/RoomList.vue | 员工 | 可用会议室 + 提交预约 |
| `/home/my-bookings` | employee/MyBookings.vue | 员工 | 我的预约 |
| `/home/room-manage` | admin/RoomManage.vue | 管理员 | 会议室管理 |
| `/home/booking-approve` | admin/BookingAdmin.vue | 管理员 | 预约审批 |
| `/home/users` | admin/UserManage.vue | 管理员 | 用户管理 |
| `/home/logs` | admin/LogList.vue | 管理员 | 操作日志 |

> 员工登录后默认进入 `/home/rooms`，管理员默认进入 `/home/room-manage`（沿用原有跳转逻辑）；「工作台 / 预约日历」通过左侧菜单进入。

---

## 八、本次体验升级清单（全部为「新增」，未改动既有业务逻辑）

### 1. 视觉升级
- 全局主题变量（`src/style.css`）：统一主色、成功/警告/危险/信息色，覆盖 Element Plus `--el-color-primary` 系列变量。
- **全局圆角统一 8px**（`--el-radius`、`--el-border-radius-base`），间距遵循 **8px 栅格**（`--app-space-1..4`）。
- 卡片统一圆角 + 轻阴影；表格表头底色、**斑马行**、悬停高亮（纯 CSS，未改模板）。
- 状态 tag 统一标准色（待审批橙 / 已通过绿 / 已驳回红 / 已取消灰）。

### 2. 布局升级（`Home.vue`）
- 侧边栏**可折叠**（`isCollapse` + 折叠按钮，宽度 220px ↔ 64px，菜单带图标）。
- 顶部导航 **面包屑自动生成**（「角色 / 当前页」）+ 用户信息 + 退出登录。
- 追加「工作台 / 预约日历」菜单项。

### 3. 新增视图
- **工作台 `Dashboard.vue`**：
  - 统计卡：会议室总数、启用会议室、今日预约数量、待审批预约数量（管理员 4 卡；员工降级为 可用会议室 / 今日我的预约 / 我的待审批 3 卡）。
  - ECharts **按需引入**饼图：预约状态占比。
- **预约日历 `RoomCalendar.vue`**：月视图（按周分列、日期网格、状态色块）+ 日视图（当天时间段列表），点击色块/行弹出预约详情。

### 4. 预约体验
- **提交预约（RoomList.vue）**：仅工作日可选（周末日期禁用 + 校验）、结束时间必须晚于开始时间、会议主题 ≤ 30 / 事由 ≤ 200 字、自动回填当前登录人（只读展示）、按钮 loading 防重复提交。
- **我的预约（MyBookings.vue）**：新增会议室 / 起止日期 / 状态多条件筛选（筛选值随分页保留）；`待审批0` 与 `已通过1` 均可取消（危险操作二次确认）；详情弹窗。
- **预约审批（BookingAdmin.vue）**：新增起止日期筛选；新增**批量通过**（勾选 + 二次确认）。

### 5. 会议室管理（RoomManage.vue）
- **表格视图 / 卡片视图**切换：卡片展示名称、容量、位置、描述、状态 + **照片占位**。
- **批量启用 / 禁用**（勾选 + 二次确认）。
- **删除前预校验**：先查询该会议室是否存在未完结（`0`/`1`）预约，存在则前端拦截；不存在再走原有删除流程（后端仍会最终校验）。

### 6. 全局体验
- 危险操作（取消预约、删除会议室、批量审批、批量启停）均有 `ElMessageBox` 二次确认。
- 提交类按钮均带 `loading`，防重复点击。

---

## 九、已知限制（前后端对齐注意，**未新增任何后端接口**）

1. **员工无法做跨用户时段冲突预校验**：员工端只有 `/api/booking/user/page`（仅本人）可用，无法查询他人/会议室占用，故前端不做冲突预校验。提交时的时段冲突由**后端**（Redisson 分布式锁 + `hasTimeConflict`）最终拦截并返回 `code=400`。
2. **「本人操作记录」弹窗未实现**：`GET /api/log/page` 仅管理员可访问且**无按用户过滤参数**，无法实现「本人操作记录」，故操作日志维持管理员页面（展示全部日志）。如需该功能需后端新增按用户过滤参数。
3. **工作台「会议室总数」仅管理员可见**：`GET /api/room/page` 仅管理员有权限，员工端无接口可获取总数，故员工端工作台不含该卡片（以「可用会议室」替代）。
4. **会议室无「设备 / 图片」字段**：卡片视图的设备标签与照片均为**前端占位**，未新增后端字段。如需真实展示需后端扩展 `MeetingRoom`。
5. **批量操作为前端并发调用**：批量审批 / 批量启停均由前端 `Promise.all` 并行调用既有**单条**接口，非新增批量后端接口；后端 `pageSize` 上限为 100（`buildPage` 中 `Math.min(pageSize, 100)`）。
6. **日历/筛选的时间范围语义**：后端按「预约整段落在范围内」过滤，跨天且跨范围边界的预约可能不被计入。
7. `src/utils/meeting.js` 为历史遗留文件（旧接口路径、未被引用），保留未删除，请勿使用，统一使用 `src/api/*`。

---

## 十、二次开发指引

- **新增接口调用**：一律在 `src/api/<模块>.js` 中新增函数，组件内引入使用；**禁止**在组件里直接写 Axios 请求。
- **保持字段一致**：提交/查询参数名、返回字段名必须与后端 `DTO/VO` 一致（`resStatus`、`roomId`、`startTime`、`endTime` 等）。
- **枚举使用统一**：预约状态、会议室状态请复用页面内的 `statusText / statusTagType` 映射，勿改数值。
- **样式**：全局样式放 `src/style.css`（由 `main.js` 引入）；组件样式一律 `<style scoped>`，避免污染。
- **路由**：新增页面使用懒加载 `() => import(...)`，并按需配置 `meta.role` 做角色限制。

---

## 十一、验证

```bash
npm run build          # 构建通过即视为静态校验通过
npm run dev            # 启动后访问 http://localhost:5173
```

冒烟清单：
1. 未登录访问受保护路由 → 跳转登录；员工访问管理员路由 → 被拦截。
2. 员工：登录 → 工作台统计与饼图 → 侧边栏折叠/面包屑 → 预约日历色块与详情 → 提交预约（工作日/结束>开始/字数/防重复）→ 我的预约取消（`0`/`1`）。
3. 管理员：登录 → 工作台 → 预约审批（日期筛选 + 批量通过）→ 会议室管理（双视图 / 批量启停 / 删除预校验）→ 用户管理 → 操作日志。
