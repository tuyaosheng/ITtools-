package com.ittools.classroom;

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

/** 应急工具：全部强制下课、消除缺勤记录（不再计入出勤率）、撤销课堂、强制删除有使用记录的课程。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmergencyToolsIT extends AbstractPostgresIT {
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
    void emergencyTools() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2046", "label", "2046级", "active", true)).get("id").asLong();
        long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "应急班", "displayOrder", 1)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "应急老师", "loginName", "em_t", "password", "em-pass-1"));
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "别的老师", "loginName", "em_o", "password", "em-pass-1"));
        student(admin, "来了", "EMG001", classId, yearId);
        long s2 = student(admin, "没来", "EMG002", classId, yearId);
        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"em_t\",\"password\":\"em-pass-1\"}");
        HttpHeaders o = session("{\"loginType\":\"TEACHER\",\"loginName\":\"em_o\",\"password\":\"em-pass-1\"}");
        HttpHeaders s1 = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"EMG001\",\"password\":\"msg-pass-1\"}");
        long course = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "应急课")).get("id").asLong();
        long l1 = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classId)).get("id").asLong();
        call(s1, HttpMethod.POST, "/api/student/lessons/" + l1 + "/join", map());
        call(t, HttpMethod.POST, "/api/teacher/lessons/" + l1 + "/end", map());

        // 消除缺勤：该节课不再计入“没来”的出勤率
        assertThat(raw(t, HttpMethod.GET, "/api/admin/tools/absences?classId=" + classId, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode abs = call(admin, HttpMethod.GET, "/api/admin/tools/absences?classId=" + classId, null);
        assertThat(abs.size()).isEqualTo(1);
        assertThat(abs.get(0).get("name").asText()).isEqualTo("没来");
        JsonNode r2 = call(t, HttpMethod.GET, "/api/teacher/reports/class/summary?classId=" + classId, null).get(1);
        assertThat(r2.get("attendancePct").asDouble()).isEqualTo(0.0);
        assertThat(call(admin, HttpMethod.POST, "/api/admin/tools/absences/clear",
                map("items", new Object[]{map("lessonId", l1, "studentId", s2)})).asInt()).isEqualTo(1);
        assertThat(call(admin, HttpMethod.GET, "/api/admin/tools/absences?classId=" + classId, null).size()).isEqualTo(0);
        r2 = call(t, HttpMethod.GET, "/api/teacher/reports/class/summary?classId=" + classId, null).get(1);
        assertThat(r2.get("lessons").asInt()).isEqualTo(0);
        assertThat(r2.get("attendancePct").isNull()).isTrue();

        // 全部强制下课
        long l2 = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classId)).get("id").asLong();
        assertThat(call(admin, HttpMethod.POST, "/api/admin/tools/end-all", null).asInt()).isGreaterThanOrEqualTo(1);
        assertThat(call(t, HttpMethod.GET, "/api/teacher/lessons/" + l2, null).get("status").asText()).isEqualTo("ENDED");

        // 撤销课堂：只能撤销自己的
        fails(o, HttpMethod.DELETE, "/api/teacher/lessons/" + l2, null);
        call(t, HttpMethod.DELETE, "/api/teacher/lessons/" + l2, null);
        fails(t, HttpMethod.GET, "/api/teacher/lessons/" + l2, null);

        // 有开课记录的课程：教师不能删，管理员可强制删除
        fails(t, HttpMethod.DELETE, "/api/teacher/courses/" + course, null);
        assertThat(call(admin, HttpMethod.GET, "/api/admin/tools/courses?keyword=应急", null).get(0).get("lessonCount").asInt()).isEqualTo(1);
        assertThat(call(admin, HttpMethod.GET, "/api/admin/tools/courses", null).size()).as("不带关键字列出全部").isGreaterThanOrEqualTo(1);
        assertThat(call(admin, HttpMethod.DELETE, "/api/admin/tools/courses/" + course, null).asInt()).isEqualTo(1);
        fails(t, HttpMethod.GET, "/api/teacher/courses/" + course, null);
        fails(t, HttpMethod.GET, "/api/teacher/lessons/" + l1, null);
    }
}
