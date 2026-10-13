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

/** 学情监控：课堂心跳累计各页面停留时间与访问次数。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PageDwellIT extends AbstractPostgresIT {
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
    void dwellTime() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2042", "label", "2042级", "active", true)).get("id").asLong();
        long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "监控班", "displayOrder", 1)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "监控老师", "loginName", "dw_t", "password", "dw-pass-1"));
        student(admin, "专心生", "DWL001", classId, yearId);
        student(admin, "未进课", "DWL002", classId, yearId);
        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"dw_t\",\"password\":\"dw-pass-1\"}");
        HttpHeaders sa = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"DWL001\",\"password\":\"msg-pass-1\"}");
        long course = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "监控课")).get("id").asLong();
        long p1 = call(t, HttpMethod.POST, "/api/teacher/courses/" + course + "/pages", map("title", "导入", "pageType", "TEXT", "studentVisible", true)).get("id").asLong();
        long p2 = call(t, HttpMethod.POST, "/api/teacher/courses/" + course + "/pages", map("title", "练习", "pageType", "TEXT", "studentVisible", true)).get("id").asLong();
        long other = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "别的课")).get("id").asLong();
        long foreign = call(t, HttpMethod.POST, "/api/teacher/courses/" + other + "/pages", map("title", "外来页", "pageType", "TEXT", "studentVisible", true)).get("id").asLong();
        long lesson = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classId)).get("id").asLong();
        call(sa, HttpMethod.POST, "/api/student/lessons/" + lesson + "/join", map());
        String hb = "/api/student/interact/lessons/" + lesson + "/heartbeat";

        call(sa, HttpMethod.POST, hb, map("pageId", p1, "pageTitle", "导入"));
        Thread.sleep(1200);
        call(sa, HttpMethod.POST, hb, map("pageId", p2, "pageTitle", "练习"));     // 导入页计 1 秒，练习页访问 1 次
        Thread.sleep(1200);
        call(sa, HttpMethod.POST, hb, map("pageId", foreign, "pageTitle", "外来页")); // 练习页计 1 秒；外来页不属于本课，忽略
        Thread.sleep(1200);
        call(sa, HttpMethod.POST, hb, map("pageId", p1, "pageTitle", "导入"));     // 外来页不计时；导入页第 2 次访问

        JsonNode d = call(t, HttpMethod.GET, "/api/teacher/interact/lessons/" + lesson + "/dwell", null);
        assertThat(d.get("pages").size()).isEqualTo(2);
        assertThat(d.get("rows").size()).as("只列出进课学生").isEqualTo(1);
        JsonNode cells = d.get("rows").get(0).get("cells");
        assertThat(cells.get(String.valueOf(p1)).get("seconds").asInt()).isEqualTo(1);
        assertThat(cells.get(String.valueOf(p1)).get("visits").asInt()).isEqualTo(2);
        assertThat(cells.get(String.valueOf(p2)).get("seconds").asInt()).isEqualTo(1);
        assertThat(cells.get(String.valueOf(p2)).get("visits").asInt()).isEqualTo(1);
        assertThat(cells.has(String.valueOf(foreign))).isFalse();
        assertThat(d.get("rows").get(0).get("total").asInt()).isEqualTo(2);

        // 浏览器切到后台：仍上报心跳（在线），但这段时间不计停留
        call(sa, HttpMethod.POST, hb, map("pageId", p2, "pageTitle", "练习", "visible", false));
        Thread.sleep(1200);
        call(sa, HttpMethod.POST, hb, map("pageId", p2, "pageTitle", "练习", "visible", true));
        d = call(t, HttpMethod.GET, "/api/teacher/interact/lessons/" + lesson + "/dwell", null);
        cells = d.get("rows").get(0).get("cells");
        assertThat(cells.get(String.valueOf(p2)).get("seconds").asInt()).as("后台时间不计").isEqualTo(1);
        JsonNode mon = call(t, HttpMethod.GET, "/api/teacher/interact/lessons/" + lesson, null).get("monitor");
        assertThat(mon.get(0).get("online").asBoolean()).isTrue();
    }
}
