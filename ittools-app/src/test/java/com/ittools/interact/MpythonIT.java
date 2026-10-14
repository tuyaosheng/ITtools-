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

/** 掌控板编程（MPYTHON）：替代 server.py 的接口——组号由服务器决定、进度上传、板子事件、教师模拟、初始积木、成绩。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MpythonIT extends AbstractPostgresIT {
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

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private long student(HttpHeaders admin, String name, String xjh, long classId, long yearId) throws Exception {
        return call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", name, "studentNo", xjh.substring(xjh.length() - 2),
                "xjh", xjh, "classId", classId, "enrollYearId", yearId, "password", "mpy-pass-1")).get("id").asLong();
    }

    @Test
    void groupsProgressEventsStartersAndScores() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2074", "label", "2074级", "active", true)).get("id").asLong();
        long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "掌控1班", "displayOrder", 1)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "掌控老师", "loginName", "mpy_t", "password", "mpy-pass-1"));
        long s1 = student(admin, "掌控甲", "MPY001", classId, yearId);
        long s2 = student(admin, "掌控乙", "MPY002", classId, yearId);
        long s3 = student(admin, "掌控丙", "MPY003", classId, yearId);
        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"mpy_t\",\"password\":\"mpy-pass-1\"}");
        HttpHeaders a = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"MPY001\",\"password\":\"mpy-pass-1\"}");
        HttpHeaders b = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"MPY002\",\"password\":\"mpy-pass-1\"}");
        HttpHeaders c = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"MPY003\",\"password\":\"mpy-pass-1\"}");

        long course = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "掌控板课")).get("id").asLong();
        long page = call(t, HttpMethod.POST, "/api/teacher/courses/" + course + "/pages",
                map("title", "掌控板闯关", "pageType", "MPYTHON", "studentVisible", true, "score", 10)).get("id").asLong();

        // ---- 课程编辑里的设置与初始积木（没有课堂） ----
        String pg = "/api/teacher/mpy/pages/" + page;
        assertThat(call(t, HttpMethod.GET, pg + "/config", null).get("lesson").asText()).isEqualTo("公开课");
        assertThat(call(t, HttpMethod.POST, pg + "/settings", map("lesson", "随便")).get("ok").asBoolean()).isFalse();
        JsonNode set = call(t, HttpMethod.POST, pg + "/settings", map("lesson", "公开课", "mode", "GROUP"));
        assertThat(set.get("ok").asBoolean()).isTrue();
        assertThat(call(t, HttpMethod.POST, pg + "/starter", map("demo", "../x", "state", map())).get("ok").asBoolean()).isFalse();
        JsonNode st = call(t, HttpMethod.POST, pg + "/starter", map("demo", "demo1", "state", map("blocks", map("languageVersion", 0))));
        assertThat(st.get("version").asText()).isNotEmpty();
        assertThat(call(t, HttpMethod.GET, pg + "/config", null).get("starterVersions").get("demo1").asText()).isEqualTo(st.get("version").asText());

        // ---- 开课、进课、分组：甲乙第 2 组，丙没分组 ----
        long lesson = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classId)).get("id").asLong();
        for (HttpHeaders s : new HttpHeaders[]{a, b, c}) call(s, HttpMethod.POST, "/api/student/lessons/" + lesson + "/join", map());
        call(t, HttpMethod.PUT, "/api/teacher/lessons/" + lesson + "/attendance/" + s1, map("groupNo", 2));
        call(t, HttpMethod.PUT, "/api/teacher/lessons/" + lesson + "/attendance/" + s2, map("groupNo", 2));

        String su = "/api/student/mpy/lessons/" + lesson + "/pages/" + page;
        String tu = "/api/teacher/mpy/lessons/" + lesson + "/pages/" + page;
        JsonNode meA = call(a, HttpMethod.GET, su + "/config", null).get("me");
        assertThat(meA.get("key").asText()).isEqualTo("2");
        assertThat(meA.get("label").isNull()).isTrue();
        JsonNode meC = call(c, HttpMethod.GET, su + "/config", null).get("me");
        assertThat(meC.get("key").asText()).as("没分组按人").isEqualTo("s" + s3);
        assertThat(meC.get("label").asText()).isEqualTo("掌控丙");
        assertThat(call(a, HttpMethod.GET, su + "/starter?demo=demo1", null).get("version").asText()).isEqualTo(st.get("version").asText());

        // ---- 上传进度：组号以服务器为准（伪造的 group 被忽略）；通关记录只增不减；刷入计次 ----
        call(a, HttpMethod.POST, su + "/code", map("group", "9", "code", "print(1)\n", "action", "save", "level", "demo1", "levelNo", 1,
                "passed", new String[]{"demo1"}, "filled", new Object[]{map("label", "x", "ok", true)}, "ws", map("blocks", map())));
        call(b, HttpMethod.POST, su + "/code", map("code", "print(2)\n", "action", "edit", "level", "demo2", "levelNo", 2, "passed", new String[]{}));
        call(c, HttpMethod.POST, su + "/code", map("code", "", "action", "open", "level", "demo1", "levelNo", 1));
        JsonNode mine = call(b, HttpMethod.GET, su + "/state", null).get("state");
        assertThat(mine.has("9")).isFalse();
        assertThat(mine.get("2").get("passed").toString()).isEqualTo("[\"demo1\"]");
        assertThat(mine.get("2").get("runs").asInt()).isEqualTo(1);
        assertThat(mine.get("2").get("code").asText()).isEqualTo("print(2)\n");
        assertThat(mine.has("s" + s3)).as("学生只看得到自己组").isFalse();

        // ---- 板子事件：学生转发（usb）、教师模拟（sim）；暗号格式校验；学生不能模拟 ----
        call(c, HttpMethod.GET, su + "/event?type=dark", null);
        assertThat(raw(c, HttpMethod.GET, su + "/event?type=DARK!", null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        call(t, HttpMethod.GET, tu + "/event?group=2&type=light", null);
        assertThat(raw(a, HttpMethod.GET, tu + "/event?group=2&type=dark", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        JsonNode ts = call(t, HttpMethod.GET, tu + "/state", null);
        List<String> keys = ts.get("keys").findValuesAsText("key");
        assertThat(keys).containsExactly("2", "s" + s3);
        assertThat(ts.get("keys").get(1).get("label").asText()).isEqualTo("掌控丙");
        assertThat(ts.get("state").get("2").get("lamp").asText()).isEqualTo("light");
        assertThat(ts.get("state").get("2").get("events").get(0).get("src").asText()).isEqualTo("sim");
        assertThat(ts.get("state").get("s" + s3).get("lamp").asText()).isEqualTo("dark");
        assertThat(ts.get("state").get("s" + s3).get("events").get(0).get("src").asText()).isEqualTo("usb");

        // ---- 成绩：公开课 5 关，过 1 关 × 页面 10 分 = 2 分；同组同分 ----
        JsonNode scores = call(t, HttpMethod.GET, "/api/teacher/lessons/" + lesson + "/scores", null);
        JsonNode col = null;
        for (JsonNode cc : scores.get("columns")) if ("MPYTHON".equals(cc.get("type").asText())) col = cc;
        assertThat(col).isNotNull();
        assertThat(col.get("full").asDouble()).isEqualTo(10.0);
        Map<Long, Double> got = new LinkedHashMap<>();
        for (JsonNode r : scores.get("rows")) {
            JsonNode v = r.get("scores").get(col.get("key").asText());
            if (v != null && !v.isNull()) got.put(r.get("studentId").asLong(), v.asDouble());
        }
        assertThat(got).containsEntry(s1, 2.0).containsEntry(s2, 2.0).containsEntry(s3, 0.0);

        // ---- 改成按人：每人一份 ----
        call(t, HttpMethod.POST, tu + "/settings", map("mode", "PERSON"));
        assertThat(call(a, HttpMethod.GET, su + "/config", null).get("me").get("key").asText()).isEqualTo("s" + s1);
        assertThat(call(t, HttpMethod.GET, tu + "/state", null).get("keys").findValuesAsText("key"))
                .as("按人列全班（按学号），之前按组留下的进度排在后面").containsExactly("s" + s1, "s" + s2, "s" + s3, "2");
    }
}
