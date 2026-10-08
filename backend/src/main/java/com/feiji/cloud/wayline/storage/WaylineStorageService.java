package com.feiji.cloud.wayline.storage;

import com.feiji.cloud.cloudapi.wayline.WaylineParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 航线文件存储（本地磁盘实现）。
 * 设备通过平台 HTTP 下载接口拉取 KMZ（file.url 指向 /api/v1/waylines/files/{fileKey}/download）；
 * 二期切换 MinIO 时仅需替换本类实现。
 */
@Service
public class WaylineStorageService {

    private static final Logger log = LoggerFactory.getLogger(WaylineStorageService.class);

    private final Path rootDir;

    public WaylineStorageService(com.feiji.cloud.config.AppProperties properties) {
        this.rootDir = Path.of(properties.wayline().storageDir());
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new IllegalStateException("航线存储目录创建失败: " + rootDir, e);
        }
    }

    /** 保存文件并返回存储 key */
    public String save(byte[] content) {
        String key = UUID.randomUUID().toString().replace("-", "") + ".kmz";
        try {
            Files.write(rootDir.resolve(key), content);
        } catch (IOException e) {
            throw new WaylineParseException("航线文件保存失败: " + e.getMessage(), e);
        }
        return key;
    }

    public byte[] load(String fileKey) {
        try {
            return Files.readAllBytes(safeResolve(fileKey));
        } catch (IOException e) {
            throw new WaylineParseException("航线文件读取失败: " + fileKey, e);
        }
    }

    public void delete(String fileKey) {
        try {
            Files.deleteIfExists(safeResolve(fileKey));
        } catch (IOException e) {
            log.warn("航线文件删除失败, key={}: {}", fileKey, e.getMessage());
        }
    }

    /** 防目录穿越：只允许本目录下的纯文件名 */
    private Path safeResolve(String fileKey) {
        Path resolved = rootDir.resolve(Path.of(fileKey).getFileName().toString());
        if (!resolved.startsWith(rootDir)) {
            throw new WaylineParseException("非法的文件 key: " + fileKey);
        }
        return resolved;
    }
}
