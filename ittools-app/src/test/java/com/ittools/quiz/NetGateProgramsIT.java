package com.ittools.quiz;

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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** 成绩开网同时要求作品页达标：测验和所选作品全部达标才开网。网络控制器（CTR）用本地假服务代替。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NetGateProgramsIT extends AbstractPostgresIT {
    @Autowired
    TestRestTemplate rest;
    final ObjectMapper om = new ObjectMapper();

    /** 假 CTR 里这台学生机的网络状态。 */
    private volatile String netState = "normal";

    private HttpServer fakeCtr() throws Exception {
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            if (path.endsWith("/enable_ip")) netState = "normal";
            if (path.endsWith("/disable_ip")) netState = "blocked";
            byte[] b = ("{\"ok\":true,\"agents\":[{\"ip\":\"127.0.0.1\",\"hostname\":\"pc\",\"is_online\":true,\"net_state\":\"" + netState + "\"}]}")
                    .getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        srv.start();
        return srv;
    }

    private void awaitNet(String want) throws Exception {
        for (int i = 0; i < 60 && !want.equals(netState); i++) Thread.sleep(500);
        assertThat(netState).isEqualTo(want);
    }

    /** 等过至少一轮定时对账（5 秒）后状态仍然不变。 */
    private void staysNet(String want) throws Exception {
        Thread.sleep(6500);
        assertThat(netState).isEqualTo(want);
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

    private JsonNode call(HttpHeaders s, HttpMethod m, String url, Object body) throws Exception {
        ResponseEntity<String> r = rest.exchange(url, m, new HttpEntity<>(body == null ? null : om.writeValueAsString(body), s), String.class);
        assertThat(r.getStatusCode()).as(url + " → " + r.getBody()).isEqualTo(HttpStatus.OK);
        return om.readTree(r.getBody()).get("data");
    }

    private void upload(HttpHeaders s, String url, String content) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) { @Override public String getFilename() { return "work.txt"; } });
        HttpHeaders mp = new HttpHeaders();
        mp.putAll(s);
        mp.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> r = rest.exchange(url, HttpMethod.POST, new HttpEntity<>(form, mp), String.class);
        assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.OK);
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private JsonNode row(HttpHeaders t, String gateUrl) throws Exception {
        return call(t, HttpMethod.GET, gateUrl, null).get("students").get(0);
    }

    @Test
    void opensOnlyWhenQuizAndProgramsAllPass() throws Exception {
        HttpServer ctr = fakeCtr();
        try {
            HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
            Map<String, String> settings = new LinkedHashMap<>();
            settings.put("net.enabled", "true");
            settings.put("net.ctrUrl", "http://127.0.0.1:" + ctr.getAddress().getPort());
            call(admin, HttpMethod.PUT, "/api/admin/settings", settings);
            long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2082", "label", "2082级", "active", true)).get("id").asLong();
            long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "开网班", "displayOrder", 1)).get("id").asLong();
            call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "开网老师", "loginName", "ng_t", "password", "ng-pass-1"));
            call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", "开网生", "studentNo", "01",
                    "xjh", "NG0001", "classId", classId, "enrollYearId", yearId, "password", "ng-pass-1"));
            HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"ng_t\",\"password\":\"ng-pass-1\"}");
            HttpHeaders s = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"NG0001\",\"password\":\"ng-pass-1\"}");

            long q = call(t, HttpMethod.POST, "/api/teacher/quiz/questions", map("qtype", "JUDGE", "stem", "1 字节 = 8 位", "answer", "T", "score", 5)).get("id").asLong();
            long paper = call(t, HttpMethod.POST, "/api/teacher/quiz/papers", map("name", "开网卷", "items", new Object[]{map("questionId", q, "score", 5)})).get("id").asLong();
            long course = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "开网课")).get("id").asLong();
            String pages = "/api/teacher/courses/" + course + "/pages";
            long hw1 = call(t, HttpMethod.POST, pages, map("title", "程序一", "pageType", "HOMEWORK", "studentVisible", true)).get("id").asLong();
            long hw2 = call(t, HttpMethod.POST, pages, map("title", "程序二", "pageType", "HOMEWORK", "studentVisible", true)).get("id").asLong();
            long quiz = call(t, HttpMethod.POST, pages, map("title", "习题", "pageType", "QUIZ", "studentVisible", true, "refId", paper)).get("id").asLong();
            Object[] rules = {map("type", "TEXT_CONTAINS", "param1", "搭好了", "score", 100)};
            call(t, HttpMethod.PUT, "/api/teacher/grading/pages/" + hw1 + "/rules", map("rules", rules));
            call(t, HttpMethod.PUT, "/api/teacher/grading/pages/" + hw2 + "/rules", map("rules", rules));

            long lesson = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classId)).get("id").asLong();
            call(s, HttpMethod.POST, "/api/student/lessons/" + lesson + "/join", map());
            String gate = "/api/teacher/quiz/lessons/" + lesson + "/net-gate";
            String up1 = "/api/student/homework/lessons/" + lesson + "/pages/" + hw1;
            String up2 = "/api/student/homework/lessons/" + lesson + "/pages/" + hw2;

            // 非作品页不能选；启用规则后没达标的学生断网
            ResponseEntity<String> bad = rest.exchange(gate, HttpMethod.PUT, new HttpEntity<>(om.writeValueAsString(
                    map("pageId", quiz, "passScore", 5, "maxTries", 3, "pageIds", new long[]{quiz}, "homeworkPass", 100)), t), String.class);
            assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            call(t, HttpMethod.PUT, gate, map("pageId", quiz, "passScore", 5, "maxTries", 3, "pageIds", new long[]{hw1, hw2}, "homeworkPass", 100));
            awaitNet("blocked");
            assertThat(call(t, HttpMethod.GET, gate, null).get("rule").get("pages").size()).isEqualTo(2);

            // 手动放行：学生测验达标后放行仍然保留（作品还没完成，不能被断网）
            String override = gate + "/students/" + row(t, gate).get("studentId").asLong() + "/override";
            call(t, HttpMethod.PUT, override, map("mode", "OPEN"));
            awaitNet("normal");
            long test = call(t, HttpMethod.GET, "/api/teacher/quiz/lessons/" + lesson + "/pages/" + quiz + "/test", null).get("id").asLong();
            call(t, HttpMethod.PUT, "/api/teacher/quiz/tests/" + test + "/status", map("status", "OPEN"));
            call(s, HttpMethod.GET, "/api/student/quiz/tests/" + test, null);
            call(s, HttpMethod.PUT, "/api/student/quiz/tests/" + test, map("answers", new Object[]{map("questionId", q, "answer", "T")}, "submit", true));
            JsonNode r = row(t, gate);
            assertThat(r.get("override").asText()).isEqualTo("OPEN");
            assertThat(r.get("passed").asBoolean()).isTrue();
            assertThat(r.get("state").asText()).isEqualTo("PROGRAM");
            staysNet("normal");

            // 恢复按成绩：测验达标但作品没交，断网；学生能看到还差什么
            call(t, HttpMethod.PUT, override, map("mode", (Object) null));
            awaitNet("blocked");
            JsonNode info = call(s, HttpMethod.GET, "/api/student/quiz/tests/" + test, null).get("netGate");
            assertThat(info.get("passed").asBoolean()).isTrue();
            assertThat(info.get("missingPrograms").toString()).contains("程序一").contains("程序二");

            // 交了一个达标、一个不达标（0 分）：仍然断网
            upload(s, up1, "我搭好了");
            upload(s, up2, "还没做");
            staysNet("blocked");
            r = row(t, gate);
            assertThat(r.get("programs").get(0).get("ok").asBoolean()).isTrue();
            assertThat(r.get("programs").get(1).get("ok").asBoolean()).isFalse();
            assertThat(r.get("programs").get(1).get("score").asDouble()).isEqualTo(0);
            assertThat(call(s, HttpMethod.GET, "/api/student/quiz/tests/" + test, null).get("netGate").get("missingPrograms").toString())
                    .doesNotContain("程序一").contains("程序二");

            // 重交达标：不用老师操作，下一轮对账自动开网
            upload(s, up2, "我也搭好了");
            awaitNet("normal");
            assertThat(row(t, gate).get("state").asText()).isEqualTo("PASS");

            // 老师发回作品：不再算达标，重新断网
            long sub2 = -1;
            for (JsonNode x : call(t, HttpMethod.GET, "/api/teacher/homework/lessons/" + lesson + "/pages/" + hw2, null).get("students")) {
                if (x.hasNonNull("id")) sub2 = x.get("id").asLong();
            }
            call(t, HttpMethod.POST, "/api/teacher/homework/submissions/" + sub2 + "/return", map("note", "再检查一下"));
            awaitNet("blocked");
            assertThat(row(t, gate).get("programs").get(1).get("returned").asBoolean()).isTrue();

            // 重交后老师打分优先于自动分：打 90 不够 100，断网；达标线降到 90 就开网
            upload(s, up2, "我也搭好了");
            awaitNet("normal");
            call(t, HttpMethod.PUT, "/api/teacher/homework/submissions/" + sub2 + "/grade", map("score", 90));
            awaitNet("blocked");
            call(t, HttpMethod.PUT, gate, map("pageId", quiz, "passScore", 5, "maxTries", 3, "pageIds", new long[]{hw1, hw2}, "homeworkPass", 90));
            awaitNet("normal");

            // 不选作品：和原来一样，只看测验
            call(t, HttpMethod.PUT, "/api/teacher/homework/submissions/" + sub2 + "/grade", map("score", 10));
            awaitNet("blocked");
            call(t, HttpMethod.PUT, gate, map("pageId", quiz, "passScore", 5, "maxTries", 3, "pageIds", new long[]{}, "homeworkPass", 100));
            awaitNet("normal");
            assertThat(call(t, HttpMethod.GET, gate, null).get("rule").get("pages").size()).isEqualTo(0);
            assertThat(row(t, gate).get("state").asText()).isEqualTo("PASS");
        } finally {
            ctr.stop(0);
        }
    }
}
