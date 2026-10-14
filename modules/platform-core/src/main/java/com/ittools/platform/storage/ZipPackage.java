package com.ittools.platform.storage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 网页包解压（课件包、学生个人站点共用）：防 zip-slip、限制总大小与文件数、兼容 Windows 自带压缩的 GBK 文件名，
 * 所有文件都在同一个顶层目录下时去掉该目录。
 */
public final class ZipPackage {
    private ZipPackage() {}

    /** 解压异常（包内容不合规），与普通 IO 异常区分。 */
    public static class BadPackage extends IllegalArgumentException {
        public BadPackage(String m) {
            super(m);
        }
    }

    public static final class Result {
        public final List<String> files;
        public final long bytes;

        Result(List<String> files, long bytes) {
            this.files = files;
            this.bytes = bytes;
        }
    }

    /** 先按 UTF-8 文件名解压，失败再按 GBK。dir 解压失败时会被清理。 */
    public static Result extract(File zip, Path dir, long maxTotal, int maxEntries) throws IOException {
        try {
            return extract(zip, dir, StandardCharsets.UTF_8, maxTotal, maxEntries);
        } catch (BadPackage e) {
            deleteDir(dir);
            throw e;
        } catch (IllegalArgumentException e) {
            deleteDir(dir);
            try {
                return extract(zip, dir, Charset.forName("GBK"), maxTotal, maxEntries);
            } catch (RuntimeException | IOException e2) {
                deleteDir(dir);
                throw e2;
            }
        } catch (IOException e) {
            deleteDir(dir);
            throw e;
        }
    }

    static Result extract(File zip, Path dir, Charset cs, long maxTotal, int maxEntries) throws IOException {
        List<String> names = new ArrayList<>();
        long total = 0;
        Path base = dir.toAbsolutePath().normalize();
        try (ZipFile zf = new ZipFile(zip, cs)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            List<ZipEntry> entries = new ArrayList<>();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (entries.size() >= maxEntries) throw new BadPackage("压缩包文件过多（超过 " + maxEntries + " 个）");
                entries.add(e);
            }
            String prefix = commonTopDir(entries);
            Files.createDirectories(base);
            for (ZipEntry e : entries) {
                String n = e.getName().replace('\\', '/');
                if (n.startsWith("__MACOSX/")) continue;
                if (prefix != null) n = n.substring(prefix.length());
                if (n.isEmpty()) continue;
                Path target = base.resolve(n).normalize();
                if (!target.startsWith(base)) throw new BadPackage("压缩包中包含非法路径：" + e.getName());
                if (e.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                try (InputStream in = zf.getInputStream(e); OutputStream out = Files.newOutputStream(target)) {
                    byte[] buf = new byte[65536];
                    int r;
                    while ((r = in.read(buf)) > 0) {
                        total += r;
                        if (total > maxTotal) throw new BadPackage("解压后超过 " + (maxTotal / 1024 / 1024) + "MB");
                        out.write(buf, 0, r);
                    }
                }
                names.add(n);
            }
        }
        if (names.isEmpty()) throw new BadPackage("压缩包是空的");
        return new Result(names, total);
    }

    private static String commonTopDir(List<ZipEntry> entries) {
        String top = null;
        for (ZipEntry e : entries) {
            String n = e.getName().replace('\\', '/');
            if (n.startsWith("__MACOSX/")) continue;
            int i = n.indexOf('/');
            if (i < 0) return null;
            String t = n.substring(0, i + 1);
            if (top == null) top = t;
            else if (!top.equals(t)) return null;
        }
        return top;
    }

    /** 主页：优先 candidates 中的文件名（不分大小写），否则取层级最浅的 html。 */
    public static String findEntry(List<String> files, List<String> candidates) {
        for (String c : candidates) for (String f : files) if (f.equalsIgnoreCase(c)) return f;
        return files.stream().filter(f -> f.toLowerCase().matches(".*\\.html?$"))
                .min(Comparator.comparingInt((String f) -> f.split("/").length).thenComparing(f -> f))
                .orElseThrow(() -> new BadPackage("压缩包中没有找到网页文件（.html）"));
    }

    public static void deleteDir(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
        } catch (IOException ignored) {
            // 清理失败不影响主流程
        }
    }
}
