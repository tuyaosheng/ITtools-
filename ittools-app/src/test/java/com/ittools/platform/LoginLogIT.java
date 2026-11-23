package com.ittools.platform;

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

/** 登入记录：登录写入、退出补记时间、教师按班级和日期查询。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginLogIT extends AbstractPostgresIT {
    @Autowired
    TestRestTemplate rest;
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

    @Test
    void loginAndLogoutAreLogged() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2038", "label", "2038级", "active", true)).get("id").asLong();
        long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "日志班", "displayOrder", 1)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "日志老师", "loginName", "log_t", "password", "log-pass-1"));
        student(admin, "日志生", "LOG001", classId, yearId);
        HttpHeaders s = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"LOG001\",\"password\":\"msg-pass-1\"}");
        call(s, HttpMethod.POST, "/api/auth/logout", null);
        session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"LOG001\",\"password\":\"msg-pass-1\"}");   // 第二次登入不退出
        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"log_t\",\"password\":\"log-pass-1\"}");
        String today = java.time.LocalDate.now().toString();
        JsonNode logs = call(t, HttpMethod.GET, "/api/teacher/login-logs?classId=" + classId + "&from=" + today + "&to=" + today, null);
        assertThat(logs.size()).isEqualTo(2);
        assertThat(logs.get(0).get("logoutAt").isNull()).as("最近一次未退出").isTrue();
        assertThat(logs.get(1).get("logoutAt").isNull()).as("第一次已退出").isFalse();
        assertThat(call(t, HttpMethod.GET, "/api/teacher/login-logs?classId=" + classId + "&from=2000-01-01&to=2000-01-02", null).size()).isEqualTo(0);
        fails(t, HttpMethod.GET, "/api/teacher/login-logs?classId=" + classId + "&from=" + today + "&to=2000-01-01", null);
    }

    @Test
    void duplicateStudentLoginKicksOldSession() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2045", "label", "2045级", "active", true)).get("id").asLong();
        long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "单点班", "displayOrder", 1)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "单点老师", "loginName", "kick_t", "password", "kick-pass-1"));
        student(admin, "单点生", "KICK01", classId, yearId);
        String stu = "{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"KICK01\",\"password\":\"msg-pass-1\"}";
        HttpHeaders first = session(stu);
        call(first, HttpMethod.GET, "/api/auth/me", null);
        HttpHeaders second = session(stu);
        ResponseEntity<String> old = rest.exchange("/api/student/records", HttpMethod.GET, new HttpEntity<>(first), String.class);
        assertThat(old.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(old.getBody()).contains("其他电脑登录").contains("\"kicked\":true");
        call(second, HttpMethod.GET, "/api/student/records", null);

        // 教师可多处登录
        HttpHeaders t1 = session("{\"loginType\":\"TEACHER\",\"loginName\":\"kick_t\",\"password\":\"kick-pass-1\"}");
        session("{\"loginType\":\"TEACHER\",\"loginName\":\"kick_t\",\"password\":\"kick-pass-1\"}");
        call(t1, HttpMethod.GET, "/api/auth/me", null);
        // 管理员也可多处登录（第一处会话在第二处登录后仍有效）
        HttpHeaders a2 = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        call(admin, HttpMethod.GET, "/api/admin/settings", null);
        call(a2, HttpMethod.GET, "/api/admin/settings", null);

        // 关闭“学生单处登录”后两处都有效
        call(admin, HttpMethod.PUT, "/api/admin/settings", map("login.singleSession", "false"));
        try {
            HttpHeaders a = session(stu);
            HttpHeaders b = session(stu);
            call(a, HttpMethod.GET, "/api/student/records", null);
            call(b, HttpMethod.GET, "/api/student/records", null);
        } finally {
            call(admin, HttpMethod.PUT, "/api/admin/settings", map("login.singleSession", "true"));
        }
    }

    /** 登录时带上机房电脑 IP（平台按 X-Forwarded-For 第一个取客户端 IP）。 */
    private ResponseEntity<String> loginFrom(String body, String ip) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("X-Forwarded-For", ip);
        return rest.postForEntity("/api/auth/login", new HttpEntity<>(body, h), String.class);
    }

    /** 预期被拒绝的登录：TestRestTemplate 的 JDK 连接遇到 POST 401 会抛 HttpRetryException，这里直接用 HttpURLConnection 读状态和正文。 */
    private String[] rejectedLoginFrom(String body, String ip) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(rest.getRootUri() + "/api/auth/login").openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("X-Forwarded-For", ip);
        c.getOutputStream().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        int code = c.getResponseCode();
        java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return new String[]{String.valueOf(code), new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8)};
    }

    private HttpHeaders sessionFrom(String body, String ip) {
        ResponseEntity<String> login = loginFrom(body, ip);
        assertThat(login.getStatusCode()).as(body + " @" + ip + " → " + login.getBody()).isEqualTo(HttpStatus.OK);
        List<String> setCookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
        HttpHeaders sess = new HttpHeaders();
        sess.setContentType(MediaType.APPLICATION_JSON);
        sess.add("X-Forwarded-For", ip);
        sess.add(HttpHeaders.COOKIE, setCookies.stream().map(sc -> sc.split(";", 2)[0]).collect(Collectors.joining("; ")));
        for (String sc : setCookies) {
            String pair = sc.split(";", 2)[0].trim();
            if (pair.startsWith("XSRF-TOKEN=")) sess.add("X-XSRF-TOKEN", pair.substring("XSRF-TOKEN=".length()));
        }
        return sess;
    }

    @Test
    void keepFirstModeBlocksOtherComputersUntilTeacherKicks() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2093", "label", "2093级", "active", true)).get("id").asLong();
        long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "占用班", "displayOrder", 1)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "占用老师", "loginName", "keep_t", "password", "keep-pass-1"));
        long sid = student(admin, "占用生", "KEEP01", classId, yearId);
        String stu = "{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"KEEP01\",\"password\":\"msg-pass-1\"}";
        fails(admin, HttpMethod.PUT, "/api/admin/settings", map("login.singleMode", "WHATEVER"));
        call(admin, HttpMethod.PUT, "/api/admin/settings", map("login.singleMode", "KEEP_FIRST"));
        try {
            HttpHeaders a = sessionFrom(stu, "10.9.0.11");
            // 别的电脑登不上，提示被占用的电脑；原来那台不受影响
            String[] other = rejectedLoginFrom(stu, "10.9.0.12");
            assertThat(other[0]).isEqualTo("401");
            assertThat(other[1]).contains("已在 10.9.0.11 上登录").contains("强制下线");
            call(a, HttpMethod.GET, "/api/student/records", null);

            // 老师在班级名单里看到在线 IP
            HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"keep_t\",\"password\":\"keep-pass-1\"}");
            JsonNode list = call(t, HttpMethod.GET, "/api/teacher/classes/" + classId + "/students", null);
            assertThat(list.get(0).get("onlineIp").asText()).isEqualTo("10.9.0.11");

            // 同一台电脑可以重新登录（关了浏览器再开），旧会话下线
            HttpHeaders a2 = sessionFrom(stu, "10.9.0.11");
            ResponseEntity<String> old = rest.exchange("/api/student/records", HttpMethod.GET, new HttpEntity<>(a), String.class);
            assertThat(old.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            call(a2, HttpMethod.GET, "/api/student/records", null);

            // 老师强制下线后，旧电脑收到“老师已让你的账号下线”，学生可以在别的电脑登录
            assertThat(call(t, HttpMethod.POST, "/api/teacher/students/" + sid + "/kick", map()).asInt()).isEqualTo(1);
            ResponseEntity<String> kicked = rest.exchange("/api/student/records", HttpMethod.GET, new HttpEntity<>(a2), String.class);
            assertThat(kicked.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(kicked.getBody()).contains("老师已让你的账号下线").contains("\"kicked\":true");
            assertThat(call(t, HttpMethod.GET, "/api/teacher/classes/" + classId + "/students", null).get(0).get("onlineIp").isNull()).isTrue();
            assertThat(call(t, HttpMethod.POST, "/api/teacher/students/" + sid + "/kick", map()).asInt()).as("已不在线").isEqualTo(0);
            HttpHeaders b = sessionFrom(stu, "10.9.0.12");

            // 学生自己退出后，也能换电脑登录
            call(b, HttpMethod.POST, "/api/auth/logout", null);
            sessionFrom(stu, "10.9.0.13");
        } finally {
            call(admin, HttpMethod.PUT, "/api/admin/settings", map("login.singleMode", "KICK_OLD"));
        }
    }
}
