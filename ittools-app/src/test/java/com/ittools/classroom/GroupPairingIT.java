package com.ittools.classroom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ittools.support.AbstractPostgresIT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v20261121：课程归档；按学号分组（挨着排 / 间隔排）；小组角色与配对（门铃1组 ↔ 显示1组）；
 * 课程页按角色显示、{{组名}}{{配对号}} 占位符；成绩开网只算本组角色的页、可要求本对都完成；课程包带上可见角色。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GroupPairingIT extends AbstractPostgresIT {
    @Autowired
    TestRestTemplate rest;
    @Autowired
    JdbcTemplate db;
    final ObjectMapper om = new ObjectMapper();
    private final Map<String, String> net = new ConcurrentHashMap<>();

    private HttpServer fakeCtr() throws Exception {
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            String q = ex.getRequestURI().getQuery();
            String ip = q != null && q.startsWith("ip=") ? q.substring(3) : null;
            if (path.endsWith("/enable_ip")) net.put(ip, "normal");
            if (path.endsWith("/disable_ip")) net.put(ip, "blocked");
            String agents = net.entrySet().stream().map(e -> "{\"ip\":\"" + e.getKey() + "\",\"hostname\":\"pc\",\"is_online\":true,\"net_state\":\"" + e.getValue() + "\"}")
                    .collect(Collectors.joining(","));
            byte[] b = ("{\"ok\":true,\"agents\":[" + agents + "]}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        srv.start();
        return srv;
    }

    private void await(String ip, String want) throws Exception {
        for (int i = 0; i < 60 && !want.equals(net.get(ip)); i++) Thread.sleep(500);
        assertThat(net.get(ip)).as(ip).isEqualTo(want);
    }

    private void stays(String ip, String want) throws Exception {
        Thread.sleep(6500);
        assertThat(net.get(ip)).as(ip).isEqualTo(want);
    }

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

    private String fails(HttpHeaders s, HttpMethod m, String url, Object body) throws Exception {
        ResponseEntity<String> r = raw(s, m, url, body);
        assertThat(r.getStatusCode()).as(url + " → " + r.getBody()).isEqualTo(HttpStatus.BAD_REQUEST);
        return r.getBody();
    }

    private ResponseEntity<String> upload(HttpHeaders s, String url, byte[] data, String filename) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(data) { @Override public String getFilename() { return filename; } });
        HttpHeaders mp = new HttpHeaders();
        mp.putAll(s);
        mp.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(form, mp), String.class);
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static List<String> names(JsonNode group) {
        List<String> out = new ArrayList<>();
        group.get("members").forEach(m -> out.add(m.get("name").asText()));
        return out;
    }

    private static List<String> titles(JsonNode pages) {
        List<String> out = new ArrayList<>();
        pages.forEach(p -> out.add(p.get("title").asText()));
        return out;
    }

    private JsonNode row(HttpHeaders t, String gate, String name) throws Exception {
        for (JsonNode r : call(t, HttpMethod.GET, gate, null).get("students")) if (name.equals(r.get("name").asText())) return r;
        throw new AssertionError(name);
    }

    private void quiz(HttpHeaders s, long test, long q) throws Exception {
        call(s, HttpMethod.GET, "/api/student/quiz/tests/" + test, null);
        call(s, HttpMethod.PUT, "/api/student/quiz/tests/" + test, map("answers", new Object[]{map("questionId", q, "answer", "T")}, "submit", true));
    }

    private void work(HttpHeaders s, String url) {
        ResponseEntity<String> r = upload(s, url, "我搭好了".getBytes(StandardCharsets.UTF_8), "work.txt");
        assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void archiveGroupingRolesPairs() throws Exception {
        HttpServer ctr = fakeCtr();
        try {
            HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
            Map<String, String> settings = new LinkedHashMap<>();
            settings.put("net.enabled", "true");
            settings.put("net.ctrUrl", "http://127.0.0.1:" + ctr.getAddress().getPort());
            call(admin, HttpMethod.PUT, "/api/admin/settings", settings);
            long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2091", "label", "2091级", "active", true)).get("id").asLong();
            long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "配对班", "displayOrder", 1)).get("id").asLong();
            call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "配对老师", "loginName", "pr_t", "password", "pr-pass-1"));
            String[] names = {"一号", "二号", "三号", "四号", "五号", "六号"};
            List<HttpHeaders> st = new ArrayList<>();
            for (int i = 0; i < names.length; i++) {
                call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", names[i], "studentNo", "0" + (i + 1),
                        "xjh", "PR000" + i, "classId", classId, "enrollYearId", yearId, "password", "pr-pass-1"));
                st.add(session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"PR000" + i + "\",\"password\":\"pr-pass-1\"}"));
            }
            HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"pr_t\",\"password\":\"pr-pass-1\"}");

            // ---- 课程与按角色显示的页面 ----
            long q = call(t, HttpMethod.POST, "/api/teacher/quiz/questions", map("qtype", "JUDGE", "stem", "MQTT 要订阅同一个主题才能收到消息", "answer", "T", "score", 5)).get("id").asLong();
            long paper = call(t, HttpMethod.POST, "/api/teacher/quiz/papers", map("name", "配对卷", "items", new Object[]{map("questionId", q, "score", 5)})).get("id").asLong();
            long course = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "门铃配对课")).get("id").asLong();
            String pages = "/api/teacher/courses/" + course + "/pages";
            long info = call(t, HttpMethod.POST, pages, map("title", "任务说明", "pageType", "TEXT", "studentVisible", true,
                    "content", "<p>你们是{{组名}}，主题 {{MQTT项目}}/Bell{{配对号}}，角色：{{角色}}</p>")).get("id").asLong();
            JsonNode bellPage = call(t, HttpMethod.POST, pages, map("title", "门铃程序", "pageType", "HOMEWORK", "studentVisible", true, "visibleRoles", "门铃"));
            long bell = bellPage.get("id").asLong();
            assertThat(bellPage.get("visibleRoles").asText()).isEqualTo("门铃");
            long show = call(t, HttpMethod.POST, pages, map("title", "显示程序", "pageType", "HOMEWORK", "studentVisible", true,
                    "visibleRoles", new String[]{"显示", " 显示 "})).get("id").asLong();
            long quizPage = call(t, HttpMethod.POST, pages, map("title", "习题", "pageType", "QUIZ", "studentVisible", true, "refId", paper)).get("id").asLong();
            for (long hw : new long[]{bell, show}) {
                call(t, HttpMethod.PUT, "/api/teacher/grading/pages/" + hw + "/rules", map("rules", new Object[]{map("type", "TEXT_CONTAINS", "param1", "搭好了", "score", 100)}));
            }
            assertThat(call(t, HttpMethod.GET, "/api/teacher/courses/" + course, null).get("pages").get(2).get("visibleRoles").asText()).isEqualTo("显示");

            // ---- 课程归档：不在列表、不能开课；取消归档后恢复 ----
            call(t, HttpMethod.POST, "/api/teacher/courses/" + course + "/archive", map("archived", true));
            assertThat(call(t, HttpMethod.GET, "/api/teacher/courses", null).findValuesAsText("name")).doesNotContain("门铃配对课");
            JsonNode all = call(t, HttpMethod.GET, "/api/teacher/courses?includeArchived=true", null);
            assertThat(all.get(0).get("archived").asBoolean()).isTrue();
            assertThat(fails(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classId))).contains("已归档");
            assertThat(call(t, HttpMethod.GET, "/api/teacher/courses/" + course, null).get("pages").size()).as("归档不动页面").isEqualTo(4);
            call(t, HttpMethod.POST, "/api/teacher/courses/" + course + "/archive", map("archived", false));
            assertThat(call(t, HttpMethod.GET, "/api/teacher/courses", null).findValuesAsText("name")).contains("门铃配对课");

            long lesson = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classId)).get("id").asLong();
            String tg = "/api/teacher/groups/lessons/" + lesson, sg = "/api/student/groups/lessons/" + lesson;

            // ---- 按学号分组（还没进课的也分好）----
            JsonNode ov = call(t, HttpMethod.POST, tg + "/by-number", map("size", 3, "mode", "INTERLEAVED"));
            assertThat(ov.get("groups").size()).isEqualTo(2);
            assertThat(names(ov.get("groups").get(0))).containsExactly("一号", "三号", "五号");
            ov = call(t, HttpMethod.POST, tg + "/by-number", map("size", 3, "mode", "CONSECUTIVE"));
            assertThat(names(ov.get("groups").get(0))).containsExactly("一号", "二号", "三号");
            assertThat(names(ov.get("groups").get(1))).containsExactly("四号", "五号", "六号");
            for (int i = 0; i < st.size(); i++) call(st.get(i), HttpMethod.POST, "/api/student/lessons/" + lesson + "/join", map());
            for (int i = 0; i < names.length; i++) {
                String ip = "10.8.0." + (i + 1);
                db.update("UPDATE lesson_attendance SET ip_address = ? WHERE lesson_id = ? AND student_id = (SELECT id FROM users WHERE name = ? AND class_id = ?)",
                        ip, lesson, names[i], classId);
                net.put(ip, "normal");
            }
            assertThat(call(st.get(0), HttpMethod.GET, sg, null).get("me").get("groupNo").asInt()).as("进课后预分组生效").isEqualTo(1);

            // ---- 一键生成角色：门铃1组、显示1组 ----
            ov = call(t, HttpMethod.POST, tg + "/auto-roles", map("roles", "门铃,显示"));
            assertThat(ov.get("groups").get(0).get("label").asText()).isEqualTo("门铃1组");
            assertThat(ov.get("groups").get(1).get("label").asText()).isEqualTo("显示1组");
            assertThat(ov.get("pairCount").asInt()).isEqualTo(1);
            assertThat(call(st.get(4), HttpMethod.GET, "/api/student/lessons/current", null).get("myAttendance").get("groupLabel").asText()).isEqualTo("显示1组");
            JsonNode att = call(t, HttpMethod.GET, "/api/teacher/lessons/" + lesson + "/attendance", null);
            assertThat(att.get(0).get("groupLabel").asText()).isEqualTo("门铃1组");

            // ---- 页面按角色显示 + 占位符 ----
            assertThat(titles(call(st.get(0), HttpMethod.GET, "/api/student/lessons/" + lesson + "/pages", null))).containsExactly("任务说明", "门铃程序", "习题");
            assertThat(titles(call(st.get(3), HttpMethod.GET, "/api/student/lessons/" + lesson + "/pages", null))).containsExactly("任务说明", "显示程序", "习题");
            assertThat(fails(st.get(0), HttpMethod.GET, "/api/student/lessons/" + lesson + "/pages/" + show, null)).contains("不是给你们组的");
            String content = call(st.get(3), HttpMethod.GET, "/api/student/lessons/" + lesson + "/pages/" + info, null).get("content").asText();
            assertThat(content).contains("你们是显示1组").contains("/Bell1").contains("角色：显示").doesNotContain("{{");

            // ---- 学生整组选角色：同号同角色只能一个组 ----
            fails(st.get(0), HttpMethod.POST, sg + "/role", map("role", "显示", "pairNo", 1));          // 还没开放
            call(t, HttpMethod.PUT, tg + "/settings", map("roleOpen", true));
            call(t, HttpMethod.POST, tg + "/clear-roles", null);
            call(st.get(1), HttpMethod.POST, sg + "/role", map("role", "显示", "pairNo", 1));             // 二号替第 1 组选
            assertThat(call(st.get(0), HttpMethod.GET, sg, null).get("me").get("groupLabel").asText()).as("整组都变").isEqualTo("显示1组");
            assertThat(fails(st.get(4), HttpMethod.POST, sg + "/role", map("role", "显示", "pairNo", 1))).contains("已经被");
            fails(st.get(4), HttpMethod.POST, sg + "/role", map("role", "广播", "pairNo", 1));          // 没有这个角色
            fails(st.get(4), HttpMethod.POST, sg + "/role", map("role", "门铃", "pairNo", 2));          // 超出配对号
            call(st.get(4), HttpMethod.POST, sg + "/role", map("role", "门铃", "pairNo", 1));
            ov = call(t, HttpMethod.GET, tg, null);
            assertThat(names(ov.get("groups").get(0))).as("组员不变").containsExactly("一号", "二号", "三号");
            assertThat(ov.get("groups").get(1).get("label").asText()).isEqualTo("门铃1组");
            // 老师可以手改组名，也可以让两组同号（三组共用一个主题时）
            call(t, HttpMethod.PUT, tg + "/groups/2/role", map("role", "门铃", "pairNo", 1, "name", "叮咚队"));
            assertThat(call(t, HttpMethod.GET, tg, null).get("groups").get(1).get("label").asText()).isEqualTo("叮咚队");
            call(t, HttpMethod.PUT, tg + "/groups/2/role", map("role", "门铃", "pairNo", 1));

            // ---- 成绩开网：只算本组角色的作品；要求本对都完成 ----
            String gate = "/api/teacher/quiz/lessons/" + lesson + "/net-gate";
            call(t, HttpMethod.PUT, gate, map("pageId", quizPage, "passScore", 5, "maxTries", 3, "pageIds", new long[]{bell, show},
                    "homeworkPass", 100, "requirePair", true));
            await("10.8.0.1", "blocked");
            long test = call(t, HttpMethod.GET, "/api/teacher/quiz/lessons/" + lesson + "/pages/" + quizPage + "/test", null).get("id").asLong();
            call(t, HttpMethod.PUT, "/api/teacher/quiz/tests/" + test + "/status", map("status", "OPEN"));
            String showUp = "/api/student/homework/lessons/" + lesson + "/pages/" + show;
            String bellUp = "/api/student/homework/lessons/" + lesson + "/pages/" + bell;
            // 显示1组三人都完成：门铃作品不用交；但搭档门铃1组还没完成 → 仍断网
            for (int i = 0; i < 3; i++) {
                quiz(st.get(i), test, q);
                work(st.get(i), showUp);
            }
            stays("10.8.0.1", "blocked");
            JsonNode r1 = row(t, gate, "一号");
            assertThat(r1.get("state").asText()).isEqualTo("PAIR");
            assertThat(r1.get("programs").get(0).get("na").asBoolean()).as("门铃程序不是显示组的").isTrue();
            assertThat(r1.get("groupLabel").asText()).isEqualTo("显示1组");
            JsonNode prog = call(st.get(0), HttpMethod.GET, "/api/student/lessons/" + lesson + "/net-gate/progress", null);
            assertThat(prog.get("members").get(0).get("done").asBoolean()).isTrue();
            assertThat(prog.get("pair").get("partners").get(0).get("label").asText()).isEqualTo("门铃1组");
            assertThat(prog.get("pair").get("partners").get(0).get("members").get(0).get("missing").toString()).contains("测验").contains("门铃程序");
            JsonNode pairs = call(t, HttpMethod.GET, "/api/teacher/lessons/" + lesson + "/pairs", null);
            assertThat(pairs.size()).isEqualTo(1);
            assertThat(pairs.get(0).get("groups").size()).isEqualTo(2);
            assertThat(pairs.get(0).get("done").asBoolean()).isFalse();
            // 门铃1组也做完 → 两组一起开网
            for (int i = 3; i < 6; i++) {
                quiz(st.get(i), test, q);
                work(st.get(i), bellUp);
            }
            await("10.8.0.1", "normal");
            await("10.8.0.6", "normal");
            assertThat(call(t, HttpMethod.GET, "/api/teacher/lessons/" + lesson + "/pairs", null).get(0).get("done").asBoolean()).isTrue();

            // ---- 课程包导出导入保留可见角色 ----
            ResponseEntity<byte[]> ex = rest.exchange("/api/teacher/courses/" + course + "/pack", HttpMethod.GET, new HttpEntity<>(t), byte[].class);
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.OK);
            ResponseEntity<String> imp = upload(t, "/api/teacher/courses/import-pack", ex.getBody(), "门铃配对课.ittools.zip");
            assertThat(imp.getStatusCode()).as(imp.getBody()).isEqualTo(HttpStatus.OK);
            long nc = om.readTree(imp.getBody()).get("data").get("course").get("id").asLong();
            JsonNode np = call(t, HttpMethod.GET, "/api/teacher/courses/" + nc, null).get("pages");
            assertThat(np.get(1).get("visibleRoles").asText()).isEqualTo("门铃");
            assertThat(np.get(2).get("visibleRoles").asText()).isEqualTo("显示");
            assertThat(np.get(0).get("visibleRoles").isNull()).isTrue();
            // 复制课程也保留
            long cp = call(t, HttpMethod.POST, "/api/teacher/courses/" + course + "/copy", null).get("id").asLong();
            assertThat(call(t, HttpMethod.GET, "/api/teacher/courses/" + cp, null).get("pages").get(1).get("visibleRoles").asText()).isEqualTo("门铃");
        } finally {
            ctr.stop(0);
        }
    }
}
