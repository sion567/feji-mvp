package com.feiji.cloud.vendor.spi;

/**
 * 设备厂商适配器 SPI（Phase 1）。
 * 平台核心通过本接口下发指令，不感知厂商协议；每接入一家厂商新增一个实现。
 * 上行事件（遥测/状态/进度）的翻译仍在各厂商 handler 中，Phase 2 收敛进适配器。
 */
public interface VendorAdapter {

    String vendor();

    boolean supports(String vendor);

    /** 能力声明：UI 与业务据此降级（不支持的指令不展示/不下发） */
    DeviceCapabilities capabilities();

    // ---- 直播 ----

    /** 下发开播指令，返回 bid 供应答配对 */
    String startLive(LiveStartCommand cmd);

    String stopLive(LiveStopCommand cmd);

    // ---- 航线任务 ----

    String prepareMission(PrepareMissionCommand cmd);

    String executeMission(String sn, String flightId);

    void cancelMission(String sn, String bid, String flightId);

    // ---- DRC 指令飞行 ----

    void drcEnter(String sn);

    void drcExit(String sn);

    void flyTo(FlyToCommand cmd);

    /** 一键起飞（lat/lng 由业务层解析缺省值后必传） */
    void takeOff(TakeOffCommand cmd);

    void joystick(JoystickCommand cmd);

    /** DRC 模式下的周期保活心跳（进入 DRC 后由平台定时调用） */
    void heartbeat(String sn);

    // ---- 统一命令对象（厂商无关） ----

    record LiveStartCommand(String gatewaySn, String videoId, String pushUrl, Integer quality) {
    }

    record LiveStopCommand(String gatewaySn, String bid, String videoId) {
    }

    /** immediate=true 时 task_type=0 且 execute_time 为当前时间；否则 task_type=1 按计划时间 */
    record PrepareMissionCommand(String sn, String flightId, long executeTime, boolean immediate,
                                 String fileUrl, String fingerprint, int rthAltitude) {
    }

    record FlyToCommand(String sn, double lat, double lng, double height, double safetyHeight) {
    }

    record TakeOffCommand(String sn, double lat, double lng, double height, double safetyHeight) {
    }

    record JoystickCommand(String sn, int x, int y, int z) {
    }
}
