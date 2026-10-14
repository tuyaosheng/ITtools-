package com.ittools.lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ittools.lab.service.NetworkControlService;
import com.ittools.support.AbstractPostgresIT;
import com.sun.net.httpserver.HttpServer;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 机房方案（前缀+1号IP+文本排版生成机位）、交换IP、网络控制器对接、各批量导入模板“下载→改→直接导入”。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LabSchemeIT extends AbstractPostgresIT {
    @Autowired
    TestRestTemplate rest;
    @Autowired
    NetworkControlService network;
    @Autowired
    JdbcTemplate jdbc;
    final ObjectMapper om = new ObjectMapper();

    // 项目约定：各测试类各自复制 cookie+CSRF 登录逻辑
    private HttpHeaders session(String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = rest.postForEntity("/api/auth/login", new HttpEntity<>(body, h), String.class);
        assertThat(login.getStatusCode()).as(body).isEqualTo(HttpStatus.OK);
        List<String> setCookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
        HttpHeaders sess = new HttpHeaders();
        sess.setContentType(MediaType.APPLICATION_JSON);
        sess.add(HttpHeaders.COOKIE, setCookies.stream().map(sc -> sc.split(";", 2)[0]).collect(Collectors.joining("; ")));
        for (String sc : setCookies) {
            String pair = sc.split(";", 2)[0].trim();
            if (pair.startsWith("XSRF-TOKEN=")) sess.add("X-XSRF-TOKEN", pair.substring("XSRF-TOKEN=".length()));
        }
        return sess;
    }

    private HttpHeaders admin() {
        return session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
    }

    private ResponseEntity<String> raw(HttpHeaders s, HttpMethod m, String url, Object body) throws Exception {
        return rest.exchange(url, m, new HttpEntity<>(body == null ? null : om.writeValueAsString(body), s), String.class);
    }

    private JsonNode call(HttpHeaders s, HttpMethod m, String url, Object body) throws Exception {
        ResponseEntity<String> r = raw(s, m, url, body);
        assertThat(r.getStatusCode()).as(url + " → " + r.getBody()).isEqualTo(HttpStatus.OK);
        return om.readTree(r.getBody()).get("data");
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private Map<String, JsonNode> workstationsByName(HttpHeaders a, String prefix) throws Exception {
        Map<String, JsonNode> m = new TreeMap<>();
        for (JsonNode w : call(a, HttpMethod.GET, "/api/admin/lab-workstations", null)) {
            if (w.get("pcName").asText().startsWith(prefix)) m.put(w.get("pcName").asText(), w);
        }
        return m;
    }

    @Test
    void schemeGeneratesWorkstationsAndSeats() throws Exception {
        HttpHeaders a = admin();
        JsonNode room = call(a, HttpMethod.POST, "/api/admin/lab/rooms/scheme", map("name", "方案机房72", "prefix", "T72-", "pad", 2,
                "baseIp", "10.72.0.11", "step", 1, "layoutText", "1 2 . 3\n4 5 . 6"));
        long roomId = room.get("id").asLong();
        assertThat(room.get("rows").asInt()).isEqualTo(2);
        assertThat(room.get("cols").asInt()).isEqualTo(4);
        assertThat(room.get("layoutText").asText()).isEqualTo("1 2 . 3\n4 5 . 6");

        Map<String, JsonNode> ws = workstationsByName(a, "T72-");
        assertThat(ws.keySet()).containsExactly("T72-01", "T72-02", "T72-03", "T72-04", "T72-05", "T72-06");
        assertThat(ws.get("T72-01").get("ipAddress").asText()).isEqualTo("10.72.0.11");
        assertThat(ws.get("T72-06").get("ipAddress").asText()).isEqualTo("10.72.0.16");
        long id1 = ws.get("T72-01").get("id").asLong();

        Map<String, Long> cell = new HashMap<>();
        for (JsonNode c : call(a, HttpMethod.GET, "/api/admin/lab/rooms/" + roomId + "/layout", null)) {
            if (c.hasNonNull("workstationId")) cell.put(c.get("row").asInt() + "-" + c.get("col").asInt(), c.get("workstationId").asLong());
        }
        assertThat(cell).hasSize(6).doesNotContainKey("1-3");
        assertThat(cell.get("1-4")).isEqualTo(ws.get("T72-03").get("id").asLong());

        // 改成 1 号在最后一排、IP 递减：机位按名字/IP 复用，不新增
        call(a, HttpMethod.PUT, "/api/admin/lab/rooms/" + roomId + "/scheme", map("name", "方案机房72", "prefix", "T72-", "pad", 2,
                "baseIp", "10.72.0.16", "step", -1, "layoutText", "4 5 6\n1 2 3"));
        ws = workstationsByName(a, "T72-");
        assertThat(ws).hasSize(6);
        assertThat(ws.get("T72-01").get("ipAddress").asText()).isEqualTo("10.72.0.16");
        assertThat(ws.get("T72-06").get("ipAddress").asText()).isEqualTo("10.72.0.11");
        assertThat(call(a, HttpMethod.GET, "/api/admin/lab/rooms/" + roomId + "/scheme", null).get("cols").asInt()).isEqualTo(3);

        // 机位按 IP 优先复用：原 T72-06（.16）这台电脑现在叫 T72-01
        assertThat(ws.get("T72-01").get("id").asLong()).isNotEqualTo(id1);

        // 交换两台的 IP
        call(a, HttpMethod.POST, "/api/admin/lab/workstations/swap-ip", map("a", ws.get("T72-01").get("id").asLong(), "b", ws.get("T72-02").get("id").asLong()));
        ws = workstationsByName(a, "T72-");
        assertThat(ws.get("T72-01").get("ipAddress").asText()).isEqualTo("10.72.0.15");
        assertThat(ws.get("T72-02").get("ipAddress").asText()).isEqualTo("10.72.0.16");

        // 校验：座位号重复、IP 落到 .255
        assertThat(raw(a, HttpMethod.PUT, "/api/admin/lab/rooms/" + roomId + "/scheme", map("name", "x", "prefix", "T72-", "pad", 2,
                "baseIp", "10.72.0.11", "step", 1, "layoutText", "1 2\n2 3")).getBody()).contains("重复");
        assertThat(raw(a, HttpMethod.PUT, "/api/admin/lab/rooms/" + roomId + "/scheme", map("name", "x", "prefix", "T72-", "pad", 2,
                "baseIp", "10.72.0.250", "step", 1, "layoutText", "1 2 3 4 5 6 7 8")).getBody()).contains("不可用");
    }

    @Test
    void networkControllerCalls() throws Exception {
        List<String> hits = Collections.synchronizedList(new ArrayList<>());
        HttpServer ctr = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ctr.createContext("/", ex -> {
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            String path = ex.getRequestURI().toString();
            hits.add(ex.getRequestMethod() + " " + path);
            String body;
            int code = 200;
            if (!"Bearer tk72".equals(auth)) {
                code = 401;
                body = "{\"error\":\"unauthorized\"}";
            } else if (path.startsWith("/api/status")) {
                body = "{\"agents\":[{\"ip\":\"10.72.0.11\",\"hostname\":\"T72-01\",\"net_state\":\"normal\",\"is_online\":true},"
                        + "{\"ip\":\"10.72.0.12\",\"hostname\":\"T72-02\",\"net_state\":\"disconnect\",\"is_online\":false}]}";
            } else if (path.startsWith("/api/network/")) {
                code = 202;
                body = "{\"ok\":true}";
            } else {
                body = "{\"ok\":true}";
            }
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(code, b.length);
            try (OutputStream o = ex.getResponseBody()) {
                o.write(b);
            }
        });
        ctr.start();
        HttpHeaders a = admin();
        try {
            String url = "http://127.0.0.1:" + ctr.getAddress().getPort();
            call(a, HttpMethod.PUT, "/api/admin/settings", map("net.enabled", "true", "net.ctrUrl", url, "net.ctrToken", "tk72"));
            JsonNode t = call(a, HttpMethod.GET, "/api/admin/lab/network/test", null);
            assertThat(t.get("ok").asBoolean()).isTrue();
            assertThat(t.get("agents").asInt()).isEqualTo(2);
            assertThat(t.get("online").asInt()).isEqualTo(1);

            network.setIp("10.72.0.12", true);
            assertThat(hits).contains("POST /api/network/enable_ip?ip=10.72.0.12");
            assertThat(network.lastCommands()).containsEntry("10.72.0.12", true);
            assertThat(network.statusByIp().get("10.72.0.12").get("netState")).isEqualTo("disconnect");

            // 座位图：状态总览 + 批量断网（坏 IP 单独记失败，不影响其它）
            JsonNode st = call(a, HttpMethod.GET, "/api/admin/lab/network/status", null);
            assertThat(st.get("enabled").asBoolean()).isTrue();
            assertThat(st.get("reachable").asBoolean()).isTrue();
            assertThat(st.get("status").get("10.72.0.11").get("online").asBoolean()).isTrue();
            assertThat(st.get("last").get("10.72.0.12").asBoolean()).isTrue();
            JsonNode batch = call(a, HttpMethod.POST, "/api/admin/lab/network/batch",
                    map("action", "cut", "ips", new String[]{"10.72.0.11", "10.72.0.12", "not-an-ip"}));
            assertThat(batch.get("count").asInt()).isEqualTo(2);
            assertThat(batch.get("failed").toString()).isEqualTo("[\"not-an-ip\"]");
            assertThat(hits).contains("POST /api/network/disable_ip?ip=10.72.0.11");
            assertThat(network.lastCommands()).containsEntry("10.72.0.11", false).containsEntry("10.72.0.12", false);
            assertThat(raw(a, HttpMethod.POST, "/api/admin/lab/network/batch", map("action", "boom", "ips", new String[]{"10.72.0.11"}))
                    .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            // 教师课堂座位图：只看得到、只能操作本课堂机房的电脑（机房里是 .21/.22，.11 不在）
            long roomId = call(a, HttpMethod.POST, "/api/admin/lab/rooms/scheme", map("name", "课堂机房72", "prefix", "N72-", "pad", 2,
                    "baseIp", "10.72.0.21", "step", 1, "layoutText", "1 2")).get("id").asLong();
            long yearId = call(a, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2079", "label", "2079级", "active", true)).get("id").asLong();
            long classId = call(a, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "网控班", "displayOrder", 1)).get("id").asLong();
            call(a, HttpMethod.PUT, "/api/admin/lab/class-configs/" + classId, map("roomId", roomId, "fixedSeat", false));
            call(a, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "网控老师", "loginName", "net72_t", "password", "net-pass-1"));
            call(a, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "别的老师", "loginName", "net72_o", "password", "net-pass-1"));
            HttpHeaders teach = session("{\"loginType\":\"TEACHER\",\"loginName\":\"net72_t\",\"password\":\"net-pass-1\"}");
            HttpHeaders other = session("{\"loginType\":\"TEACHER\",\"loginName\":\"net72_o\",\"password\":\"net-pass-1\"}");
            long courseId = call(teach, HttpMethod.POST, "/api/teacher/courses", map("name", "网控课")).get("id").asLong();
            long lesson = call(teach, HttpMethod.POST, "/api/teacher/lessons", map("courseId", courseId, "classId", classId)).get("id").asLong();
            String base = "/api/teacher/lessons/" + lesson + "/network";
            JsonNode ts = call(teach, HttpMethod.GET, base + "/status", null);
            assertThat(ts.get("enabled").asBoolean()).isTrue();
            assertThat(ts.get("status").has("10.72.0.11")).isFalse();
            assertThat(ts.get("last").has("10.72.0.11")).isFalse();
            assertThat(raw(other, HttpMethod.GET, base + "/status", null).getBody()).contains("不是你的课堂");
            hits.clear();
            JsonNode tb = call(teach, HttpMethod.POST, base + "/batch", map("action", "open", "ips", new String[]{"10.72.0.21", "10.72.0.22"}));
            assertThat(tb.get("count").asInt()).isEqualTo(2);
            assertThat(hits).contains("POST /api/network/enable_ip?ip=10.72.0.21", "POST /api/network/enable_ip?ip=10.72.0.22");
            assertThat(raw(teach, HttpMethod.POST, base + "/batch", map("action", "cut", "ips", new String[]{"10.72.0.21", "10.72.0.11"})).getBody())
                    .contains("不是本课堂机房");
            assertThat(raw(other, HttpMethod.POST, base + "/batch", map("action", "cut", "ips", new String[]{"10.72.0.21"})).getBody())
                    .contains("不是你的课堂");
            assertThat(call(teach, HttpMethod.GET, base + "/status", null).get("last").get("10.72.0.21").asBoolean()).isTrue();
            assertThat(network.lastCommands()).containsEntry("10.72.0.11", false);
            call(teach, HttpMethod.POST, "/api/teacher/lessons/" + lesson + "/end", map());
            assertThat(raw(teach, HttpMethod.POST, base + "/batch", map("action", "cut", "ips", new String[]{"10.72.0.21"})).getBody())
                    .contains("课堂已结束");

            call(a, HttpMethod.PUT, "/api/admin/settings", map("net.ctrToken", "wrong"));
            assertThat(call(a, HttpMethod.GET, "/api/admin/lab/network/test", null).get("error").asText()).contains("令牌不正确");
            assertThatThrownBy(() -> network.setIp("10.72.0.12", false)).hasMessageContaining("令牌不正确");

            assertThat(raw(a, HttpMethod.PUT, "/api/admin/settings", map("net.ctrUrl", "ftp://x")).getStatusCode().is4xxClientError()).isTrue();
        } finally {
            call(a, HttpMethod.PUT, "/api/admin/settings", map("net.enabled", "false", "net.ctrUrl", "http://127.0.0.1:8770", "net.ctrToken", ""));
            jdbc.update("DELETE FROM lab_net_state WHERE ip_address LIKE '10.72.%'");
            ctr.stop(0);
        }
        assertThatThrownBy(() -> network.setIp("10.72.0.12", true)).hasMessageContaining("未启用");
    }

    // ---- 批量导入模板：下载 → 在模板里改 → 直接导入 ----

    private byte[] download(HttpHeaders s, String url) {
        ResponseEntity<byte[]> r = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(s), byte[].class);
        assertThat(r.getStatusCode()).as(url).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    /** 打开模板，按 edits（行号 → 列名 → 值）改示例行，返回改好的 xlsx；顺带确认第二张表是填写说明。 */
    private byte[] fill(byte[] xlsx, Map<Integer, Map<String, String>> edits) throws Exception {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(2);
            assertThat(wb.getSheetName(1)).isEqualTo("填写说明");
            Sheet s = wb.getSheetAt(0);
            Row h = s.getRow(0);
            for (Map.Entry<Integer, Map<String, String>> e : edits.entrySet()) {
                Row r = s.getRow(e.getKey()) == null ? s.createRow(e.getKey()) : s.getRow(e.getKey());
                for (Map.Entry<String, String> c : e.getValue().entrySet()) {
                    int col = -1;
                    for (int i = 0; i < h.getLastCellNum(); i++) if (c.getKey().equals(h.getCell(i).getStringCellValue())) col = i;
                    assertThat(col).as(c.getKey()).isGreaterThanOrEqualTo(0);
                    r.createCell(col).setCellValue(c.getValue());
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private JsonNode upload(HttpHeaders s, String url, byte[] xlsx) throws Exception {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(xlsx) { @Override public String getFilename() { return "filled.xlsx"; } });
        HttpHeaders mp = new HttpHeaders();
        mp.putAll(s);
        mp.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> r = rest.exchange(url, HttpMethod.POST, new HttpEntity<>(form, mp), String.class);
        assertThat(r.getStatusCode()).as(url + " → " + r.getBody()).isEqualTo(HttpStatus.OK);
        return om.readTree(r.getBody()).get("data");
    }

    private static Map<String, String> row(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void everyTemplateImportsAfterFillingIn() throws Exception {
        HttpHeaders a = admin();
        Map<Integer, Map<String, String>> st = new LinkedHashMap<>();
        st.put(1, row("姓名", "模板生甲", "学籍号", "TPL72001", "年级码", "2072", "班级", "模板班"));
        st.put(2, row("姓名", "模板生乙", "学籍号", "TPL72002", "年级码", "2072", "班级", "模板班"));
        JsonNode r = upload(a, "/api/admin/import/students", fill(download(a, "/api/admin/import/students/template"), st));
        assertThat(r.get("imported").asInt()).as(r.toString()).isEqualTo(2);

        Map<Integer, Map<String, String>> te = new LinkedHashMap<>();
        te.put(1, row("姓名", "模板老师", "登录名", "tpl72_t", "初始密码", "tpl-pass-1"));
        r = upload(a, "/api/admin/import/teachers", fill(download(a, "/api/admin/import/teachers/template"), te));
        assertThat(r.get("imported").asInt()).as(r.toString()).isEqualTo(1);

        Map<Integer, Map<String, String>> ws = new LinkedHashMap<>();
        ws.put(1, row("计算机名", "W72-1", "IP地址", "10.72.9.1"));
        ws.put(2, row("计算机名", "W72-2", "IP地址", "10.72.9.2"));
        ws.put(3, row("计算机名", "W72-3", "IP地址", "10.72.9.3"));
        r = upload(a, "/api/admin/lab/workstations/import", fill(download(a, "/api/admin/lab/workstations/template"), ws));
        assertThat(r.get("imported").asInt()).as(r.toString()).isEqualTo(3);

        // 题库模板的 4 道示例题原样即可导入
        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"tpl72_t\",\"password\":\"tpl-pass-1\"}");
        Map<Integer, Map<String, String>> q = new LinkedHashMap<>();
        q.put(5, row("题型", "判断题", "题干", "模板导入的判断题", "答案", "×"));
        r = upload(t, "/api/teacher/quiz/questions/import", fill(download(t, "/api/teacher/quiz/questions/template"), q));
        assertThat(r.get("imported").asInt()).as(r.toString()).isEqualTo(5);
    }
}
