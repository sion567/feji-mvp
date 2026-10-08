package com.feiji.cloud.plot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 地块（作业区域/农田/园区等），coordinates 为多边形 JSON 数组 [[lng,lat],...]（WGS-84）。
 */
@TableName("plot")
public class PlotEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;
    private String category;
    private String crop;
    private Double areaMu;
    private String description;
    private String coordinates;
    private String color;
    private Long workspaceId;
    private String creator;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getCrop() { return crop; }
    public void setCrop(String crop) { this.crop = crop; }
    public Double getAreaMu() { return areaMu; }
    public void setAreaMu(Double areaMu) { this.areaMu = areaMu; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCoordinates() { return coordinates; }
    public void setCoordinates(String coordinates) { this.coordinates = coordinates; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }
    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
