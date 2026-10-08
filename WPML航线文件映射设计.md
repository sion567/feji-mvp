# WPML 航线文件映射设计

> 版本：v0.1
> 日期：2026-10-07
> 规范依据：[WPML 总览](https://developer.dji.com/doc/cloud-api-tutorial/cn/api-reference/dji-wpml/overview.html)、[template.kml](https://developer.dji.com/doc/cloud-api-tutorial/cn/api-reference/dji-wpml/template-kml.html)、[waylines.wpml](https://developer.dji.com/doc/cloud-api-tutorial/cn/api-reference/dji-wpml/waylines-wpml.html)（同源文档：github.com/dji-sdk/Cloud-API-Doc `docs/cn/30.specification/80.dji-wpml/`）
> 代码位置：`backend/src/main/java/com/feiji/cloud/cloudapi/wayline/`

---

## 1. WPML 文件是什么

无人机航线的物理载体是 **KMZ 文件**（本质是 ZIP 包），内部结构：

```
巡检航线.kmz（ZIP）
└── wpmz/
    ├── template.kml      模板文件：业务属性，用户可编辑（Pilot 里画航线生成的是它）
    ├── waylines.wpml     执行文件：机器执行指令（由模板演算而来，精度更高）
    └── res/              辅助资源（倾斜摄影等场景）
```

- `template.kml` = 创建信息 + 任务信息(missionConfig) + 模板信息(Folder)
- `waylines.wpml` = 任务信息(missionConfig) + 航线信息(Folder，可多条)
- **航线名称 = KMZ 文件名**
- 上云API 的航线管理（下发任务、对象存储）直接使用 KMZ 原文件

## 2. 设计思路：内部统一模型

不直接用 WPML XML 作为业务数据结构，而是解析为**平台自己的中立模型**（JSON 友好的 record），三方复用：

```
KMZ 原文件 ──WaylineKmzParser──► 内部统一模型 ──┬──► 前端地图预览（航点/动作标记）
（MinIO 存储）                                  ├──► 任务管理（校验、下发、进度关联）
                                               └──► 在线编辑器（反向生成 WPML，远期）
```

内部模型只保留业务主干，动作参数用字符串 Map 全量保真——保证不丢信息，又不为几十种动作参数各建类。

## 3. 内部模型定义

```
WaylineFile                      航线文件（= 一个 KMZ）
├── name / author / createTime / updateTime
├── droneModelKey / droneModelName      机型 "0-91-1" → "Matrice 3TD"（复用 DeviceModelEnum）
├── payloadModelKey / payloadModelName  负载 "1-93-0"
├── missionConfig                任务全局配置
├── templates: WaylineTemplate[] 模板（template.kml 的 Folder）
└── waylines: Wayline[]          可执行航线（waylines.wpml 的 Folder）
      ├── waylineId / templateId / autoFlightSpeed / executeHeightMode
      └── waypoints: Waypoint[]
            ├── index / longitude / latitude / height / ellipsoidHeight / executeHeight
            ├── speed / turnMode / turnDampingDist / headingMode / gimbalPitchAngle
            └── actionGroups: WaypointActionGroup[]
                  ├── groupId / startIndex / endIndex / mode / triggerType / triggerParam
                  └── actions: WaypointAction[] { actionId, func, params:Map }
```

## 4. 映射总表

### 4.1 文件级（template.kml / waylines.wpml → WaylineFile）

| WPML 元素 | 内部字段 | 说明 |
|---|---|---|
| KMZ 文件名 | name | 官方约定：航线名 = KMZ 文件名 |
| wpml:author | author | |
| wpml:createTime / updateTime | createTime / updateTime | 毫秒时间戳 |
| wpml:missionConfig/wpml:droneInfo/{droneEnumValue, droneSubEnumValue} | droneModelKey / droneModelName | 拼为 `0-type-subType`，查 DeviceModelEnum 得显示名 |
| wpml:missionConfig/wpml:payloadInfo/{payloadEnumValue, payloadSubEnumValue} | payloadModelKey / payloadModelName | 拼为 `1-type-subType`（domain 1=负载） |

### 4.2 任务级 wpml:missionConfig → MissionConfig

| WPML 元素 | 内部字段 | 取值（官方枚举） |
|---|---|---|
| flyToWaylineMode | flyToWaylineMode | safely（安全飞行，逐点 goTo） / point_to_point（直飞） |
| finishAction | finishAction | goHome / noAction / autoLand / gotoFirstWaypoint |
| exitOnRCLost | exitOnRCLost | executeLostAction / continueWayline |
| executeRCLostAction | executeRCLostAction | goBack / landing / hover |
| takeOffSecurityHeight | takeOffSecurityHeight | 安全起飞高度，m（部分文件写作 takeoffSecurityHeight，解析已兼容） |
| globalTransitionalSpeed | globalTransitionalSpeed | 全局过渡（切航线）速度，m/s |

### 4.3 模板 Folder（template.kml）→ WaylineTemplate

| WPML 元素 | 内部字段 | 说明 |
|---|---|---|
| wpml:templateId | templateId | 模板序号 |
| wpml:templateType | templateType | waypoint（航点飞行）/ mapping2d / mapping3d / mappingStrip（建图航拍） |
| wpml:autoFlightSpeed | autoFlightSpeed | m/s |
| wpml:waylineCoordinateSysParam/wpml:coordinateMode | coordinateMode | WGS84 |
| wpml:waylineCoordinateSysParam/wpml:heightMode | heightMode | relativeToStartPoint / ellipsoidHeight / aboveGroundLevel / realTimeFollowSurface |
| wpml:globalHeight / wpml:globalEllipsoidHeight | globalHeight / globalEllipsoidHeight | 全局高度，m（建图类模板用） |

### 4.4 航线 Folder（waylines.wpml）→ Wayline

| WPML 元素 | 内部字段 | 说明 |
|---|---|---|
| wpml:waylineId | waylineId | 航线序号 |
| wpml:templateId | templateId | 关联模板 |
| wpml:autoFlightSpeed | autoFlightSpeed | m/s |
| wpml:executeHeightMode | executeHeightMode | 同 heightMode 取值 |
| Folder/wpml:Placemark* | waypoints | 每个 Placemark 一个航点 |

### 4.5 航点 Placemark → Waypoint

| WPML 元素 | 内部字段 | 说明 |
|---|---|---|
| Point/wpml:coordinates | longitude, latitude | 度；KML 顺序为「经度,纬度[,高程]」 |
| wpml:index | index | 航点序号，0 起 |
| wpml:height | height | 基于起点相对高度，m |
| wpml:ellipsoidHeight | ellipsoidHeight | 椭球高，m |
| wpml:executeHeight | executeHeight | 实际执行高度，m |
| wpml:waypointSpeed | speed | m/s；useGlobalSpeed=1 时文件里可缺省，业务侧回退 Wayline.autoFlightSpeed |
| wpml:waypointTurnParam/wpml:waypointTurnMode | turnMode | 转弯模式（断/连续曲率，到点停/穿越/协调转弯，见官方文档取值表） |
| wpml:waypointTurnParam/wpml:waypointTurnDampingDist | turnDampingDist | 转弯阻尼距离，m |
| wpml:waypointHeadingParam/wpml:waypointHeadingMode | headingMode | followWayline（沿航线方向）/ manually / smoothTransition / towardPOI |
| wpml:gimbalPitchAngle | gimbalPitchAngle | 云台俯仰角，度 |
| wpml:ActionGroup* | actionGroups | 动作组，见 4.6 |

### 4.6 动作组与动作

| WPML 元素 | 内部字段 | 说明 |
|---|---|---|
| wpml:actionGroupId | groupId | |
| wpml:actionGroupStartIndex / EndIndex | startIndex / endIndex | 动作组作用的航点区间 |
| wpml:actionGroupMode | mode | sequence（顺序执行） |
| wpml:actionTrigger/wpml:actionTriggerType | triggerType | reachPoint（到达航点触发）/ multipleTiming（定时，秒）/ multipleDistance（定距，米） |
| wpml:actionTrigger/wpml:actionTriggerParam | triggerParam | 触发间隔值（秒或米，随 triggerType） |
| wpml:action/wpml:actionId | actionId | |
| wpml:action/wpml:actionActuatorFunc | func | takePhoto / startRecord / stopRecord / focus / zoom / customDirName / gimbalRotate / rotateYaw / hover |
| wpml:action/wpml:actionActuatorFuncParam/* | params（Map 全量保真） | 参数随 func 不同，键名保持 WPML 原文 |

## 5. 复用场景与落点

| 场景 | 阶段 | 用法 |
|---|---|---|
| 航线库 | P1 | KMZ 原文件存 MinIO；解析模型落 MySQL（`wayline_file` 表存元信息+JSON）。下发任务用对象存储 key，DJI 设备只认 KMZ 原文件 |
| 航线预览 | P1 | `GET /api/v1/waylines/{id}` 返回内部模型 JSON，前端地图画航点连线、动作图标 |
| 任务校验 | P1 | 机型匹配（droneModelKey vs 任务设备）、航点数/高度合法性 |
| 在线编辑器 | P2+ | 内部模型 → 反向生成 template.kml + waylines.wpml → 打包 KMZ（新增 WaylineKmzWriter，与解析器对称） |

## 6. 实现说明

- **解析器**：`WaylineKmzParser`（`cloudapi/wayline/` 包），JDK 内置 DOM 解析，按 **localName 匹配**——不受 wpml 命名空间版本（wpmz/1.0.x）与前缀写法影响；开启防 XXE（禁 DOCTYPE）
- **REST 接口**：`POST /api/v1/waylines/parse`（multipart 上传 KMZ）→ 返回内部模型 JSON 预览；解析失败返回 400 + 原因
- **机型映射复用**：droneModelKey/payloadModelKey 直接查 `DeviceModelEnum`（与设备管理同一份枚举）
- **测试**：`WaylineKmzParserTest` 用按规范构造的样例 KMZ（M3TD + 2 航点 + 拍照/云台动作）验证全链路映射，已通过

## 7. 暂不建模（留扩展点）

- mapping2d/3d/Strip 建图模板的特有参数（航向重叠率、旁向重叠率、盘旋参数、倾斜角度等）——需要建图业务时再按 4.3 表补字段
- `res/` 辅助资源文件
- template.kml → waylines.wpml 的演算逻辑（Pilot/司空生成的 KMZ 两文件都在，平台不需要自己演算）
