package com.feiji.cloud.cloudapi.device;

import java.util.Map;

/**
 * mode_code 值的中文标签（来源：官方文档物模型，用于展示层）。
 * 无人机 mode_code：0 待机 / 1 起飞准备 / 2 起飞准备完成 / 3 手动飞行 / 4 自动起飞 / 5 航线飞行 /
 * 6 全景拍照 / 7 智能跟随 / 8 ADS-B 避让 / 9 自动返航 / 10 自动降落 / 11 迫降 / 12 三桨降落 / 13 升级中 / 14 未连接。
 */
public final class ModeCodeLabels {

    private static final Map<Integer, String> DRONE = Map.ofEntries(
            Map.entry(0, "待机"),
            Map.entry(1, "起飞准备"),
            Map.entry(2, "起飞准备完成"),
            Map.entry(3, "手动飞行"),
            Map.entry(4, "自动起飞"),
            Map.entry(5, "航线飞行"),
            Map.entry(6, "全景拍照"),
            Map.entry(7, "智能跟随"),
            Map.entry(8, "ADS-B 避让"),
            Map.entry(9, "自动返航"),
            Map.entry(10, "自动降落"),
            Map.entry(11, "迫降"),
            Map.entry(12, "三桨降落"),
            Map.entry(13, "升级中"),
            Map.entry(14, "未连接"));

    private static final Map<Integer, String> DOCK = Map.of(
            0, "待机",
            1, "作业中");

    private ModeCodeLabels() {
    }

    public static String drone(Integer code) {
        return label(DRONE, code);
    }

    public static String dock(Integer code) {
        return label(DOCK, code);
    }

    private static String label(Map<Integer, String> table, Integer code) {
        if (code == null) {
            return "-";
        }
        return table.getOrDefault(code, "未知(" + code + ")");
    }
}
