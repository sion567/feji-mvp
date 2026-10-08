package com.feiji.cloud.cloudapi.device;

/**
 * 设备型号枚举，key 为 domain-type-subType（对齐官方 SDK DeviceEnum 与
 * 文档 docs/cn/30.specification/70.product-type-enumerate.md）。
 * domain: 0=无人机, 1=负载, 2=遥控器, 3=机场。
 */
public enum DeviceModelEnum {

    M30("0-67-0", "Matrice 30"),
    M30T("0-67-1", "Matrice 30T"),
    M300_RTK("0-60-0", "Matrice 300 RTK"),
    M350_RTK("0-89-0", "Matrice 350 RTK"),
    M3E("0-77-0", "Matrice 3E"),
    M3T("0-77-1", "Matrice 3T"),
    M3M("0-77-2", "Matrice 3M"),
    M3D("0-91-0", "Matrice 3D"),
    M3TD("0-91-1", "Matrice 3TD"),
    DOCK("3-1-0", "DJI Dock"),
    DOCK_2("3-2-0", "DJI Dock 2"),
    DOCK_3("3-3-0", "DJI Dock 3"),
    RC_PLUS("2-57-0", "DJI RC Plus"),
    RC_PRO("2-58-0", "DJI RC Pro");

    private final String key;
    private final String label;

    DeviceModelEnum(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    /** model key 转显示名，未收录的 key 原样返回 */
    public static String labelOf(String key) {
        if (key == null) {
            return null;
        }
        for (DeviceModelEnum value : values()) {
            if (value.key.equals(key)) {
                return value.label;
            }
        }
        return key;
    }
}
