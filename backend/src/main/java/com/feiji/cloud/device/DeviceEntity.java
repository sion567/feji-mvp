package com.feiji.cloud.device;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("device")
public class DeviceEntity {

    @TableId
    private String sn;
    private String vendor;
    private String deviceName;
    private String modelKey;
    private String modelName;
    private String kind;
    private String parentSn;
    private Long workspaceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getSn() { return sn; }
    public void setSn(String sn) { this.sn = sn; }
    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getModelKey() { return modelKey; }
    public void setModelKey(String modelKey) { this.modelKey = modelKey; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getParentSn() { return parentSn; }
    public void setParentSn(String parentSn) { this.parentSn = parentSn; }
    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
