package com.feiji.cloud.alert;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 设备安全告警：HMS 健康告警事件、DRC 上行 HMS、OSD 派生的低电量/RTK 断连。
 * category: HMS/BATTERY/RTK；level: NOTICE/WARN/CRITICAL；payload 为原始上报 JSON。
 */
@TableName("device_alert")
public class DeviceAlertEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sn;
    private String category;
    private String level;
    private String eventKey;
    private String message;
    private String payload;
    private Long occurredAt;
    private Long workspaceId;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSn() { return sn; }
    public void setSn(String sn) { this.sn = sn; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Long getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Long occurredAt) { this.occurredAt = occurredAt; }
    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
