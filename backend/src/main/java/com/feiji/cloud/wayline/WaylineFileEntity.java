package com.feiji.cloud.wayline;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("wayline_file")
public class WaylineFileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String fileKey;
    private Long sizeBytes;
    private String droneModelKey;
    private String droneModelName;
    private String payloadModelKey;
    private String payloadModelName;
    private String templateType;
    private Integer waylineCount;
    private Integer waypointCount;
    private Long workspaceId;
    private String creator;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFileKey() { return fileKey; }
    public void setFileKey(String fileKey) { this.fileKey = fileKey; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getDroneModelKey() { return droneModelKey; }
    public void setDroneModelKey(String droneModelKey) { this.droneModelKey = droneModelKey; }
    public String getDroneModelName() { return droneModelName; }
    public void setDroneModelName(String droneModelName) { this.droneModelName = droneModelName; }
    public String getPayloadModelKey() { return payloadModelKey; }
    public void setPayloadModelKey(String payloadModelKey) { this.payloadModelKey = payloadModelKey; }
    public String getPayloadModelName() { return payloadModelName; }
    public void setPayloadModelName(String payloadModelName) { this.payloadModelName = payloadModelName; }
    public String getTemplateType() { return templateType; }
    public void setTemplateType(String templateType) { this.templateType = templateType; }
    public Integer getWaylineCount() { return waylineCount; }
    public void setWaylineCount(Integer waylineCount) { this.waylineCount = waylineCount; }
    public Integer getWaypointCount() { return waypointCount; }
    public void setWaypointCount(Integer waypointCount) { this.waypointCount = waypointCount; }
    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }
    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
