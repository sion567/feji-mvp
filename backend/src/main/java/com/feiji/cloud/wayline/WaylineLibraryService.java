package com.feiji.cloud.wayline;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.feiji.cloud.auth.LoginUser;
import com.feiji.cloud.cloudapi.wayline.model.WaylineFile;
import com.feiji.cloud.cloudapi.wayline.WaylineKmzParser;
import com.feiji.cloud.common.exception.BusinessException;
import com.feiji.cloud.config.AppProperties;
import com.feiji.cloud.wayline.storage.WaylineStorageService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 航线库：KMZ 上传 → 解析 → 存储（本地磁盘，二期可切 MinIO）→ 元数据落库。
 */
@Service
public class WaylineLibraryService {

    private final WaylineFileMapper fileMapper;
    private final WaylineStorageService storage;
    private final WaylineKmzParser parser;
    private final AppProperties appProperties;

    public WaylineLibraryService(WaylineFileMapper fileMapper, WaylineStorageService storage,
                                 WaylineKmzParser parser, AppProperties appProperties) {
        this.fileMapper = fileMapper;
        this.storage = storage;
        this.parser = parser;
        this.appProperties = appProperties;
    }

    public WaylineFileEntity upload(byte[] kmz, String name, LoginUser user) {
        WaylineFile parsed = parser.parse(kmz, name);
        String fileKey = storage.save(kmz);

        WaylineFileEntity entity = new WaylineFileEntity();
        entity.setName(name);
        entity.setFileKey(fileKey);
        entity.setSizeBytes((long) kmz.length);
        entity.setDroneModelKey(parsed.droneModelKey());
        entity.setDroneModelName(parsed.droneModelName());
        entity.setPayloadModelKey(parsed.payloadModelKey());
        entity.setPayloadModelName(parsed.payloadModelName());
        entity.setTemplateType(parsed.templates() != null && !parsed.templates().isEmpty()
                ? parsed.templates().get(0).templateType() : null);
        entity.setWaylineCount(parsed.waylines() == null ? 0 : parsed.waylines().size());
        entity.setWaypointCount(parsed.waylines() == null ? 0 : parsed.waylines().stream()
                .mapToInt(wayline -> wayline.waypoints() == null ? 0 : wayline.waypoints().size()).sum());
        entity.setWorkspaceId(user.workspaceId());
        entity.setCreator(user.username());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        fileMapper.insert(entity);
        return entity;
    }

    public List<WaylineFileEntity> list(Long workspaceId) {
        return fileMapper.selectList(new QueryWrapper<WaylineFileEntity>()
                .eq("workspace_id", workspaceId)
                .orderByDesc("id"));
    }

    public WaylineFileEntity requireById(Long id) {
        WaylineFileEntity entity = fileMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("航线文件不存在: id=" + id);
        }
        return entity;
    }

    /** 解析 KMZ 返回内部统一模型（前端地图预览用） */
    public WaylineFile detail(Long id) {
        WaylineFileEntity entity = requireById(id);
        return parser.parse(storage.load(entity.getFileKey()), entity.getName());
    }

    public void delete(Long id) {
        WaylineFileEntity entity = requireById(id);
        storage.delete(entity.getFileKey());
        fileMapper.deleteById(id);
    }

    /** 设备拉取 KMZ 的下载地址（flighttask_prepare 的 file.url） */
    public String downloadUrl(WaylineFileEntity entity) {
        return appProperties.wayline().downloadBaseUrl()
                + "/api/v1/waylines/files/" + entity.getFileKey() + "/download";
    }

    /** 航线最大执行高度（米），解析失败返回 null；用于超 120 米合规提醒 */
    public Double maxExecuteHeight(WaylineFileEntity entity) {
        try {
            WaylineFile parsed = parser.parse(storage.load(entity.getFileKey()), entity.getName());
            return parsed.waylines() == null ? null : parsed.waylines().stream()
                    .filter(wayline -> wayline.waypoints() != null)
                    .flatMap(wayline -> wayline.waypoints().stream())
                    .map(waypoint -> waypoint.executeHeight())
                    .filter(java.util.Objects::nonNull)
                    .max(Double::compare)
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
