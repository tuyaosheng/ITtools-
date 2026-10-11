package com.ittools.platform.storage;

import com.ittools.platform.settings.SystemSettingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.YearMonth;
import java.util.Locale;
import java.util.UUID;

/**
 * 本地磁盘文件存储（单校机房 LAN 部署）。所有上传文件保存在 ittools.storage.root 下，
 * 数据库只保存相对路径；下载统一经由后端鉴权接口，不直接暴露目录。
 */
@Service
public class FileStorageService {
    private final Path root;
    private final SystemSettingService settings;

    public FileStorageService(@Value("${ittools.storage.root:data/storage}") String root,
                              SystemSettingService settings) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
        this.settings = settings;
    }

    public Path root() {
        return root;
    }

    public StoredFile store(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        long maxBytes = settings.getInt(SystemSettingService.UPLOAD_MAX_MB) * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("文件超过上传大小限制（" + settings.getInt(SystemSettingService.UPLOAD_MAX_MB) + "MB）");
        }
        try (InputStream in = file.getInputStream()) {
            return store(in, file.getSize(), category, file.getOriginalFilename());
        } catch (IOException e) {
            throw new IllegalStateException("文件保存失败：" + e.getMessage());
        }
    }

    public StoredFile store(InputStream in, long size, String category, String originalName) {
        String name = cleanName(originalName);
        String ext = extensionOf(name);
        if (settings.isBlockedExtension(ext)) {
            throw new IllegalArgumentException("不允许上传该类型的文件：." + ext);
        }
        String rel = category + "/" + YearMonth.now() + "/" + UUID.randomUUID().toString().replace("-", "")
                + (ext.isEmpty() ? "" : "." + ext);
        Path target = resolve(rel);
        try {
            Files.createDirectories(target.getParent());
            long written = Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredFile(rel, name, size > 0 ? size : written, ext);
        } catch (IOException e) {
            throw new IllegalStateException("文件保存失败：" + e.getMessage());
        }
    }

    public StoredFile storeBytes(byte[] data, String category, String originalName) {
        return store(new ByteArrayInputStream(data), data.length, category, originalName);
    }

    public Path resolve(String relPath) {
        Path p = root.resolve(relPath).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("非法文件路径");
        }
        return p;
    }

    public void delete(String relPath) {
        if (relPath == null || relPath.isEmpty()) return;
        try {
            Files.deleteIfExists(resolve(relPath));
        } catch (IOException ignored) {
            // 删除失败不影响业务数据
        }
    }

    public ResponseEntity<Resource> download(String relPath, String downloadName, boolean inline) {
        Path p = resolve(relPath);
        if (!Files.exists(p)) {
            throw new IllegalArgumentException("文件不存在");
        }
        String name = downloadName == null ? p.getFileName().toString() : downloadName;
        MediaType type = MediaTypeFactory.getMediaType(name).orElse(MediaType.APPLICATION_OCTET_STREAM);
        ContentDisposition cd = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(name, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(type)
                .body(new FileSystemResource(p));
    }

    public static String extensionOf(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private static String cleanName(String n) {
        if (n == null || n.trim().isEmpty()) return "file";
        String s = n.replace('\\', '/');
        return s.substring(s.lastIndexOf('/') + 1);
    }
}
