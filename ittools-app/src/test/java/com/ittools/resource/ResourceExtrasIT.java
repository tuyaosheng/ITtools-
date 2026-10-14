package com.ittools.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** 第7批：阅读计时、学生个人站点、指法练习与排行榜、教师管理学生网盘、教案打包导入。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResourceExtrasIT extends AbstractPostgresIT {
    @Autowired
    TestRestTemplate rest;
    @org.springframework.boot.test.web.server.LocalServerPort
    int port;
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

    private ResponseEntity<String> raw(HttpHeaders s, HttpMethod m, String url, Object body) throws Exception {
        return rest.exchange(url, m, new HttpEntity<>(body == null ? null : om.writeValueAsString(body), s), String.class);
    }

    private JsonNode call(HttpHeaders s, HttpMethod m, String url, Object body) throws Exception {
        ResponseEntity<String> r = raw(s, m, url, body);
        assertThat(r.getStatusCode()).as(url + " → " + r.getBody()).isEqualTo(HttpStatus.OK);
        return om.readTree(r.getBody()).get("data");
    }

    private void fails(HttpHeaders s, HttpMethod m, String url, Object body) throws Exception {
        assertThat(raw(s, m, url, body).getStatusCode()).as(url).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private long student(HttpHeaders admin, String name, String xjh, long classId, long yearId) throws Exception {
        return call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", name, "studentNo", xjh.substring(xjh.length() - 2),
                "xjh", xjh, "classId", classId, "enrollYearId", yearId, "password", "msg-pass-1")).get("id").asLong();
    }

    private static byte[] zip(java.nio.charset.Charset cs, String... nameContent) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(bos, cs)) {
            for (int i = 0; i < nameContent.length; i += 2) {
                z.putNextEntry(new java.util.zip.ZipEntry(nameContent[i]));
                z.write(nameContent[i + 1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    private ResponseEntity<String> upload(HttpHeaders s, String url, byte[] data, String filename) {
        org.springframework.util.MultiValueMap<String, Object> form = new org.springframework.util.LinkedMultiValueMap<>();
        form.add("file", new org.springframework.core.io.ByteArrayResource(data) { @Override public String getFilename() { return filename; } });
        HttpHeaders mp = new HttpHeaders();
        mp.putAll(s);
        mp.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(form, mp), String.class);
    }

    private ResponseEntity<String> form(HttpHeaders s, String url, Map<String, Object> fields, String fileField, byte[] data, String filename) {
        org.springframework.util.MultiValueMap<String, Object> form = new org.springframework.util.LinkedMultiValueMap<>();
        for (Map.Entry<String, Object> e : fields.entrySet()) form.add(e.getKey(), String.valueOf(e.getValue()));
        if (data != null) form.add(fileField, new org.springframework.core.io.ByteArrayResource(data) { @Override public String getFilename() { return filename; } });
        HttpHeaders mp = new HttpHeaders();
        mp.putAll(s);
        mp.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(form, mp), String.class);
    }

    @Test
    void resourceExtras() throws Exception {
        java.nio.charset.Charset utf8 = java.nio.charset.StandardCharsets.UTF_8;
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2071", "label", "2071级", "active", true)).get("id").asLong();
        long classA = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "资源1班", "displayOrder", 1)).get("id").asLong();
        long classB = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "资源2班", "displayOrder", 2)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "资源老师", "loginName", "rx_t", "password", "rx-pass-1"));
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "另一老师", "loginName", "rx_o", "password", "rx-pass-1"));
        long s1 = student(admin, "读书郎", "RXS001", classA, yearId);
        student(admin, "隔壁班", "RXS102", classB, yearId);
        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"rx_t\",\"password\":\"rx-pass-1\"}");
        HttpHeaders o = session("{\"loginType\":\"TEACHER\",\"loginName\":\"rx_o\",\"password\":\"rx-pass-1\"}");
        HttpHeaders a = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"RXS001\",\"password\":\"msg-pass-1\"}");
        HttpHeaders b = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"RXS102\",\"password\":\"msg-pass-1\"}");

        // ---- 阅读计时 ----
        JsonNode res = om.readTree(form(t, "/api/teacher/resources", map("title", "网络安全读本", "category", "课外"), "file",
                "<html><body>阅读内容</body></html>".getBytes(utf8), "read.html").getBody()).get("data");
        long rid = res.get("id").asLong();
        call(t, HttpMethod.PUT, "/api/teacher/resources/" + rid + "/classes", map("classIds", new long[]{classA}));
        fails(b, HttpMethod.POST, "/api/student/resources/" + rid + "/open", null);
        call(a, HttpMethod.POST, "/api/student/resources/" + rid + "/open", null);
        Thread.sleep(1200);
        JsonNode beat = call(a, HttpMethod.POST, "/api/student/resources/" + rid + "/read", null);
        assertThat(beat.get("seconds").asInt()).isEqualTo(1);
        assertThat(call(a, HttpMethod.GET, "/api/student/resources/reading", null).get(String.valueOf(rid)).get("views").asInt()).isEqualTo(1);
        JsonNode rep = call(t, HttpMethod.GET, "/api/teacher/resources/" + rid + "/reading?classId=" + classA, null);
        assertThat(rep.get(0).get("seconds").asInt()).isEqualTo(1);
        fails(o, HttpMethod.GET, "/api/teacher/resources/" + rid + "/reading?classId=" + classA, null);

        // ---- 学生个人站点：沙箱响应、替换旧站点、班级展示、教师删除 ----
        assertThat(form(a, "/api/student/site", map("title", "我的小站"), "file", "x".getBytes(utf8), "site.rar").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode site = om.readTree(form(a, "/api/student/site", map("title", "我的小站"), "file",
                zip(utf8, "mysite/index.html", "<h1>HELLO</h1>", "mysite/css/a.css", "h1{}"), "site.zip").getBody()).get("data").get("site");
        String url = site.get("url").asText();
        assertThat(url).matches("/site/[0-9a-f]{32}/index\\.html");
        ResponseEntity<String> page = rest.getForEntity(url, String.class);
        assertThat(page.getBody()).isEqualTo("<h1>HELLO</h1>");
        assertThat(page.getHeaders().getFirst("Content-Security-Policy")).startsWith("sandbox");
        assertThat(page.getHeaders().getFirst("Content-Security-Policy")).doesNotContain("allow-same-origin");
        JsonNode site2 = om.readTree(form(a, "/api/student/site", map("title", "新站"), "file", zip(utf8, "home.htm", "<p>V2</p>"), "v2.zip").getBody()).get("data").get("site");
        assertThat(site2.get("url").asText()).endsWith("/home.htm");
        assertThat(rest.getForEntity(url, String.class).getStatusCode()).as("旧站点删除").isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(a, HttpMethod.GET, "/api/sites/class", null).get(0).get("title").asText()).isEqualTo("新站");
        assertThat(call(b, HttpMethod.GET, "/api/sites/class", null).size()).as("只看本班").isEqualTo(0);
        assertThat(call(t, HttpMethod.GET, "/api/sites/class?classId=" + classA, null).size()).isEqualTo(1);
        call(admin, HttpMethod.PUT, "/api/admin/settings", map("site.maxMb", "1"));
        try {
            byte[] big = new byte[1100 * 1024];
            new java.util.Random(1).nextBytes(big);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(bos)) {
                z.setLevel(0);
                z.putNextEntry(new java.util.zip.ZipEntry("index.html"));
                z.write(big);
                z.closeEntry();
            }
            assertThat(form(a, "/api/student/site", map(), "file", bos.toByteArray(), "big.zip").getStatusCode()).as("超过站点大小").isEqualTo(HttpStatus.BAD_REQUEST);
        } finally {
            call(admin, HttpMethod.PUT, "/api/admin/settings", map("site.maxMb", "20"));
        }
        call(t, HttpMethod.DELETE, "/api/teacher/sites/" + s1, null);
        assertThat(call(a, HttpMethod.GET, "/api/student/site", null).get("site").isNull()).isTrue();

        // ---- 指法练习与排行榜 ----
        fails(a, HttpMethod.POST, "/api/student/typing", map("level", "HIGH", "durationSec", 60, "typedChars", 5000, "correctChars", 5000, "words", 900));
        fails(a, HttpMethod.POST, "/api/student/typing", map("level", "HIGH", "durationSec", 10, "typedChars", 50, "correctChars", 50, "words", 9));
        JsonNode ty = call(a, HttpMethod.POST, "/api/student/typing", map("level", "HIGH", "durationSec", 60, "typedChars", 200, "correctChars", 190, "words", 30));
        assertThat(ty.get("speed").asInt() + "/" + ty.get("accuracy").asDouble()).isEqualTo("190/95.0");
        call(b, HttpMethod.POST, "/api/student/typing", map("level", "HIGH", "durationSec", 60, "typedChars", 300, "correctChars", 290, "words", 40));
        call(b, HttpMethod.POST, "/api/student/typing", map("level", "HIGH", "durationSec", 60, "typedChars", 400, "correctChars", 200, "words", 40));  // 正确率低不上榜
        JsonNode lb = call(a, HttpMethod.GET, "/api/typing/leaderboard?level=HIGH&yearId=" + yearId, null);
        assertThat(lb.get(0).get("name").asText() + lb.get(0).get("speed").asInt()).isEqualTo("隔壁班290");
        assertThat(lb.get(1).get("rank").asInt()).isEqualTo(2);
        assertThat(call(a, HttpMethod.GET, "/api/typing/leaderboard?level=HIGH&classId=" + classA, null).size()).isEqualTo(1);
        assertThat(call(a, HttpMethod.GET, "/api/student/typing", null).size()).isEqualTo(1);

        // ---- 教师管理学生网盘 ----
        form(a, "/api/disk", map("title", "游戏"), "file", new byte[2048], "game.exe.txt");
        JsonNode disks = call(t, HttpMethod.GET, "/api/teacher/disks/classes/" + classA, null);
        assertThat(disks.get(0).get("files").asInt()).isEqualTo(1);
        long fileId = call(t, HttpMethod.GET, "/api/teacher/disks/students/" + s1, null).get(0).get("id").asLong();
        call(t, HttpMethod.DELETE, "/api/teacher/disks/students/" + s1 + "/files/" + fileId + "?penalty=true", null);
        JsonNode myDisk = call(a, HttpMethod.GET, "/api/disk", null);
        assertThat(myDisk.get("penaltyBytes").asLong()).isEqualTo(2048);
        assertThat(myDisk.get("files").size()).isEqualTo(0);
        call(t, HttpMethod.PUT, "/api/teacher/disks/students/" + s1 + "/policy", map("disabled", true));
        assertThat(form(a, "/api/disk", map(), "file", new byte[10], "a.txt").getBody()).contains("网盘已被老师关闭");
        call(t, HttpMethod.PUT, "/api/teacher/disks/students/" + s1 + "/policy", map("disabled", false, "resetPenalty", true));
        assertThat(form(a, "/api/disk", map(), "file", new byte[10], "a.txt").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(a, HttpMethod.GET, "/api/disk", null).get("penaltyBytes").asLong()).isEqualTo(0);

        // ---- 教案打包 / 导入 ----
        org.springframework.util.MultiValueMap<String, Object> pf = new org.springframework.util.LinkedMultiValueMap<>();
        pf.add("title", "网络基础教案");
        pf.add("content", "<p>教学目标</p>");
        pf.add("doc", new org.springframework.core.io.ByteArrayResource("教学设计".getBytes(utf8)) { @Override public String getFilename() { return "设计.docx"; } });
        HttpHeaders mp = new HttpHeaders();
        mp.putAll(t);
        mp.setContentType(MediaType.MULTIPART_FORM_DATA);
        long plan = om.readTree(rest.exchange("/api/teacher/lesson-plans", HttpMethod.POST, new HttpEntity<>(pf, mp), String.class).getBody()).get("data").get("id").asLong();
        ResponseEntity<byte[]> pack = rest.exchange("/api/teacher/lesson-plans/" + plan + "/pack", HttpMethod.GET, new HttpEntity<>(t), byte[].class);
        assertThat(pack.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.exchange("/api/teacher/lesson-plans/" + plan + "/pack", HttpMethod.GET, new HttpEntity<>(o), String.class).getStatusCode())
                .as("私有教案别人不能导出").isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode imported = om.readTree(form(o, "/api/teacher/lesson-plans/import", map(), "file", pack.getBody(), "网络基础教案.教案.zip").getBody());
        long np = imported.get("data").asLong();
        JsonNode plans = call(o, HttpMethod.GET, "/api/teacher/lesson-plans", null);
        JsonNode mine = null;
        for (JsonNode x : plans) if (x.get("id").asLong() == np) mine = x;
        assertThat(mine.get("title").asText() + mine.get("docName").asText()).isEqualTo("网络基础教案设计.docx");
        assertThat(rest.exchange("/api/teacher/lesson-plans/" + np + "/doc", HttpMethod.GET, new HttpEntity<>(o), String.class).getBody()).isEqualTo("教学设计");
        assertThat(form(o, "/api/teacher/lesson-plans/import", map(), "file", zip(utf8, "x.txt", "x"), "bad.zip").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
