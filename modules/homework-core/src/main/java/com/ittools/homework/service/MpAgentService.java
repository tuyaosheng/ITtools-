package com.ittools.homework.service;

import com.ittools.platform.storage.FileStorageService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 掌控板助手（学生机上的小程序）：像 Mind+ 上传模式一样，在学生机本机编译 C++ 并写进掌控板。
 * <ul>
 *   <li>助手按 IP 认人：它从哪台电脑连上来，就只领那台电脑上登录的学生（或老师）发的任务；</li>
 *   <li>助手长轮询领任务、回报进度；状态只放内存（重启平台后助手会自动重新连上）；</li>
 *   <li>Mind+ 自己的掌控板 / Wi-Fi / MQTT 积木代码生成文件（main.js）由助手从学生机的 Mind+ 里读来交给平台，
 *       平台只收下面登记过 SHA-256 的版本（这些文件会在学生浏览器里运行，不能让人随便换），也会先找教师机本机的 Mind+。</li>
 * </ul>
 */
@Service
public class MpAgentService {
    /** Mind+ 2.0.7 的掌控板设备和网络扩展（名字 → main.js 的 SHA-256）。 */
    public static final Map<String, String> MINDPLUS_FILES;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("dev-DFRobot-handpyEsp32@0.0.6", "a92df8b0c7ce9ffedd0bc53a11d3c2e3e019956df4c1ae28340030e7b92ffed6");
        m.put("ext-DFRobot-MQTT@0.0.4", "ab958031d086538fe480b33d05f78bb982cea963fb55ac75bb11a6e980f904a8");
        m.put("ext-DFRobot-WIFI@0.0.5", "59db3d5a86a0a891eb2bafccbd6386bcdddfe31f6b09e90b510659dbce367c28");
        MINDPLUS_FILES = Collections.unmodifiableMap(m);
    }
    static final long ONLINE_MS = 45_000;
    static final int MAX_CODE = 200_000;
    static final int MAX_LOG = 60_000;

    private final FileStorageService storage;
    private final Map<String, Agent> agents = new ConcurrentHashMap<>();
    private final Map<String, BlockingQueue<Job>> queues = new ConcurrentHashMap<>();
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public MpAgentService(FileStorageService storage) {
        this.storage = storage;
    }

    static final class Agent {
        String ip;
        String version;
        boolean mindplus;
        String port;
        String note;
        volatile long lastSeen;
    }

    static final class Job {
        String id;
        String ip;
        Long userId;
        String code;
        volatile String status = "QUEUED";   // QUEUED / TAKEN / COMPILING / UPLOADING / DONE / FAILED
        volatile String message = "排队中";
        final StringBuilder log = new StringBuilder();
        long created = System.currentTimeMillis();
        volatile long updated = created;
    }

    // ---------------- 助手 ----------------

    /** 助手长轮询：报到（版本、有没有 Mind+、板子在哪个串口），等最多 waitSeconds 秒领一个任务。 */
    public Map<String, Object> poll(String ip, String version, boolean mindplus, String port, String note, int waitSeconds) throws InterruptedException {
        Agent a = agents.computeIfAbsent(ip, k -> new Agent());
        a.ip = ip;
        a.version = cut(version, 40);
        a.mindplus = mindplus;
        a.port = cut(port, 40);
        a.note = cut(note, 200);
        a.lastSeen = System.currentTimeMillis();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("need", missingFiles());
        BlockingQueue<Job> q = queues.computeIfAbsent(ip, k -> new LinkedBlockingQueue<>());
        Job j = q.poll(Math.max(0, Math.min(waitSeconds, 25)), TimeUnit.SECONDS);
        a.lastSeen = System.currentTimeMillis();
        while (j != null && !"QUEUED".equals(j.status)) j = q.poll();   // 学生取消或过期的跳过
        if (j != null) {
            j.status = "TAKEN";
            j.message = "助手已收到";
            j.updated = System.currentTimeMillis();
            Map<String, Object> job = new LinkedHashMap<>();
            job.put("id", j.id);
            job.put("code", j.code);
            r.put("job", job);
        }
        return r;
    }

    /** 助手回报进度：status、要追加的日志、给学生看的一句话。 */
    public void progress(String ip, String jobId, String status, String log, String message) {
        Job j = jobs.get(jobId);
        if (j == null || !j.ip.equals(ip)) throw new IllegalArgumentException("没有这个任务");
        if (status != null && status.matches("COMPILING|UPLOADING|DONE|FAILED")) j.status = status;
        if (message != null) j.message = cut(message, 300);
        if (log != null && !log.isEmpty()) {
            synchronized (j.log) {
                j.log.append(log);
                if (j.log.length() > MAX_LOG) j.log.delete(0, j.log.length() - MAX_LOG);
            }
        }
        j.updated = System.currentTimeMillis();
        Agent a = agents.get(ip);
        if (a != null) a.lastSeen = System.currentTimeMillis();
    }

    /** 助手交来 Mind+ 的 main.js：只收登记过的版本。 */
    public void putMindplusFile(String name, byte[] data) {
        String want = MINDPLUS_FILES.get(name);
        if (want == null) throw new IllegalArgumentException("不认识的文件：" + name);
        if (data == null || data.length > 2_000_000 || !want.equals(sha256(data))) throw new IllegalArgumentException("文件内容和 Mind+ 2.0.7 的不一样，没有收下");
        try {
            Path p = mindplusDir().resolve(name + ".js");
            Files.createDirectories(p.getParent());
            Path tmp = p.resolveSibling(name + ".js.tmp");
            Files.write(tmp, data);
            Files.move(tmp, p, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("保存失败：" + e.getMessage());
        }
    }

    /** 编辑器取 Mind+ 的 main.js（没有返回 null）。 */
    public byte[] getMindplusFile(String name) {
        String want = MINDPLUS_FILES.get(name);
        if (want == null) return null;
        for (Path p : candidates(name)) {
            try {
                if (Files.isRegularFile(p)) {
                    byte[] d = Files.readAllBytes(p);
                    if (want.equals(sha256(d))) return d;
                }
            } catch (IOException ignored) {
                // 下一个
            }
        }
        return null;
    }

    public List<String> missingFiles() {
        List<String> r = new ArrayList<>();
        for (String n : MINDPLUS_FILES.keySet()) if (getMindplusFile(n) == null) r.add(n);
        return r;
    }

    private List<Path> candidates(String name) {
        List<Path> r = new ArrayList<>();
        r.add(mindplusDir().resolve(name + ".js"));
        // 教师机本机装了 Mind+（下载过掌控板和网络扩展）也可以直接用
        String la = System.getenv("LOCALAPPDATA");
        if (la != null) {
            Path up = Paths.get(la, "mind+", "download", "upload");
            r.add(up.resolve(name.startsWith("dev-") ? "devices" : "extensions").resolve(name).resolve("main.js"));
        }
        return r;
    }

    private Path mindplusDir() {
        return storage.root().resolve("mindplus").toAbsolutePath().normalize();
    }

    // ---------------- 学生 / 老师（浏览器）----------------

    /** 这台电脑上的助手在不在。 */
    public Map<String, Object> status(String ip) {
        Map<String, Object> r = new LinkedHashMap<>();
        Agent a = agents.get(ip);
        boolean online = a != null && System.currentTimeMillis() - a.lastSeen < ONLINE_MS;
        r.put("online", online);
        r.put("mindplus", online && a.mindplus);
        r.put("port", online ? a.port : null);
        r.put("version", online ? a.version : null);
        r.put("note", online ? a.note : null);
        List<String> files = new ArrayList<>();
        for (String n : MINDPLUS_FILES.keySet()) if (getMindplusFile(n) != null) files.add(n);
        r.put("files", files);
        r.put("ip", ip);
        return r;
    }

    public Map<String, Object> submit(String ip, Long userId, String code) {
        if (code == null || code.trim().isEmpty()) throw new IllegalArgumentException("没有代码");
        if (code.length() > MAX_CODE) throw new IllegalArgumentException("代码太长了");
        Agent a = agents.get(ip);
        if (a == null || System.currentTimeMillis() - a.lastSeen >= ONLINE_MS) throw new IllegalStateException("这台电脑上的掌控板助手没有打开");
        if (!a.mindplus) throw new IllegalStateException("这台电脑上没找到 Mind+ 的编译工具");
        cleanup();
        // 同一台电脑只留最新的一个：之前还在排队的作废
        BlockingQueue<Job> q = queues.computeIfAbsent(ip, k -> new LinkedBlockingQueue<>());
        for (Job old : q) {
            old.status = "FAILED";
            old.message = "已被新的上传代替";
        }
        q.clear();
        Job j = new Job();
        j.id = UUID.randomUUID().toString().replace("-", "");
        j.ip = ip;
        j.userId = userId;
        j.code = code;
        jobs.put(j.id, j);
        q.add(j);
        return view(j, 0);
    }

    public Map<String, Object> job(String ip, Long userId, String id, int logFrom) {
        Job j = jobs.get(id);
        if (j == null || !j.ip.equals(ip) || (j.userId != null && !j.userId.equals(userId))) throw new IllegalArgumentException("没有这个任务");
        // 助手长时间没回音
        long now = System.currentTimeMillis();
        if (("QUEUED".equals(j.status) || "TAKEN".equals(j.status)) && now - j.updated > 60_000) {
            j.status = "FAILED";
            j.message = "掌控板助手没有回应，请看看学生机上的助手窗口";
        } else if (("COMPILING".equals(j.status) || "UPLOADING".equals(j.status)) && now - j.updated > 180_000) {
            j.status = "FAILED";
            j.message = "编译或上传超时";
        }
        return view(j, logFrom);
    }

    private Map<String, Object> view(Job j, int logFrom) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", j.id);
        r.put("status", j.status);
        r.put("message", j.message);
        String log;
        synchronized (j.log) {
            log = j.log.toString();
        }
        int from = Math.max(0, Math.min(logFrom, log.length()));
        r.put("log", log.substring(from));
        r.put("logEnd", log.length());
        r.put("updatedAt", Instant.ofEpochMilli(j.updated).toString());
        return r;
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        jobs.values().removeIf(j -> now - j.created > 3_600_000);
        agents.values().removeIf(a -> now - a.lastSeen > 86_400_000);
    }

    // ---------------- 工具 ----------------
    static String cut(String s, int n) {
        if (s == null) return null;
        return s.length() > n ? s.substring(0, n) : s;
    }

    static String sha256(byte[] d) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(d);
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
