package com.feiji.cloud.control;

import com.feiji.cloud.common.exception.BusinessException;
import com.feiji.cloud.device.DeviceRegistryService;
import com.feiji.cloud.mqtt.ServicesReplyListener;
import com.feiji.cloud.vendor.spi.CommandRouter;
import com.feiji.cloud.vendor.spi.VendorAdapter;
import com.feiji.cloud.ws.WsPushService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * DRC 指令飞行（控制面）：
 * - drc_enter / drc_exit（services，应答后切换状态）
 * - 进入后按周期向 thing/product/{sn}/drc/down 发心跳（真机按官方要求约 500ms，开发默认 1s）
 * - drc_flyto（指点飞行）、takeoff_to_point（一键起飞）
 * - drone_control（摇杆轴量 x/y/z，-1024~1024）经 drc/down 下发，fire-and-forget
 * drc/up 通道的应答解析留待真机联调（已订阅，仅日志）。
 */
@Service
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DrcService implements ServicesReplyListener {

    private static final Logger log = LoggerFactory.getLogger(DrcService.class);

    private final CommandRouter commandRouter;
    private final WsPushService wsPushService;
    private final DeviceRegistryService registry;
    private final ObjectMapper objectMapper;

    private final Map<String, Boolean> active = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> heartbeats = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newScheduledThreadPool(1, runnable -> {
                Thread thread = new Thread(runnable, "drc-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

    public DrcService(CommandRouter commandRouter, WsPushService wsPushService,
                      DeviceRegistryService registry, ObjectMapper objectMapper) {
        this.commandRouter = commandRouter;
        this.wsPushService = wsPushService;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    public void enter(String sn) {
        commandRouter.route(sn).drcEnter(sn);
    }

    public void exit(String sn) {
        commandRouter.route(sn).drcExit(sn);
    }

    public void flyTo(String sn, double lat, double lng, double height, double safetyHeight) {
        requireActive(sn);
        commandRouter.route(sn).flyTo(new VendorAdapter.FlyToCommand(sn, lat, lng, height, safetyHeight));
    }

    public void takeOffToPoint(String sn, Double lat, Double lng, double height, double safetyHeight) {
        if (lat == null || lng == null) {
            double[] pos = currentPosition(sn);
            if (pos == null) {
                throw new BusinessException("设备无当前定位，无法一键起飞");
            }
            lat = pos[0];
            lng = pos[1];
        }
        commandRouter.route(sn).takeOff(new VendorAdapter.TakeOffCommand(sn, lat, lng, height, safetyHeight));
    }

    public void droneControl(String sn, int x, int y, int z) {
        requireActive(sn);
        commandRouter.route(sn).joystick(new VendorAdapter.JoystickCommand(sn, x, y, z));
    }

    public List<String> activeList() {
        return active.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .toList();
    }

    public boolean isActive(String sn) {
        return Boolean.TRUE.equals(active.get(sn));
    }

    // ---------------- services 应答 ----------------

    @Override
    public void onServicesReply(String sn, String method, String bid, JsonNode data) {
        int result = data.path("result").asInt(-1);
        switch (method) {
            case "drc_enter" -> {
                if (result == 0) {
                    active.put(sn, true);
                    startHeartbeat(sn);
                    wsPushService.push("drc_update", Map.of("sn", sn, "active", true));
                    log.info("DRC 已进入: {}", sn);
                } else {
                    log.warn("DRC 进入失败: sn={}, result={}", sn, result);
                }
            }
            case "drc_exit" -> {
                stopHeartbeat(sn);
                active.put(sn, false);
                wsPushService.push("drc_update", Map.of("sn", sn, "active", false));
                log.info("DRC 已退出: {}", sn);
            }
            case "drc_flyto", "takeoff_to_point" ->
                log.info("{} 应答: sn={}, result={}", method, sn, result);
            default -> {
            }
        }
    }

    // ---------------- 内部 ----------------

    private void startHeartbeat(String sn) {
        stopHeartbeat(sn);
        ScheduledFuture<?> future = heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                commandRouter.route(sn).heartbeat(sn);
            } catch (Exception e) {
                log.debug("DRC 心跳发送失败, sn={}: {}", sn, e.getMessage());
            }
        }, 1, 1, TimeUnit.SECONDS);
        heartbeats.put(sn, future);
    }

    private void stopHeartbeat(String sn) {
        ScheduledFuture<?> future = heartbeats.remove(sn);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void requireActive(String sn) {
        if (!isActive(sn)) {
            throw new BusinessException("设备未进入 DRC 模式，请先进入指令飞行");
        }
    }

    private double[] currentPosition(String sn) {
        try {
            String osd = registry.latestOsd(sn);
            if (osd == null) {
                return null;
            }
            JsonNode node = objectMapper.readTree(osd);
            JsonNode lat = node.path("latitude");
            JsonNode lng = node.path("longitude");
            if (lat.isMissingNode() || lng.isMissingNode()) {
                return null;
            }
            return new double[]{lat.asDouble(), lng.asDouble()};
        } catch (Exception e) {
            return null;
        }
    }
}
