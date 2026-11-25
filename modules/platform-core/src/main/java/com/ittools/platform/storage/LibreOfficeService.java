package com.ittools.platform.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 调用服务器上的 LibreOffice（免费，可离线安装）转换课件。没装时 available() 为 false，调用方退回 POI 方案。
 * soffice 位置：配置 ittools.office.soffice / 环境变量 SOFFICE_PATH，否则在常见安装位置里找。
 * 同一时间只跑一个转换（LibreOffice 同一个用户配置目录不能并发），使用平台自己的配置目录，不影响本机桌面版。
 */
@Service
public class LibreOfficeService {
    private static final long TIMEOUT_SECONDS = 180;
    private final String configured;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile String resolved;
    private volatile boolean probed;

    public LibreOfficeService(@Value("${ittools.office.soffice:}") String configured) {
        this.configured = configured == null ? "" : configured.trim();
    }

    public boolean available() {
        return soffice() != null;
    }

    /** soffice 可执行文件路径；找不到返回 null（结果缓存，装好 LibreOffice 后重启平台生效）。 */
    public String soffice() {
        if (probed) return resolved;
        List<String> candidates = new ArrayList<>();
        if (!configured.isEmpty()) candidates.add(configured);
        String env = System.getenv("SOFFICE_PATH");
        if (env != null && !env.trim().isEmpty()) candidates.add(env.trim());
        candidates.addAll(Arrays.asList(
                "C:\\Program Files\\LibreOffice\\program\\soffice.exe",
                "C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe",
                "D:\\Program Files\\LibreOffice\\program\\soffice.exe",
                "D:\\LibreOffice\\program\\soffice.exe",
                "/usr/bin/soffice", "/usr/local/bin/soffice", "/usr/lib/libreoffice/program/soffice",
                "/opt/libreoffice/program/soffice", "/Applications/LibreOffice.app/Contents/MacOS/soffice"));
        for (String c : candidates) {
            if (new File(c).isFile()) {
                resolved = c;
                break;
            }
        }
        probed = true;
        return resolved;
    }

    /**
     * 把 src 转换成 filter 指定的格式，输出到 outDir，返回生成的文件。
     * @param filter  如 "svg:impress_svg_Export"、"pdf"
     * @param outExt  生成文件的扩展名，如 "svg"
     */
    public Path convert(Path src, String filter, String outExt, Path workDir) throws Exception {
        String exe = soffice();
        if (exe == null) throw new IllegalStateException("服务器没有安装 LibreOffice");
        Files.createDirectories(workDir);
        // 用纯英文文件名，避免命令行里中文、空格出问题
        String ext = FileStorageService.extensionOf(src.getFileName().toString());
        Path in = workDir.resolve("deck." + ext);
        Files.copy(src, in, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Path profile = workDir.getParent().resolve("lo-profile").toAbsolutePath();
        List<String> cmd = Arrays.asList(exe, "--headless", "--norestore", "--nologo", "--nolockcheck", "--nodefault",
                "-env:UserInstallation=" + profile.toUri().toString(),
                "--convert-to", filter, "--outdir", workDir.toAbsolutePath().toString(), in.toAbsolutePath().toString());
        if (!lock.tryLock(TIMEOUT_SECONDS, TimeUnit.SECONDS)) throw new IllegalStateException("转换任务排队太久，请稍后再试");
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true)
                    .redirectOutput(workDir.resolve("convert.log").toFile()).start();
            if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException("转换超时");
            }
        } finally {
            lock.unlock();
            Files.deleteIfExists(in);
        }
        Path out = workDir.resolve("deck." + outExt);
        if (!Files.exists(out) || Files.size(out) == 0) throw new IllegalStateException("LibreOffice 没有生成结果");
        return out;
    }
}
