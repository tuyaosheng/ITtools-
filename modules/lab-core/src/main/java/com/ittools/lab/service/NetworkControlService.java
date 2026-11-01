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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.ittools.platform.db.Sql.p;

/**
 * 对接“电脑室网络控制器 CTR”主控端 HTTP 接口（默认 http://127.0.0.1:8770）：按 IP 开网/断网、查询各机状态。
 * 由服务器端调用，令牌放在 Authorization 请求头里（CTR 的跨站防护对带令牌请求头的调用放行）。
 * 每个机房可以设置自己的 CTR；电脑按 IP → 座位 → 机房 找到对应的 CTR，没放进设置了 CTR 的机房时走系统设置里的全局 CTR。
 */
@Service
public class NetworkControlService {
    private static final Logger log = LoggerFactory.getLogger(NetworkControlService.class);
    static final int CONNECT_MS = 2000;
    static final int READ_MS = 5000;
    static final long STATUS_CACHE_MS = 3000;
    /** 连不上的 CTR 过一会儿再试，免得它拖慢每次状态查询。 */
    static final long FAILED_CACHE_MS = 10000;
    static final String DEFAULT_URL = "http://127.0.0.1:8770";

    private final SystemSettingService settings;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<Ctr, Snapshot> statusCache = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ctr-status");
        t.setDaemon(true);
        return t;
    });

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
        setIp(ip, on, ctrOf(ip));
    }

    private void setIp(String ip, boolean on, Ctr ctr) {
        requireEnabled();
        if (ip == null || !ip.matches("\\d{1,3}(\\.\\d{1,3}){3}")) throw new IllegalArgumentException("没有这台电脑的 IP（" + ip + "）");
        statusCache.remove(ctr);
        call(ctr, (on ? "/api/network/enable_ip?ip=" : "/api/network/disable_ip?ip=") + enc(ip));
        jdbc.update("INSERT INTO lab_net_state(ip_address, enabled) VALUES (:ip, :on) ON CONFLICT (ip_address) DO UPDATE SET enabled = :on, updated_at = now()",
                p("ip", ip, "on", on));
    }

    /** 各被控端状态（ip → {online, netState, hostname}），合并所有 CTR 的结果；全都连不上才返回 null。 */
    public Map<String, Map<String, Object>> statusByIp() {
        if (!enabled()) return null;
        Set<Ctr> ctrs = allCtrs();
        refresh(ctrs);
        boolean any = false;
        Map<String, Map<String, Object>> m = new HashMap<>();
        for (Ctr c : ctrs) {
            List<Map<String, Object>> l = cachedAgents(c);
            if (l == null) continue;
            any = true;
            for (Map<String, Object> a : l) m.put(String.valueOf(a.get("ip")), a);
        }
        return any ? m : null;
    }

    /** 本平台最后下发的指令（ip → 是否开网）。 */
    public Map<String, Boolean> lastCommands() {
        Map<String, Boolean> m = new HashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT ip_address, enabled FROM lab_net_state", p())) {
            m.put((String) r.get("ip_address"), (Boolean) r.get("enabled"));
        }
        return m;
    }

    /** 座位图用：是否启用、能否连上（给了机房就看这个机房用的 CTR）、各被控端状态、本平台最后下发的指令。 */
    public Map<String, Object> overview(Long roomId) {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean on = enabled();
        out.put("enabled", on);
        Map<String, Map<String, Object>> st = on ? statusByIp() : null;
        out.put("reachable", st != null && (roomId == null || cachedAgents(roomCtr(roomId)) != null));
        out.put("status", st == null ? new HashMap<>() : st);
        out.put("last", lastCommands());
        return out;
    }

    /** 批量开网 / 断网：逐台下发，返回成功台数和失败的 IP（一台失败不影响其它；连不上的 CTR 不再逐台等超时）。 */
    public Map<String, Object> setIps(List<String> ips, boolean on) {
        requireEnabled();
        if (ips == null || ips.isEmpty()) throw new IllegalArgumentException("请先选择电脑");
        int ok = 0;
        List<String> failed = new ArrayList<>();
        Set<Ctr> dead = new HashSet<>();
        for (String ip : ips) {
            Ctr ctr = ctrOf(ip);
            if (dead.contains(ctr)) {
                failed.add(ip);
                continue;
            }
            try {
                setIp(ip, on, ctr);
                ok++;
            } catch (RuntimeException e) {
                if (e instanceof CtrUnreachableException) dead.add(ctr);
                failed.add(ip);
                log.debug("{} {} 失败：{}", on ? "开网" : "断网", ip, e.getMessage());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", ok);
        out.put("failed", failed);
        return out;
    }

    /** 测试全局 CTR 的连接：健康检查 + 被控端数量。 */
    public Map<String, Object> test() {
        return test(globalCtr());
    }

    /** 测试这个机房实际使用的 CTR。 */
    public Map<String, Object> testRoom(Long roomId) {
        requireRoom(roomId);
        return test(roomCtr(roomId));
    }

    private Map<String, Object> test(Ctr ctr) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("url", ctr.url);
        try {
            JsonNode h = call(ctr, "/api/health");
            out.put("ok", h.path("ok").asBoolean(false));
            List<Map<String, Object>> a = agents(ctr);
            statusCache.put(ctr, new Snapshot(a));
            out.put("agents", a.size());
            out.put("online", a.stream().filter(x -> Boolean.TRUE.equals(x.get("online"))).count());
        } catch (RuntimeException e) {
            statusCache.remove(ctr);
            out.put("ok", false);
            out.put("error", e.getMessage());
        }
        return out;
    }

    // ================= 机房各自的 CTR =================

    /** 机房的 CTR 设置；conflicts 是放在本机房、但因为也在别的机房而实际走另一台 CTR 的电脑。 */
    public Map<String, Object> roomController(Long roomId) {
        requireRoom(roomId);
        Map<String, Object> out = new LinkedHashMap<>(jdbc.queryForMap(
                "SELECT ctr_url AS \"ctrUrl\", ctr_token AS \"ctrToken\" FROM lab_room WHERE id = :id", p("id", roomId)));
        Ctr mine = roomCtr(roomId);
        List<String> conflicts = new ArrayList<>();
        for (String ip : jdbc.queryForList("SELECT w.ip_address FROM lab_seat s JOIN lab_workstation w ON w.id = s.workstation_id "
                + "WHERE s.room_id = :r ORDER BY w.ip_address", p("r", roomId), String.class)) {
            if (!ctrOf(ip).equals(mine)) conflicts.add(ip);
        }
        out.put("conflicts", conflicts);
        return out;
    }

    /** 保存机房的 CTR；地址留空表示用全局 CTR。 */
    public void saveRoomController(Long roomId, String url, String token) {
        requireRoom(roomId);
        String u = url == null ? "" : url.trim();
        String t = token == null ? "" : token.trim();
        if (!u.isEmpty() && !u.matches("https?://[^\\s/]+(:\\d+)?/?")) throw new IllegalArgumentException("网络控制器地址格式应为 http://IP:端口");
        if (u.length() > 200 || t.length() > 200) throw new IllegalArgumentException("网络控制器地址和令牌不超过 200 个字符");
        jdbc.update("UPDATE lab_room SET ctr_url = :u, ctr_token = :t WHERE id = :id",
                p("u", u.isEmpty() ? null : u, "t", u.isEmpty() || t.isEmpty() ? null : t, "id", roomId));
    }

    private void requireRoom(Long roomId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM lab_room WHERE id = :id", p("id", roomId), Long.class);
        if (n == null || n == 0) throw new IllegalArgumentException("机房模型不存在");
    }

    private Ctr globalCtr() {
        return new Ctr(settings.get(SystemSettingService.NET_CTR_URL), settings.get(SystemSettingService.NET_CTR_TOKEN));
    }

    private Ctr roomCtr(Long roomId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT ctr_url, ctr_token FROM lab_room WHERE id = :id AND ctr_url IS NOT NULL", p("id", roomId));
        return l.isEmpty() ? globalCtr() : new Ctr((String) l.get(0).get("ctr_url"), (String) l.get(0).get("ctr_token"));
    }

    /** 这台电脑归哪台 CTR：同一台电脑放在多个设了 CTR 的机房时，取最早建的机房。 */
    private Ctr ctrOf(String ip) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT r.ctr_url, r.ctr_token FROM lab_workstation w "
                + "JOIN lab_seat s ON s.workstation_id = w.id JOIN lab_room r ON r.id = s.room_id "
                + "WHERE w.ip_address = :ip AND r.ctr_url IS NOT NULL ORDER BY r.id LIMIT 1", p("ip", ip));
        return l.isEmpty() ? globalCtr() : new Ctr((String) l.get(0).get("ctr_url"), (String) l.get(0).get("ctr_token"));
    }

    private Set<Ctr> allCtrs() {
        Set<Ctr> out = new LinkedHashSet<>();
        out.add(globalCtr());
        for (Map<String, Object> r : jdbc.queryForList("SELECT ctr_url, ctr_token FROM lab_room WHERE ctr_url IS NOT NULL ORDER BY id", p())) {
            out.add(new Ctr((String) r.get("ctr_url"), (String) r.get("ctr_token")));
        }
        return out;
    }

    // ================= 状态缓存 =================

    /** 并行刷新过期的 CTR，一台连不上最多耽误一次超时。 */
    private void refresh(Collection<Ctr> ctrs) {
        CompletableFuture<?>[] all = ctrs.stream()
                .map(c -> CompletableFuture.runAsync(() -> cachedAgents(c), pool))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(all).join();
    }

    /** 被控端列表，缓存未过期就复用；连不上返回 null。 */
    private List<Map<String, Object>> cachedAgents(Ctr ctr) {
        Snapshot s = statusCache.get(ctr);
        if (s != null && s.fresh()) return s.agents;
        List<Map<String, Object>> a = null;
        try {
            a = agents(ctr);
        } catch (RuntimeException e) {
            log.debug("网络控制器状态读取失败：{}", e.getMessage());
        }
        statusCache.put(ctr, new Snapshot(a));
        return a;
    }

    private List<Map<String, Object>> agents(Ctr ctr) {
        JsonNode r = call(ctr, "/api/status");
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

    private JsonNode call(Ctr ctr, String path) {
        String url = ctr.url + path;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(CONNECT_MS);
            c.setReadTimeout(READ_MS);
            boolean write = path.startsWith("/api/network/");
            c.setRequestMethod(write ? "POST" : "GET");
            if (!ctr.token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + ctr.token);
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
            throw new CtrUnreachableException("连不上网络控制器（" + ctr.url + "）：请确认教师机上的网络控制器主控端已打开，地址和端口正确");
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

    /** 一台 CTR：地址 + 令牌。 */
    private static final class Ctr {
        final String url;
        final String token;

        Ctr(String url, String token) {
            this.url = (url == null || url.trim().isEmpty() ? DEFAULT_URL : url.trim()).replaceAll("/+$", "");
            this.token = token == null ? "" : token.trim();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Ctr && url.equals(((Ctr) o).url) && token.equals(((Ctr) o).token);
        }

        @Override
        public int hashCode() {
            return url.hashCode() * 31 + token.hashCode();
        }
    }

    /** 一次状态查询的结果；agents 为 null 表示当时连不上。 */
    private static final class Snapshot {
        final List<Map<String, Object>> agents;
        final long at = System.currentTimeMillis();

        Snapshot(List<Map<String, Object>> agents) {
            this.agents = agents;
        }

        boolean fresh() {
            return System.currentTimeMillis() - at <= (agents == null ? FAILED_CACHE_MS : STATUS_CACHE_MS);
        }
    }

    private static final class CtrUnreachableException extends IllegalArgumentException {
        CtrUnreachableException(String message) {
            super(message);
        }
    }
}
