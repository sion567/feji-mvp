package com.feiji.cloud.wayline;

import com.feiji.cloud.auth.LoginUser;
import com.feiji.cloud.cloudapi.wayline.WaylineKmzWriter;
import com.feiji.cloud.cloudapi.wayline.model.MissionConfig;
import com.feiji.cloud.cloudapi.wayline.model.Wayline;
import com.feiji.cloud.cloudapi.wayline.model.WaylineFile;
import com.feiji.cloud.cloudapi.wayline.model.WaylineTemplate;
import com.feiji.cloud.cloudapi.wayline.model.Waypoint;
import com.feiji.cloud.cloudapi.wayline.model.WaypointAction;
import com.feiji.cloud.cloudapi.wayline.model.WaypointActionGroup;
import com.feiji.cloud.cloudapi.device.DeviceModelEnum;
import com.feiji.cloud.common.exception.BusinessException;
import com.feiji.cloud.device.DeviceRegistryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 选点拍照航线编排：前端选点 → RouteOptimizer 点序优化 → WaylineKmzWriter 生成 KMZ → 航线库入库。
 * 后续派发复用现有 WaylineJobService 链路。参数预设与流程见根目录《路径算法设计.md》。
 */
@Service
public class RouteComposeService {

    private static final Logger log = LoggerFactory.getLogger(RouteComposeService.class);

    /** 单航线航点上限（防误传巨量点位撑爆生成器） */
    public static final int MAX_WAYPOINTS = 200;
    /** 默认统一飞行高度（米），与 DRC flyto 默认安全高度一致 */
    public static final double DEFAULT_HEIGHT = 80;
    /** 默认航线速度（m/s） */
    public static final double DEFAULT_SPEED = 8;
    /** 起飞安全高度（米） */
    public static final double TAKEOFF_SECURITY_HEIGHT = 80;
    /** 默认机型（M3TD），设备注册表无机型记录时兜底 */
    private static final String DEFAULT_DRONE_KEY = "0-91-1";
    /** M3TD 默认负载（广角相机），与官方样例一致 */
    private static final String DEFAULT_PAYLOAD_KEY = "1-93-0";

    private final DeviceRegistryService registry;
    private final WaylineKmzWriter writer;
    private final WaylineLibraryService library;
    private final ObjectMapper objectMapper;

    public RouteComposeService(DeviceRegistryService registry, WaylineKmzWriter writer,
                               WaylineLibraryService library, ObjectMapper objectMapper) {
        this.registry = registry;
        this.writer = writer;
        this.library = library;
        this.objectMapper = objectMapper;
    }

    // ---------------- 请求/响应（web 校验注解随 DTO，供控制器 @Valid 直用） ----------------

    public record PointInput(
            @NotNull(message = "纬度不能为空") @Min(-90) @Max(90) Double lat,
            @NotNull(message = "经度不能为空") @Min(-180) @Max(180) Double lng) {
    }

    public record PlanRequest(
            @NotBlank(message = "设备 SN 不能为空") String sn,
            @NotEmpty(message = "请先选择航点") @Valid List<PointInput> points,
            @Min(value = 5, message = "飞行高度需在 5~1500 米之间")
            @Max(value = 1500, message = "飞行高度需在 5~1500 米之间") Double height) {
    }

    public record ComposeRequest(
            @NotBlank(message = "设备 SN 不能为空") String sn,
            @NotBlank(message = "航线名称不能为空") String name,
            @NotEmpty(message = "请先选择航点") @Valid List<PointInput> points,
            @Min(value = 5, message = "飞行高度需在 5~1500 米之间")
            @Max(value = 1500, message = "飞行高度需在 5~1500 米之间") Double height,
            @Min(value = 1, message = "航线速度需在 1~15 m/s 之间")
            @Max(value = 15, message = "航线速度需在 1~15 m/s 之间") Double speed) {
    }

    /** 排序后的点：sourceIndex 为前端选点原始下标，便于回显对应关系 */
    public record PointView(double lat, double lng, int sourceIndex) {
    }

    public record PlanResponse(List<PointView> orderedPoints, double totalDistanceMeters,
                               String strategy) {
    }

    // ---------------- 能力 ----------------

    /** 排序预览：起点 = 设备当前 OSD 定位，返回优化后的点序与总距离 */
    public PlanResponse plan(PlanRequest request) {
        OrderPlan plan = buildOrder(request.sn(), request.points(), request.height());
        List<PointView> views = new ArrayList<>();
        for (int i = 0; i < plan.orderedInputs().size(); i++) {
            PointInput input = plan.orderedInputs().get(i);
            views.add(new PointView(input.lat(), input.lng(), plan.result().order().get(i)));
        }
        return new PlanResponse(views, round2(plan.result().totalDistanceMeters()),
                plan.result().strategy().name());
    }

    /** 生成 KMZ 入航线库（返回航线文件实体，后续走现有任务下发） */
    public WaylineFileEntity compose(ComposeRequest request, LoginUser user) {
        OrderPlan plan = buildOrder(request.sn(), request.points(), request.height());
        double height = plan.height();
        double speed = request.speed() == null ? DEFAULT_SPEED : request.speed();

        WaylineFile model = buildModel(request.sn(), request.name(),
                plan.orderedInputs(), height, speed);
        byte[] kmz = writer.write(model);
        WaylineFileEntity entity = library.upload(kmz, request.name(), user);
        log.info("选点航线已生成: name={}, waypoints={}, distance={}m, strategy={}, fileId={}",
                request.name(), plan.orderedInputs().size(),
                plan.result().totalDistanceMeters(), plan.result().strategy(), entity.getId());
        return entity;
    }

    // ---------------- 内部 ----------------

    private record OrderPlan(List<PointInput> orderedInputs, RouteOptimizer.Result result,
                             double height) {
    }

    private OrderPlan buildOrder(String sn, List<PointInput> points, Double height) {
        if (points == null || points.isEmpty()) {
            throw new BusinessException("请先在地图上选择航点");
        }
        if (points.size() > MAX_WAYPOINTS) {
            throw new BusinessException("航点数量超过上限 " + MAX_WAYPOINTS);
        }
        for (PointInput point : points) {
            if (point.lat() == null || point.lng() == null) {
                throw new BusinessException("航点坐标不完整");
            }
        }
        double flyHeight = height == null ? DEFAULT_HEIGHT : height;
        if (flyHeight < 5 || flyHeight > 1500) {
            throw new BusinessException("飞行高度需在 5~1500 米之间");
        }

        double[] start = startPosition(sn);
        List<RouteOptimizer.Point> optimizerPoints = new ArrayList<>();
        for (PointInput point : points) {
            optimizerPoints.add(new RouteOptimizer.Point(point.lat(), point.lng(), flyHeight));
        }
        // 起点高度取目标高度：选点为统一高度，段间高差为 0，距离即水平 haversine
        RouteOptimizer.Result result = RouteOptimizer.optimize(
                new RouteOptimizer.Point(start[0], start[1], flyHeight), optimizerPoints);

        List<PointInput> ordered = new ArrayList<>();
        for (int index : result.order()) {
            ordered.add(points.get(index));
        }
        return new OrderPlan(ordered, result, flyHeight);
    }

    /** 设备当前定位（与 DRC takeoff 同源 latestOsd），取不到即明确报错 */
    private double[] startPosition(String sn) {
        String osd = registry.latestOsd(sn);
        if (osd == null) {
            throw new BusinessException("设备无当前定位，无法规划航线");
        }
        try {
            JsonNode node = objectMapper.readTree(osd);
            JsonNode lat = node.path("latitude");
            JsonNode lng = node.path("longitude");
            if (lat.isMissingNode() || lng.isMissingNode()) {
                throw new BusinessException("设备定位数据不完整，无法规划航线");
            }
            return new double[]{lat.asDouble(), lng.asDouble()};
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("设备定位数据解析失败，无法规划航线");
        }
    }

    /** 内部模型组装：参数预设见《路径算法设计.md》§4.3 */
    private WaylineFile buildModel(String sn, String name, List<PointInput> orderedPoints,
                                   double height, double speed) {
        // 机型取设备注册表（在线设备必有），无记录兜底 M3TD；负载固定 M3TD 相机
        String droneModelKey = DEFAULT_DRONE_KEY;
        DeviceRegistryService.DeviceEntry entry = registry.entry(sn);
        if (entry != null && entry.model() != null) {
            droneModelKey = entry.model();
        }
        long now = System.currentTimeMillis();

        List<Waypoint> waypoints = new ArrayList<>();
        for (int i = 0; i < orderedPoints.size(); i++) {
            waypoints.add(photoWaypoint(i, orderedPoints.get(i), height, speed));
        }
        return new WaylineFile(
                name,
                "feiji-platform",
                now,
                now,
                droneModelKey,
                DeviceModelEnum.labelOf(droneModelKey),
                DEFAULT_PAYLOAD_KEY,
                null,
                new MissionConfig("safely", "goHome", "executeLostAction", "goBack",
                        TAKEOFF_SECURITY_HEIGHT, 10.0),
                List.of(new WaylineTemplate(0, "waypoint", speed, "WGS84",
                        "relativeToStartPoint", null, null)),
                List.of(new Wayline(0, 0, speed, "relativeToStartPoint", waypoints)));
    }

    /** 拍照航点：到点停 + 机头沿航线 + 云台俯拍 + 到点触发「压云台→拍照」动作组 */
    private Waypoint photoWaypoint(int index, PointInput point, double height, double speed) {
        Map<String, String> gimbalParams = new LinkedHashMap<>();
        gimbalParams.put("gimbalRotateMode", "absoluteAnglePosition");
        gimbalParams.put("gimbalPitchRotateEnable", "1");
        gimbalParams.put("gimbalPitchRotateAngle", "-90");
        Map<String, String> photoParams = new LinkedHashMap<>();
        photoParams.put("payloadPositionIndex", "0");
        photoParams.put("fileSuffix", "point_" + index);

        WaypointActionGroup group = new WaypointActionGroup(index, index, index, "sequence",
                "reachPoint", null,
                List.of(new WaypointAction(0, "gimbalRotate", gimbalParams),
                        new WaypointAction(1, "takePhoto", photoParams)));
        return new Waypoint(index, point.lng(), point.lat(), height, null, height,
                speed, "toPointAndStopWithDiscontinuityCurvature", 0.0, "followWayline",
                -90.0, List.of(group));
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
