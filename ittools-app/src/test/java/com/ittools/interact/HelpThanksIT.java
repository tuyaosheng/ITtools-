package com.ittools.interact;

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

/** 学生互助：登记“收到谁的帮助”、互助榜。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HelpThanksIT extends AbstractPostgresIT {
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
    void thankHelper() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2037", "label", "2037级", "active", true)).get("id").asLong();
        long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "互助班", "displayOrder", 1)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "互助老师", "loginName", "help_t", "password", "help-pass-1"));
        long a = student(admin, "求助生", "HLP001", classId, yearId);
        long b = student(admin, "热心生", "HLP002", classId, yearId);
        student(admin, "缺席生", "HLP003", classId, yearId);
        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"help_t\",\"password\":\"help-pass-1\"}");
        HttpHeaders sa = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"HLP001\",\"password\":\"msg-pass-1\"}");
        HttpHeaders sb = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"HLP002\",\"password\":\"msg-pass-1\"}");
        long course = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "互助课")).get("id").asLong();
        long lesson = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classId)).get("id").asLong();
        call(sa, HttpMethod.POST, "/api/student/lessons/" + lesson + "/join", map());
        call(sb, HttpMethod.POST, "/api/student/lessons/" + lesson + "/join", map());
        String base = "/api/student/interact/lessons/" + lesson;
        assertThat(call(sa, HttpMethod.GET, base, null).get("classmates").size()).as("只列出已进课的同学").isEqualTo(1);
        call(sa, HttpMethod.POST, base + "/help", map("message", "循环不会写"));
        fails(sa, HttpMethod.POST, base + "/help/thanks", map("helperId", a));   // 不能是自己
        call(sa, HttpMethod.POST, base + "/help/thanks", map("helperId", b));
        assertThat(call(sa, HttpMethod.GET, base, null).get("myHelp").isNull()).as("登记后求助结束").isTrue();
        call(sa, HttpMethod.POST, base + "/help/thanks", map("helperId", b));   // 没举手也可以直接登记
        JsonNode tv = call(t, HttpMethod.GET, "/api/teacher/interact/lessons/" + lesson, null);
        assertThat(tv.get("helpers").get(0).get("name").asText()).isEqualTo("热心生");
        assertThat(tv.get("helpers").get(0).get("count").asInt()).isEqualTo(2);
        assertThat(tv.get("helps").toString()).contains("热心生");
    }
}
