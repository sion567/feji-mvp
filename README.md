# 无人机自控平台（基于大疆上云API）

基于 DJI Cloud API 的无人机自主控制平台。当前状态：**后端 + 前端 MVP 骨架已初始化，MQTT→设备识别→REST/WebSocket 全链路已实测打通**。

计划文档：
- [大疆无人机自控平台建设计划.md](大疆无人机自控平台建设计划.md)（总体分期规划）
- [MVP功能清单与范围说明.md](MVP功能清单与范围说明.md)（MVP 范围与排期）
- [WPML航线文件映射设计.md](WPML航线文件映射设计.md)（航线 KMZ 解析与内部模型映射）
- [多厂商接入抽象设计.md](多厂商接入抽象设计.md)（防腐层方案与分阶段路线；Phase 0 已完成：vendor 字段、DjiCommandSender 收敛、中立遥测模型）

## 目录结构

```
feiji/
├── backend/                 # 后端：Java 17+ / Spring Boot 3.3.x
│   └── src/main/java/com/feiji/cloud/
│       ├── cloudapi/device/ # 对齐大疆官方消息模型的 DTO（OSD/设备型号/mode_code 标签）
│       ├── cloudapi/wayline/ # 航线 KMZ/WPML 解析 → 内部统一模型（见《WPML航线文件映射设计.md》）
│       ├── wayline/         # 航线库（上传/存储/预览）+ 航线任务（派发/进度/定时巡检）
│       ├── control/         # DRC 指令飞行（进入/退出/指点飞行/一键起飞/摇杆+心跳）
│       ├── map/             # 地图标绘元素（点/线/面，WGS-84 存储）
│       ├── vendor/           # 厂商抽象：spi（VendorAdapter 接口/命令对象/CommandRouter）+ dji（大疆适配器）
│       ├── plot/             # 地块管理（多边形边界/面积自动计算/信息维护）
│       ├── flight/           # 架次记录（起降自动识别/里程/最大高度/全量遥测采样落库）
│       ├── mqtt/            # MQTT 接入框架（连接/订阅/Topic 分发/指令信封/应答监听）
│       ├── vendor/dji/      # 大疆指令发送收敛（DjiCommandSender/DjiModes）——Phase 1 将成为 VendorAdapter 实现
│       ├── device/          # 设备注册表(Redis 实时态) + 档案(MySQL,含 vendor) + Telemetry 中立遥测 + 轨迹 + REST
│       ├── live/            # 直播：live_start_push/stop 指令、会话状态机、ZLM/SRS 播放地址
│       ├── ws/              # 前端 WebSocket 推送网关（OSD 节流、直播/任务事件）
│       ├── auth/            # JWT 登录、RBAC(@RequireRole)、审计标记(@Audited)
│       ├── user|workspace|audit/  # 用户/角色/工作空间/审计日志（MySQL）
├── frontend/                # 前端：Vue3 + TypeScript + Vite + Leaflet
├── tools/
│   └── mock-device/         # 虚拟设备模拟器（Node：机场+无人机 OSD/上下线/直播指令应答，可选 ffmpeg 实推）
├── deploy/
│   └── docker-compose.yml   # 基础设施：EMQX / MySQL / Redis / MinIO / ZLMediaKit
├── 大疆无人机自控平台建设计划.md
└── MVP功能清单与范围说明.md
```

## 技术栈

- 后端：Java 17+（本机 JDK 25 已验证）、Spring Boot 3.3.x、MyBatis-Plus、Redis、Eclipse Paho（MQTT）、JJWT、springdoc
- 前端：Vue 3.5、Vue Router 4、TypeScript、Vite 5、Leaflet 1.9（本机 Node 22 已验证）
- 基础设施：EMQX 5（MQTT Broker）、MySQL 8、Redis 7、MinIO（对象存储）、ZLMediaKit（流媒体）

## 快速开始

### 1. 启动基础设施（需要 Docker；本机未装可先跳过）

> 本机若已装有 Mosquitto(1883) 与 Redis(6379)，骨架阶段可直接使用，无需 Docker。

```bash
cd deploy && docker compose up -d
```

- EMQX 控制台：http://localhost:18083（admin/public）
- MinIO 控制台：http://localhost:9001（feiji/feiji123456）
- ZLMediaKit：http://localhost:8080

### 2. 启动后端（需要 JDK 17+ 与 Maven）

```bash
cd backend && mvn spring-boot:run              # 默认连 MySQL（localhost:3306，见 application.yml）
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=h2   # 无 MySQL 时用 H2 文件库演示
```

服务端口 6789。启动时自动执行 `db/schema.sql` 建表并播种默认工作空间与内置管理员（admin/admin123，来自 app.security 配置）。MQTT 未就绪时后台每 5 秒重试，不影响启动。运行日志写入 `backend/logs/feiji-backend.log`。

### 3. 启动前端（需要 Node 18+）

```bash
cd frontend && npm install && npm run dev
```

访问 http://localhost:5173（Vite 已代理 /api 与 /ws 到 6789），内置管理员 admin/admin123。

### 4. 无真机自测：虚拟设备模拟器（需要 Node 18+）

```bash
cd tools/mock-device && npm install && node mock-dock.mjs
```

模拟 1 台 Dock 2 + 1 架 M3TD：周期上报 OSD（无人机绕机场飞行）、上下线拓扑、直播能力；收到开播指令后用 ffmpeg 向下发的 RTMP 地址推测试流（`--no-ffmpeg` 关闭）。加 `--demo-alarm` 可循环演示安全告警（HMS 事件 → 低电量 → RTK 断连，每 45 秒切换）。可自定义 SN/坐标。

### 5. 验证

```bash
# 接口文档
http://localhost:6789/swagger-ui.html

# WebSocket 冒烟（连上后 5 秒内用 EMQX 控制台或 mosquitto_pub 发一条 OSD）
node frontend/scripts/ws-smoke.mjs ws://localhost:6789/ws/v1/messages
```

用 mosquitto_pub 模拟官方格式消息（详见下节）后：

- 态势大屏地图出现设备图标，无人机位置随 OSD 移动，右侧详情面板实时刷新，可一键**回放轨迹**（Redis 环形存储，最近 2000 点）
- 设备管理页列出 SN / 名称（可改名）/ 型号（如 Matrice 3TD）/ 类型 / 在线状态 / 父子拓扑，操作员以上可**改名/移除**设备
- **直播**：选中无人机点「开始直播」→ 后端下发 `live_start_push`（含 RTMP 推流地址）→ 设备应答后会话变 LIVE，前端 mpegts.js 播放 HTTP-FLV。视频面需流媒体服务器运行（`deploy/docker-compose.yml` 已编排 **SRS**，支持 `media-server-type: zlm|srs` 双配置）
- **航线库**：航线管理页上传 KMZ → 解析元数据落库 → 地图预览（天地图影像底图，航点连线+动作标记）
- **航线任务**：任务管理页创建「立即 / 定时（单次）/ 每日巡检」任务 → `flighttask_prepare/execute` 下发 → 进度实时显示（WS 推送）→ 30 分钟无进度自动判失败
- **地图标绘**：大屏工具条绘制点位/折线/多边形（WGS-84 统一存储），侧栏标注列表可删除；二期按官方 element_group 结构同步给 Pilot 2
- **地块管理**：地图绘制地块边界，自动计算面积（亩），维护编号/类别/种植内容等信息；大屏同步展示地块边界
- **飞行记录（架次）**：OSD 飞行状态跳变自动开闭架次——起飞/降落时间与位置、最大高度、里程（haversine 累计）、关联任务；架次内全量遥测 3 秒/点落库（`flight_telemetry`），飞行记录页可查列表并地图回放轨迹（起绿落红）；失联 10 分钟强制闭合作兜底
- **合规报备**：任务报备状态（未报备/已报备/已批复+批复号）留痕并快照到架次；航线最大执行高度超 120 米时创建任务返回合规提醒（适飞空域超 120 米需提前 1 日 12 时前在民航局 UOM 平台申请飞行活动）。报备责任在运营单位，平台仅辅助留痕
- **DRC 指令飞行**：选中设备后一键起飞、地图点击指点飞行（`drc_flyto`）、进入/退出 DRC（含周期心跳），方向键经 `drc/down` 发送摇杆轴量
- **安全告警**：HMS 健康告警事件（`device_hms`/`device_exit_hms`）、DRC 飞行中 HMS 推送、OSD 派生的低电量（30% 警告 / 15% 严重，阈值下穿触发一次）与 RTK 断连/恢复自动转为告警：落库（`device_alert`）+ WebSocket 实时推送，大屏右上角告警面板可折叠查看，接口 `GET /api/v1/alerts`
- **系统管理**（管理员可见）：用户管理（创建/删除/改角色）、工作空间、审计日志（登录/开播/改名等操作全留痕）

## 权限模型（MVP RBAC）

三级角色（层级递减）：**管理员 ADMIN**（全部功能 + 用户/工作空间管理 + 审计日志）→ **操作员 OPERATOR**（直播开关、设备改名/移除）→ **观察者 OBSERVER**（只看）。接口用 `@RequireRole` 声明最低角色，由 JWT 拦截器统一校验；设备数据按用户所属工作空间隔离。

## 直播链路说明

- 协议：官方 `live_start_push` / `live_stop_push`（services，tid/bid 配对 services_reply），`live_set_quality` 预留
- 流媒体：支持 **SRS**（默认，docker-compose 已编排）与 ZLMediaKit，`app.live.media-server-type` 切换（两者播放地址规则不同）
- 推流地址按**完整 RTMP 地址（含流名）**下发给设备；真机联调若确认 Pilot/机场只接受 host/app 形式并自行追加流名，调整 `LiveStreamService.buildPushUrl` 与播放地址的对应关系即可
- 会话状态机：STARTING →(应答 result=0)→ LIVE →(stop)→ 移除；15 秒无应答定时巡检置 FAILED；同一 videoId 防重复开播

## 航线任务说明

- 协议（对齐官方 flighttask 指令族）：`flighttask_prepare`（data 平铺 flight_id/execute_time/task_type/file{url,fingerprint}）→ 立即任务补发 `flighttask_execute`；进度事件 `flighttask_progress`（从网关 SN 上报）
- **KMZ 下载**：任务派发时 `file.url` 指向平台 `/api/v1/waylines/files/{key}/download`（设备可达即可，无需 MinIO；`app.wayline.download-base-url` 配置设备可达地址）
- 任务类型：IMMEDIATE（创建即派发）/ TIMED（调度器到点派发）/ RECURRING（每日 HH:mm 派发，终态后自动复位等次日）
- 状态机：PENDING → DISPATCHING → RUNNING → SUCCESS/FAILED/CANCELED；30 分钟无进度超时判失败
- 底图：天地图（CGCS2000 ≈ WGS-84，无人机坐标直接叠图无需转换），key 在 `frontend/.env` 的 `VITE_TDT_TK`

## DRC 指令飞行说明

- 协议：`drc_enter`/`drc_exit`（services）→ 进入后周期向 `thing/product/{sn}/drc/down` 发心跳（开发默认 1 秒，真机按官方约 500ms 调整）；`drc_flyto`、`takeoff_to_point`（services）；摇杆 `drone_control`（x/y/z 轴量 -1024~1024，fire-and-forget）
- `drc/up` 上行通道已订阅（HMS 推送已解析为告警；ack 消息仅日志）
- 一键起飞缺省取设备当前定位作为起飞目标
- 真机联调待验证点：摇杆轴量与方向的映射关系、心跳周期、drc/up 的 ack 处理
- 直播能力（live_capacity_update 事件）已解析存储，开播时自动取设备上报的 camera_index

## 消息模型对齐（与 DJI 官方）

来源（字段级依据）：
- 官方 Java SDK：https://github.com/dji-sdk/DJI-Cloud-API-Demo （cloud-sdk 模块，OsdDock / OsdDockDrone / OsdRcDrone / DeviceEnum）
- 官方文档：https://github.com/dji-sdk/Cloud-API-Doc （docs/cn/70.server-api-reference/10.mqtt/）

已对齐的要点：

1. **OSD 载荷为扁平结构**：`thing/product/{sn}/osd` 的 `data` 直接是设备属性（无人机属性平铺，负载相机以 `type-subType-gimbalIndex` 如 `43-0-0` 平铺在顶层），信封层另有 `gateway` 字段标注控制源 SN。
2. **typed DTO**：`backend/.../cloudapi/device/` 下 `OsdDock`、`OsdDrone`（合并 OsdDockDrone/OsdRcDrone 公共字段）、`DroneBattery`、`PositionState`，snake_case 与官方一致，未知字段忽略。
3. **设备型号**：`DeviceModelEnum` 维护 `domain-type-subType`（如 `3-2-0`→DJI Dock 2、`0-91-1`→Matrice 3TD）与显示名映射。
4. **上下线推送**：`sys/product/{sn}/status` 的 `data.online / data.offline`（含 sn、model、parent、child[] 拓扑），`DeviceStatusHandler` 解析后入册并同步子设备。
5. **mode_code 中文标签**：`ModeCodeLabels`（无人机 0~14 官方枚举）。
6. **安全告警事件**：`events` 的 `device_hms` / `device_exit_hms`（data.hms 数组，code/level/module/key，官方 level 0~3）与 DRC 上行 `hms` 推送解析为告警；OSD 派生低电量（battery.capacity_percent 下穿 30/15）与 RTK 断连（position_state.rtk_number/is_fixed 边沿）。告警统一落 `device_alert` 表并经 WS 推送（type=device_alert），查询接口 `GET /api/v1/alerts?sn=&level=&limit=`。

模拟验证（mosquitto_pub 示例）：

```bash
# 机场上线（官方格式）
mosquitto_pub -h localhost -t "sys/product/7CTDM3B00A0001/status" -m '{"tid":"t1","bid":"b1","timestamp":1728259200000,"data":{"online":{"sn":"7CTDM3B00A0001","model":"3-2-0","child":[{"sn":"1581F5BHD232Q00A85YB","model":"0-91-1"}]}}}'
# 机场 OSD
mosquitto_pub -h localhost -t "thing/product/7CTDM3B00A0001/osd" -m '{"tid":"t2","bid":"b2","timestamp":1728259200001,"data":{"mode_code":0,"latitude":31.0203,"longitude":121.4402,"cover_state":0,"drone_in_dock":true,"job_number":128}}'
# 无人机 OSD
mosquitto_pub -h localhost -t "thing/product/1581F5BHD232Q00A85YB/osd" -m '{"tid":"t3","bid":"b3","timestamp":1728259200002,"gateway":"7CTDM3B00A0001","data":{"mode_code":5,"latitude":31.0215,"longitude":121.4415,"height":120.5,"horizontal_speed":8.5,"battery":{"capacity_percent":82}}}'

# HMS 健康告警事件（官方格式，平台转告警并推送前端）
mosquitto_pub -h localhost -t "thing/product/1581F5BHD232Q00A85YB/events" -m '{"tid":"t4","bid":"b4","timestamp":1728259200003,"method":"device_hms","data":{"hms":[{"code":"0x16100086","created_at":1728259200003,"host":"DRONE","level":2,"module":"battery","key":"hms_0x16100086_bat_overheat"}]}}'
```

## 地图瓦片

前端默认使用 OpenStreetMap 瓦片（`frontend/src/components/DeviceMap.vue` 的 `TILE_URL`）。国内环境建议替换为：
- 高德矢量瓦片（无需 key）
- 天地图（需申请 key）

## 生产部署注意事项

- 更换 JWT secret、MySQL/Redis/MinIO/EMQX 全部默认口令；
- EMQX 启用认证与 ACL（按设备 SN 隔离 Topic），关闭匿名访问；
- 全站 HTTPS（DJI Pilot 2 接入三方平台的硬性要求：可信证书）；
- WebSocket 端点收紧 AllowedOrigins 并在握手时校验登录态。
