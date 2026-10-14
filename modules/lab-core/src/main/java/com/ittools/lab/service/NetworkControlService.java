package com.ittools.lab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ittools.platform.settings.SystemSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/**
 * 对接“电脑室网络控制器 CTR”主控端 HTTP 接口（默认 http://127.0.0.1:8770）：按 IP 开网/断网、全部开/断、查询各机状态。
 * 由服务器端调用，令牌放在 Authorization 请求头里（CTR 的跨站防护对带令牌请求头的调用放行）。
 */
@Service
public class NetworkControlService {
    private static final Logger log = LoggerFactory.getLogger(NetworkControlService.class);
    static final int CONNECT_MS = 2000;
    static final int READ_MS = 5000;
    static final long STATUS_CACHE_MS = 3000;

    private final SystemSettingService settings;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private volatile List<Map<String, Object>> statusCache;
    private volatile long statusAt;

    public NetworkControlService(SystemSettingService settings, NamedParameterJdbcTemplate jdbc) {
        this.settings = settings;
        this.jdbc = jdbc;
    }

    public boolean enabled() {
        return settings.getBool(SystemSettingService.NET_ENABLED);
    }

    private void requireEnabled() {
        if (!enabled()) throw new IllegalArgumentException("未启用网络控制器（管理员在“系统设置 → 网络控制器”中开启）");
    }

    /** 给某台电脑开网 / 断网，并记录本平台最后下发的指令。 */
    public void setIp(String ip, boolean on) {
        requireEnabled();
        if (ip == null || !ip.matches("\\d{1,3}(\\.\\d{1,3}){3}")) throw new IllegalArgumentException("没有这台电脑的 IP（" + ip + "）");
        call((on ? "/api/network/enable_ip?ip=" : "/api/network/disable_ip?ip=") + enc(ip));
        jdbc.update("INSERT INTO lab_net_state(ip_address, enabled) VALUES (:ip, :on) ON CONFLICT (ip_address) DO UPDATE SET enabled = :on, updated_at = now()",
                p("ip", ip, "on", on));
        statusAt = 0;
    }

    /** 全部开网 / 全部断网（作用于 CTR 上所有在线被控端）。 */
    public void setAll(boolean on) {
        requireEnabled();
        call(on ? "/api/network/enable" : "/api/network/disable");
        statusAt = 0;
    }

    /** 各被控端状态（ip → {online, netState, hostname}），3 秒内复用缓存；连不上返回 null。 */
    public Map<String, Map<String, Object>> statusByIp() {
        if (!enabled()) return null;
        List<Map<String, Object>> l = statusCache;
        if (l == null || System.currentTimeMillis() - statusAt > STATUS_CACHE_MS) {
            try {
                l = agents();
                statusCache = l;
                statusAt = System.currentTimeMillis();
            } catch (RuntimeException e) {
                log.debug("网络控制器状态读取失败：{}", e.getMessage());
                return null;
            }
        }
        Map<String, Map<String, Object>> m = new HashMap<>();
        for (Map<String, Object> a : l) m.put(String.valueOf(a.get("ip")), a);
        return m;
    }

    /** 本平台最后下发的指令（ip → 是否开网）。 */
    public Map<String, Boolean> lastCommands() {
        Map<String, Boolean> m = new HashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT ip_address, enabled FROM lab_net_state", p())) {
            m.put((String) r.get("ip_address"), (Boolean) r.get("enabled"));
        }
        return m;
    }

    /** 座位图用：是否启用、能否连上、各被控端状态、本平台最后下发的指令。 */
    public Map<String, Object> overview() {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean on = enabled();
        out.put("enabled", on);
        Map<String, Map<String, Object>> st = on ? statusByIp() : null;
        out.put("reachable", st != null);
        out.put("status", st == null ? new HashMap<>() : st);
        out.put("last", lastCommands());
        return out;
    }

    /** 批量开网 / 断网：逐台下发，返回成功台数和失败的 IP（一台失败不影响其它）。 */
    public Map<String, Object> setIps(List<String> ips, boolean on) {
        requireEnabled();
        if (ips == null || ips.isEmpty()) throw new IllegalArgumentException("请先选择电脑");
        int ok = 0;
        List<String> failed = new ArrayList<>();
        for (String ip : ips) {
            try {
                setIp(ip, on);
                ok++;
            } catch (RuntimeException e) {
                failed.add(ip);
                log.debug("{} {} 失败：{}", on ? "开网" : "断网", ip, e.getMessage());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", ok);
        out.put("failed", failed);
        return out;
    }

    /** 测试连接：健康检查 + 被控端数量。 */
    public Map<String, Object> test() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("url", base());
        try {
            JsonNode h = call("/api/health");
            out.put("ok", h.path("ok").asBoolean(false));
            List<Map<String, Object>> a = agents();
            out.put("agents", a.size());
            out.put("online", a.stream().filter(x -> Boolean.TRUE.equals(x.get("online"))).count());
        } catch (RuntimeException e) {
            out.put("ok", false);
            out.put("error", e.getMessage());
        }
        return out;
    }

    private List<Map<String, Object>> agents() {
        JsonNode r = call("/api/status");
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode a : r.path("agents")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ip", a.path("ip").asText());
            m.put("hostname", a.path("hostname").asText(""));
            m.put("online", a.path("is_online").asBoolean(false));
            m.put("netState", a.path("net_state").asText(""));
            out.add(m);
        }
        return out;
    }

    private String base() {
        String u = settings.get(SystemSettingService.NET_CTR_URL);
        return (u == null || u.trim().isEmpty() ? "http://127.0.0.1:8770" : u.trim()).replaceAll("/+$", "");
    }

    private JsonNode call(String path) {
        String url = base() + path;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(CONNECT_MS);
            c.setReadTimeout(READ_MS);
            boolean write = path.startsWith("/api/network/");
            c.setRequestMethod(write ? "POST" : "GET");
            String token = settings.get(SystemSettingService.NET_CTR_TOKEN);
            if (token != null && !token.trim().isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token.trim());
            if (write) {
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(0);
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = in == null ? "" : read(in);
            if (code == 401) throw new IllegalArgumentException("网络控制器拒绝：令牌不正确");
            if (code == 403) throw new IllegalArgumentException("网络控制器拒绝了请求（403），请在网络控制器设置令牌，并在这里填写相同的令牌");
            if (code >= 400) throw new IllegalArgumentException("网络控制器返回错误 " + code);
            return json.readTree(body.isEmpty() ? "{}" : body);
        } catch (IOException e) {
            throw new IllegalArgumentException("连不上网络控制器（" + base() + "）：请确认教师机上的网络控制器主控端已打开，地址和端口正确");
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) {
            bos.write(buf, 0, r);
            if (bos.size() > 4 * 1024 * 1024) break;
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (IOException e) {
            return s;
        }
    }
}
