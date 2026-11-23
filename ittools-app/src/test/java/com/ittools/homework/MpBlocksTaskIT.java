package com.ittools.homework;

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

/**
 * 平台图形化编程：作品页旁边显示任务单（按标题关联，复制课程后还能对上，任务单页没打开时只能看到标题），
 * 学生随时练习的程序每人一份、自动保存。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MpBlocksTaskIT extends AbstractPostgresIT {
    @Autowired
    TestRestTemplate rest;
    final ObjectMapper om = new ObjectMapper();

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

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void taskSheetBesideEditorAndFreePractice() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2058", "label", "2058级", "active", true)).get("id").asLong();
        long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "八(6)班", "displayOrder", 1)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "任务单老师", "loginName", "ts_t", "password", "ts-pass-1"));
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", "任务单甲", "studentNo", "01", "xjh", "TS0001", "classId", classId, "enrollYearId", yearId, "password", "ts-pass-1"));
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", "任务单乙", "studentNo", "02", "xjh", "TS0002", "classId", classId, "enrollYearId", yearId, "password", "ts-pass-1"));
        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"ts_t\",\"password\":\"ts-pass-1\"}");
        HttpHeaders s1 = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"TS0001\",\"password\":\"ts-pass-1\"}");
        HttpHeaders s2 = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"TS0002\",\"password\":\"ts-pass-1\"}");

        long course = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "光线实验")).get("id").asLong();
        String pages = "/api/teacher/courses/" + course + "/pages";
        long sheet = call(t, HttpMethod.POST, pages, map("title", "实验任务单", "pageType", "SURVEY", "studentVisible", true, "content", "<p>第一步：读光线值</p>")).get("id").asLong();
        long hw = call(t, HttpMethod.POST, pages, map("title", "光线程序", "pageType", "HOMEWORK", "studentVisible", true)).get("id").asLong();
        JsonNode cfg = call(t, HttpMethod.PUT, "/api/teacher/homework/pages/" + hw + "/config", map("blockEditor", true, "taskPageTitle", "实验任务单"));
        assertThat(cfg.get("taskPageTitle").asText()).isEqualTo("实验任务单");
        // 没开图形化编程时任务单不保存
        long hw2 = call(t, HttpMethod.POST, pages, map("title", "普通作品", "pageType", "HOMEWORK", "studentVisible", true)).get("id").asLong();
        assertThat(call(t, HttpMethod.PUT, "/api/teacher/homework/pages/" + hw2 + "/config", map("blockEditor", false, "taskPageTitle", "实验任务单")).get("taskPageTitle").isNull()).isTrue();

        long lesson = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classId)).get("id").asLong();
        call(s1, HttpMethod.POST, "/api/student/lessons/" + lesson + "/join", map());
        call(t, HttpMethod.PUT, "/api/teacher/lessons/" + lesson + "/pages/" + hw, map("open", true));
        String task = "/api/student/mpblocks/lessons/" + lesson + "/pages/" + hw + "/task";
        // 任务单页没打开：只知道标题
        call(t, HttpMethod.PUT, "/api/teacher/lessons/" + lesson + "/pages/" + sheet, map("open", false));
        JsonNode t1 = call(s1, HttpMethod.GET, task, null);
        assertThat(t1.get("title").asText()).isEqualTo("实验任务单");
        assertThat(t1.get("pageId").asLong()).isEqualTo(sheet);
        assertThat(t1.get("pageType").asText()).isEqualTo("SURVEY");
        assertThat(t1.get("open").asBoolean()).isFalse();
        call(t, HttpMethod.PUT, "/api/teacher/lessons/" + lesson + "/pages/" + sheet, map("open", true));
        assertThat(call(s1, HttpMethod.GET, task, null).get("open").asBoolean()).isTrue();

        // 复制课程：任务单按标题还能对上（指向新课程里的页面）
        long copy = call(t, HttpMethod.POST, "/api/teacher/courses/" + course + "/copy", map()).get("id").asLong();
        JsonNode cp = call(t, HttpMethod.GET, "/api/teacher/courses/" + copy, null).get("pages");
        long newHw = 0;
        for (JsonNode p : cp) if ("光线程序".equals(p.get("title").asText())) newHw = p.get("id").asLong();
        assertThat(call(t, HttpMethod.GET, "/api/teacher/homework/pages/" + newHw + "/config", null).get("taskPageTitle").asText()).isEqualTo("实验任务单");

        // 改了任务单标题：学生这边显示“找不到”
        call(t, HttpMethod.PUT, "/api/teacher/pages/" + sheet, map("title", "新任务单", "pageType", "SURVEY", "studentVisible", true));
        assertThat(call(s1, HttpMethod.GET, task, null).get("missing").asBoolean()).isTrue();

        // ---- 随时练习：每人一份，自动保存 ----
        JsonNode f0 = call(s1, HttpMethod.GET, "/api/student/mpblocks/free", null);
        assertThat(f0.get("project").isNull()).isTrue();
        assertThat(f0.get("canSubmit").asBoolean()).isFalse();
        call(s1, HttpMethod.PUT, "/api/student/mpblocks/free", map("project", map("targets", new Object[0], "meta", map("semver", "3.0.0")), "code", "print(1)"));
        assertThat(call(s1, HttpMethod.GET, "/api/student/mpblocks/free", null).get("project").get("meta").get("semver").asText()).isEqualTo("3.0.0");
        assertThat(call(s2, HttpMethod.GET, "/api/student/mpblocks/free", null).get("project").isNull()).isTrue();
        // 老师不能用学生的接口
        assertThat(raw(t, HttpMethod.GET, "/api/student/mpblocks/free", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
