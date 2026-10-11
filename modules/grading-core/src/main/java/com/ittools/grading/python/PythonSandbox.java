package com.ittools.grading.python;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 服务端 Python 评测沙箱：把学生代码与各阅卷点输入交给 harness.py，逐点在子进程中运行并限时。
 * <ul>
 *   <li>docker（默认）：每次评测起一个一次性容器——断网、只读根文件系统、限内存/CPU/进程数、非 root 用户</li>
 *   <li>local：直接调用本机 python，仅限开发环境（无隔离，只有超时保护）</li>
 *   <li>disabled：关闭服务端评测</li>
 * </ul>
 */
@Component
public class PythonSandbox {
    private static final Logger log = LoggerFactory.getLogger(PythonSandbox.class);
    private static final int MAX_CODE = 64 * 1024;

    private final String mode;
    private final String image;
    private final String dockerCmd;
    private final String localCmd;
    private final Semaphore permits;
    private final String bootstrap;
    private final ObjectMapper json = new ObjectMapper();

    public PythonSandbox(@Value("${ittools.python.runner:docker}") String mode,
                         @Value("${ittools.python.image:python:3.12-alpine}") String image,
                         @Value("${ittools.python.docker-cmd:docker}") String dockerCmd,
                         @Value("${ittools.python.local-cmd:python}") String localCmd,
                         @Value("${ittools.python.max-concurrent:4}") int maxConcurrent) {
        this.mode = mode;
        this.image = image;
        this.dockerCmd = dockerCmd;
        this.localCmd = localCmd;
        this.permits = new Semaphore(Math.max(1, maxConcurrent), true);
        // harness 以 base64 传给 python -c，避免 Windows 命令行对引号/换行的转义问题
        String harness = readResource("/python/harness.py");
        this.bootstrap = "import base64;exec(base64.b64decode('"
                + Base64.getEncoder().encodeToString(harness.getBytes(StandardCharsets.UTF_8)) + "').decode('utf-8'))";
        if ("local".equals(mode)) log.warn("Python 评测使用 local 模式：学生代码直接在服务器上运行，仅限开发环境！");
    }

    public String mode() {
        return mode;
    }

    /** 运行代码，返回每个输入对应的 {status: OK|RE|TLE, stdout, stderr, ms}。 */
    public List<Map<String, Object>> run(String code, List<String> inputs, int timeoutMs) {
        if ("disabled".equals(mode)) throw new IllegalStateException("服务器未启用 Python 评测");
        if (code == null) code = "";
        if (code.length() > MAX_CODE) throw new IllegalArgumentException("代码过长（上限 64KB）");
        if (inputs.isEmpty()) return Collections.emptyList();
        try {
            if (!permits.tryAcquire(60, TimeUnit.SECONDS)) throw new IllegalStateException("评测繁忙，请稍后再试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("评测被中断");
        }
        try {
            return exec(code, inputs, timeoutMs);
        } finally {
            permits.release();
        }
    }

    private List<Map<String, Object>> exec(String code, List<String> inputs, int timeoutMs) {
        String container = "ittools-py-" + UUID.randomUUID();
        List<String> cmd = new ArrayList<>();
        if ("docker".equals(mode)) {
            cmd.addAll(Arrays.asList(dockerCmd, "run", "--rm", "-i", "--name", container,
                    "--network", "none", "--memory", "256m", "--memory-swap", "256m", "--cpus", "1", "--pids-limit", "64",
                    "--read-only", "--tmpfs", "/tmp:rw,size=16m", "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
                    "--user", "65534:65534", image, "python3", "-I", "-c", bootstrap));
        } else {
            cmd.addAll(Arrays.asList(localCmd, "-I", "-c", bootstrap));
        }
        Map<String, Object> req = new HashMap<>();
        req.put("code", code);
        req.put("inputs", inputs);
        req.put("timeout", timeoutMs / 1000.0);
        // 每点限时 + 容器启动余量
        long totalMs = (long) inputs.size() * (timeoutMs + 500) + 15000;

        Process proc = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            proc = pb.start();
            StreamCollector out = new StreamCollector(proc.getInputStream());
            StreamCollector err = new StreamCollector(proc.getErrorStream());
            out.start();
            err.start();
            try (OutputStream in = proc.getOutputStream()) {
                in.write(json.writeValueAsBytes(req));
            }
            if (!proc.waitFor(totalMs, TimeUnit.MILLISECONDS)) {
                killContainer(container);
                throw new IllegalStateException("评测超时，请检查代码是否有死循环");
            }
            out.join(5000);
            err.join(5000);
            String stdout = out.text();
            if (proc.exitValue() != 0 || stdout.isEmpty()) {
                log.warn("Python 沙箱异常退出 code={} stderr={}", proc.exitValue(), err.text());
                throw new IllegalStateException("评测环境异常，请联系管理员（" + firstLine(err.text()) + "）");
            }
            Map<String, List<Map<String, Object>>> res = json.readValue(stdout, new TypeReference<Map<String, List<Map<String, Object>>>>() {});
            return res.get("results");
        } catch (IOException e) {
            log.warn("无法启动 Python 沙箱: {}", e.getMessage());
            throw new IllegalStateException("无法启动 Python 评测环境（" + ("docker".equals(mode) ? "请确认 Docker 已运行" : "请确认已安装 Python") + "）");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            killContainer(container);
            throw new IllegalStateException("评测被中断");
        } finally {
            if (proc != null && proc.isAlive()) proc.destroyForcibly();
        }
    }

    private void killContainer(String name) {
        if (!"docker".equals(mode)) return;
        try {
            new ProcessBuilder(dockerCmd, "kill", name).redirectErrorStream(true).start().waitFor(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("结束评测容器失败 {}: {}", name, e.getMessage());
        }
    }

    private static String firstLine(String s) {
        String t = s == null ? "" : s.trim();
        int i = t.indexOf('\n');
        t = i < 0 ? t : t.substring(0, i);
        return t.length() > 120 ? t.substring(0, 120) : t;
    }

    private static String readResource(String path) {
        try (InputStream in = PythonSandbox.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("缺少资源 " + path);
            return new String(readAll(in), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) buf.write(b, 0, n);
        return buf.toByteArray();
    }

    /** 后台读取子进程输出，防止管道写满导致死锁。 */
    private static final class StreamCollector extends Thread {
        private final InputStream in;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

        StreamCollector(InputStream in) {
            this.in = in;
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) {
                    synchronized (buf) {
                        if (buf.size() < 4 * 1024 * 1024) buf.write(b, 0, n);
                    }
                }
            } catch (IOException ignored) {
                // 进程被强制结束
            }
        }

        String text() {
            synchronized (buf) {
                return new String(buf.toByteArray(), StandardCharsets.UTF_8);
            }
        }
    }
}
