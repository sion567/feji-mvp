package com.feiji.cloud.device;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.feiji.cloud.cloudapi.device.DeviceModelEnum;
import com.feiji.cloud.common.Vendors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备档案（MySQL）：持久化设备型号/类型/拓扑/昵称，来自上下线推送与 OSD 识别。
 * 数据库不可用时写路径静默降级（仅告警），读路径由调用方兜底。
 */
@Service
public class DeviceArchiveService {

    private static final Logger log = LoggerFactory.getLogger(DeviceArchiveService.class);

    private static final String INDB_FLAG_PREFIX = "feiji:device:indb:";

    private final DeviceMapper deviceMapper;
    private final StringRedisTemplate redis;

    public DeviceArchiveService(DeviceMapper deviceMapper, StringRedisTemplate redis) {
        this.deviceMapper = deviceMapper;
        this.redis = redis;
    }

    /** OSD 高频路径用：通过 Redis 标志节流，每设备每小时最多回源一次 */
    public void ensureDevice(String sn, String vendor, String modelKey, String modelName, String kind, String parentSn) {
        try {
            Boolean first = redis.opsForValue().setIfAbsent(INDB_FLAG_PREFIX + sn, "1", Duration.ofHours(1));
            if (Boolean.TRUE.equals(first)) {
                upsert(sn, vendor, modelKey, modelName, kind, parentSn);
            }
        } catch (Exception e) {
            log.debug("设备档案 ensure 跳过, sn={}: {}", sn, e.getMessage());
        }
    }

    /** 上下线路径用：直接落库（拓扑/型号变化时必须即时更新） */
    public void upsert(String sn, String vendor, String modelKey, String modelName, String kind, String parentSn) {
        try {
            DeviceEntity existing = deviceMapper.selectById(sn);
            if (existing == null) {
                DeviceEntity entity = new DeviceEntity();
                entity.setSn(sn);
                entity.setVendor(vendor);
                entity.setModelKey(modelKey);
                entity.setModelName(modelName);
                entity.setKind(kind);
                entity.setParentSn(parentSn);
                entity.setWorkspaceId(1L);
                entity.setCreatedAt(LocalDateTime.now());
                entity.setUpdatedAt(LocalDateTime.now());
                deviceMapper.insert(entity);
            } else {
                if (vendor != null) {
                    existing.setVendor(vendor);
                }
                if (modelKey != null) {
                    existing.setModelKey(modelKey);
                    existing.setModelName(modelName);
                }
                if (kind != null) {
                    existing.setKind(kind);
                }
                if (parentSn != null) {
                    existing.setParentSn(parentSn);
                }
                existing.setUpdatedAt(LocalDateTime.now());
                deviceMapper.updateById(existing);
            }
        } catch (Exception e) {
            log.warn("设备档案更新失败, sn={}: {}", sn, e.getMessage());
        }
    }

    public List<DeviceEntity> listByWorkspace(Long workspaceId) {
        return deviceMapper.selectList(new QueryWrapper<DeviceEntity>()
                .eq("workspace_id", workspaceId)
                .orderByDesc("updated_at"));
    }

    public void rename(String sn, String deviceName) {
        DeviceEntity existing = deviceMapper.selectById(sn);
        if (existing == null) {
            existing = new DeviceEntity();
            existing.setSn(sn);
            existing.setWorkspaceId(1L);
            existing.setCreatedAt(LocalDateTime.now());
        }
        existing.setDeviceName(deviceName);
        existing.setUpdatedAt(LocalDateTime.now());
        if (existing.getCreatedAt() == null) {
            deviceMapper.insert(existing);
        } else {
            deviceMapper.updateById(existing);
        }
    }

    public void unbind(String sn) {
        deviceMapper.deleteById(sn);
    }

    /** 设备厂商；无档案或查询失败时回退 DJI（当前接入管线的默认厂商） */
    public String vendorOf(String sn) {
        try {
            DeviceEntity entity = deviceMapper.selectById(sn);
            return entity == null || entity.getVendor() == null ? Vendors.DJI : entity.getVendor();
        } catch (Exception e) {
            return Vendors.DJI;
        }
    }

    /** 设备所属工作空间；无档案时回退默认工作空间 1 */
    public Long workspaceOf(String sn) {
        try {
            DeviceEntity entity = deviceMapper.selectById(sn);
            return entity == null || entity.getWorkspaceId() == null ? 1L : entity.getWorkspaceId();
        } catch (Exception e) {
            return 1L;
        }
    }
}
