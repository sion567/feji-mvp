package com.feiji.cloud.wayline;

import com.feiji.cloud.auth.Audited;
import com.feiji.cloud.auth.CurrentUser;
import com.feiji.cloud.auth.LoginUser;
import com.feiji.cloud.auth.RequireRole;
import com.feiji.cloud.auth.Role;
import com.feiji.cloud.cloudapi.wayline.model.WaylineFile;
import com.feiji.cloud.common.api.HttpResult;
import com.feiji.cloud.wayline.storage.WaylineStorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/waylines")
public class WaylineController {

    private final WaylineLibraryService libraryService;
    private final WaylineStorageService storageService;

    public WaylineController(WaylineLibraryService libraryService, WaylineStorageService storageService) {
        this.libraryService = libraryService;
        this.storageService = storageService;
    }

    /** 上传 KMZ 并解析入库（设备执行时通过 file.url 拉取原文件） */
    @PostMapping("/upload")
    @RequireRole(Role.OPERATOR)
    @Audited("上传航线")
    public HttpResult<WaylineFileEntity> upload(@RequestParam("file") MultipartFile file,
                                                @CurrentUser LoginUser user) throws IOException {
        String name = file.getOriginalFilename() == null ? "wayline.kmz" : file.getOriginalFilename();
        return HttpResult.ok(libraryService.upload(file.getBytes(), name, user));
    }

    @GetMapping
    public HttpResult<List<WaylineFileEntity>> list(@CurrentUser LoginUser user) {
        return HttpResult.ok(libraryService.list(user.workspaceId()));
    }

    @GetMapping("/{id}")
    public HttpResult<WaylineFile> detail(@PathVariable Long id) {
        return HttpResult.ok(libraryService.detail(id));
    }

    @DeleteMapping("/{id}")
    @RequireRole(Role.OPERATOR)
    @Audited("删除航线")
    public HttpResult<Void> delete(@PathVariable Long id) {
        libraryService.delete(id);
        return HttpResult.ok();
    }

    /**
     * KMZ 下载（flighttask_prepare 的 file.url 指向这里，设备侧拉取）。
     * fileKey 为服务端生成的随机文件名，鉴权拦截器对 /api/v1/waylines/files/** 豁免。
     */
    @GetMapping("/files/{fileKey}/download")
    public ResponseEntity<byte[]> download(@PathVariable String fileKey) {
        byte[] content = storageService.load(fileKey);
        String encodedName = URLEncoder.encode(fileKey, StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }
}
