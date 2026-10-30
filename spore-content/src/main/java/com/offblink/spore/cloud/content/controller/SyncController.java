package com.offblink.spore.cloud.content.controller;

import com.offblink.spore.cloud.common.ErrorCode;
import com.offblink.spore.cloud.common.IdentityContext;
import com.offblink.spore.cloud.common.R;
import com.offblink.spore.cloud.common.dto.SyncPushReq;
import com.offblink.spore.cloud.content.service.SyncService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 同步接口（手机端专用，协议与 Spore-GUI 完全一致）：
 * pull 游标增量（含墓碑）→ push LWW → 题图按需上/下推。
 * 身份来自网关注入的签名头（{@link IdentityContext}），没配对的请求一律 40101。
 */
@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;
    private final String dataDir;

    public SyncController(SyncService syncService,
                          @Value("${spore.storage.data-dir:./data/attachments}") String dataDir) {
        this.syncService = syncService;
        this.dataDir = dataDir;
    }

    private Path storageDir() {
        return Paths.get(dataDir).toAbsolutePath().normalize();
    }

    @GetMapping("/pull")
    public R<Map<String, Object>> pull(@RequestParam(defaultValue = "0") long cursor,
                                       @RequestParam(defaultValue = "200") int limit) {
        return R.ok(syncService.pull(IdentityContext.requireUserId(), cursor, limit));
    }

    @PostMapping("/push")
    public R<Map<String, Object>> push(@RequestBody SyncPushReq req) {
        return R.ok(syncService.push(IdentityContext.requireUserId(), req));
    }

    /** 手机推题图：文件名 = articleId（扩展名保底 .img），路径写回 article 行 */
    @PostMapping(value = "/push-attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Map<String, String>> pushAttachment(@RequestParam String articleId,
                                                 @RequestParam("file") MultipartFile file)
            throws IOException {
        long userId = IdentityContext.requireUserId();
        if (file == null || file.isEmpty()) {
            return R.fail(ErrorCode.BAD_REQUEST, "文件为空");
        }
        String safeId = articleId.replaceAll("[^0-9A-Za-z\\-_]", "");
        if (safeId.isEmpty()) {
            return R.fail(ErrorCode.BAD_REQUEST, "articleId 非法");
        }
        Path dir = storageDir();
        Files.createDirectories(dir);
        String ext = extensionOf(file.getOriginalFilename());
        Path target = dir.resolve(safeId + ext);
        // 先写临时文件再原子移动——半途失败不毁已有题图
        Path tmp = dir.resolve(safeId + ext + ".part");
        file.transferTo(tmp);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        Map<String, String> data = new LinkedHashMap<String, String>();
        data.put("path", dir.relativize(target).toString());
        syncService.bindAttachment(userId, safeId, data.get("path"));
        return R.ok(data);
    }

    /** 拉题图：按相对路径读盘（normalize 后必须仍在题库目录内，防路径穿越） */
    @GetMapping("/pull-attachment")
    public ResponseEntity<byte[]> pullAttachment(@RequestParam String path) throws IOException {
        IdentityContext.requireUserId();
        Path root = storageDir();
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root) || !Files.isRegularFile(resolved)) {
            return ResponseEntity.notFound().build();
        }
        byte[] bytes = Files.readAllBytes(resolved);
        return ResponseEntity.ok()
                .contentType(mediaTypeOf(resolved.getFileName().toString()))
                .body(bytes);
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return ".img";
        }
        int dot = filename.lastIndexOf('.');
        return (dot >= 0 && dot < filename.length() - 1) ? filename.substring(dot) : ".img";
    }

    private static MediaType mediaTypeOf(String filename) {
        String name = filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (name.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }
}
