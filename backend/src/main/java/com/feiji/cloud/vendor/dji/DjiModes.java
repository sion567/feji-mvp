package com.feiji.cloud.vendor.dji;

/**
 * DJI mode_code → 平台统一飞行状态枚举（字符串）。
 * 平台枚举：IDLE/PREPARING/FLYING/TAKEOFF/MISSION/RTH/LANDING/UPGRADING/DISCONNECTED/WORKING/UNKNOWN
 */
public final class DjiModes {

    private DjiModes() {
    }

    public static String droneMode(Integer code) {
        if (code == null) {
            return "UNKNOWN";
        }
        return switch (code) {
            case 0 -> "IDLE";
            case 1, 2 -> "PREPARING";
            case 3, 7, 8 -> "FLYING";
            case 4 -> "TAKEOFF";
            case 5, 6 -> "MISSION";
            case 9 -> "RTH";
            case 10, 11, 12 -> "LANDING";
            case 13 -> "UPGRADING";
            case 14 -> "DISCONNECTED";
            default -> "UNKNOWN";
        };
    }

    public static String dockMode(Integer code) {
        if (code == null) {
            return "UNKNOWN";
        }
        return switch (code) {
            case 0 -> "IDLE";
            case 1 -> "WORKING";
            default -> "UNKNOWN";
        };
    }
}
