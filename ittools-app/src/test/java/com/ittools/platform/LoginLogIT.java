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
}
