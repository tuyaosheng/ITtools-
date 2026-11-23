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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** 每组人数上限、进组后不能自己换组、小组全员完成才开网。网络控制器（CTR）用本地假服务代替，每个学生一台电脑。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NetGateGroupIT extends AbstractPostgresIT {
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

    private JsonNode row(HttpHeaders t, String gate, String name) throws Exception {
        for (JsonNode r : call(t, HttpMethod.GET, gate, null).get("students")) {
            if (name.equals(r.get("name").asText())) return r;
        }
        throw new AssertionError(name);
    }

    @Test
    void groupLimitLockAndWholeGroupGate() throws Exception {
        HttpServer ctr = fakeCtr();
        try {
            HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
            Map<String, String> settings = new LinkedHashMap<>();
            settings.put("net.enabled", "true");
            settings.put("net.ctrUrl", "http://127.0.0.1:" + ctr.getAddress().getPort());
            call(admin, HttpMethod.PUT, "/api/admin/settings", settings);
            long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2083", "label", "2083级", "active", true)).get("id").asLong();
            long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "小组班", "displayOrder", 1)).get("id").asLong();
            call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "小组老师", "loginName", "gp_t", "password", "gp-pass-1"));
            String[] names = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛"};
            List<HttpHeaders> st = new ArrayList<>();
            for (int i = 0; i < names.length; i++) {
                call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", names[i], "studentNo", "0" + (i + 1),
                        "xjh", "GP000" + i, "classId", classId, "enrollYearId", yearId, "password", "gp-pass-1"));
                st.add(session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"GP000" + i + "\",\"password\":\"gp-pass-1\"}"));
            }
            HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"gp_t\",\"password\":\"gp-pass-1\"}");

            long q = call(t, HttpMethod.POST, "/api/teacher/quiz/questions", map("qtype", "JUDGE", "stem", "1 字节 = 8 位（小组）", "answer", "T", "score", 5)).get("id").asLong();
            long paper = call(t, HttpMethod.POST, "/api/teacher/quiz/papers", map("name", "小组卷", "items", new Object[]{map("questionId", q, "score", 5)})).get("id").asLong();
            long course = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "小组课")).get("id").asLong();
            String pages = "/api/teacher/courses/" + course + "/pages";
            long hw = call(t, HttpMethod.POST, pages, map("title", "程序一", "pageType", "HOMEWORK", "studentVisible", true)).get("id").asLong();
            long quiz = call(t, HttpMethod.POST, pages, map("title", "习题", "pageType", "QUIZ", "studentVisible", true, "refId", paper)).get("id").asLong();
            call(t, HttpMethod.PUT, "/api/teacher/grading/pages/" + hw + "/rules", map("rules", new Object[]{map("type", "TEXT_CONTAINS", "param1", "搭好了", "score", 100)}));

            long lesson = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classId)).get("id").asLong();
            for (int i = 0; i < st.size(); i++) {
                call(st.get(i), HttpMethod.POST, "/api/student/lessons/" + lesson + "/join", map());
            }
            // 测试都从本机发起，IP 相同；这里给每人一台电脑
            for (int i = 0; i < names.length; i++) {
                String ip = "10.9.0." + (i + 1);
                db.update("UPDATE lesson_attendance SET ip_address = ? WHERE lesson_id = ? AND student_id = (SELECT x.student_id FROM lesson_attendance x JOIN users u ON u.id = x.student_id WHERE x.lesson_id = lesson_attendance.lesson_id AND u.name = ?)", ip, lesson, names[i]);
                net.put(ip, "normal");
            }
            String tg = "/api/teacher/groups/lessons/" + lesson;
            String sg = "/api/student/groups/lessons/" + lesson;

            // 没设上限：不锁定，可以退出，其他课的行为不变
            JsonNode over = call(t, HttpMethod.PUT, tg + "/settings", map("groupCount", 3, "joinOpen", true));
            assertThat(over.get("maxSize").asInt()).isEqualTo(0);
            assertThat(over.get("locked").asBoolean()).isFalse();
            call(st.get(0), HttpMethod.POST, sg + "/join", map("groupNo", 1));
            call(st.get(0), HttpMethod.POST, sg + "/join", map("groupNo", 2));
            call(st.get(0), HttpMethod.POST, sg + "/join", map("groupNo", 0));

            // 上限 3：第 4 人加入失败；设上限不锁定换组
            over = call(t, HttpMethod.PUT, tg + "/settings", map("maxSize", 3));
            assertThat(over.get("locked").asBoolean()).isFalse();
            for (int i = 0; i < 3; i++) call(st.get(i), HttpMethod.POST, sg + "/join", map("groupNo", 1));
            ResponseEntity<String> full = raw(st.get(3), HttpMethod.POST, sg + "/join", map("groupNo", 1));
            assertThat(full.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(full.getBody()).contains("已满");
            assertThat(call(st.get(3), HttpMethod.GET, sg, null).get("groups").get(0).get("full").asBoolean()).isTrue();

            // 开放期间可以自己换组、退出再回来
            call(st.get(0), HttpMethod.POST, sg + "/join", map("groupNo", 3));
            call(st.get(0), HttpMethod.POST, sg + "/join", map("groupNo", 0));
            call(st.get(0), HttpMethod.POST, sg + "/join", map("groupNo", 1));
            // 关闭自由选组后不能再加入或换组
            call(t, HttpMethod.PUT, tg + "/settings", map("joinOpen", false));
            ResponseEntity<String> closed = raw(st.get(0), HttpMethod.POST, sg + "/join", map("groupNo", 3));
            assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(closed.getBody()).contains("已关闭");
            assertThat(raw(st.get(4), HttpMethod.POST, sg + "/join", map("groupNo", 3)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            call(t, HttpMethod.PUT, tg + "/settings", map("joinOpen", true));

            // 四人同时抢第 2 组：只进 3 个
            ExecutorService pool = Executors.newFixedThreadPool(4);
            List<Future<HttpStatus>> futures = new ArrayList<>();
            for (int i = 4; i < 8; i++) {
                HttpHeaders s = st.get(i);
                futures.add(pool.submit(() -> HttpStatus.valueOf(raw(s, HttpMethod.POST, sg + "/join", map("groupNo", 2)).getStatusCodeValue())));
            }
            int joined = 0;
            for (Future<HttpStatus> f : futures) if (f.get() == HttpStatus.OK) joined++;
            pool.shutdown();
            assertThat(joined).isEqualTo(3);
            assertThat(call(t, HttpMethod.GET, tg, null).get("groups").get(1).get("members").size()).isEqualTo(3);

            // 老师调人不受上限限制，调完调回去
            long dingId = 0;
            for (JsonNode m : call(t, HttpMethod.GET, tg, null).get("ungrouped")) {
                if ("丁".equals(m.get("name").asText())) dingId = m.get("studentId").asLong();
            }
            assertThat(call(t, HttpMethod.PUT, tg + "/members/" + dingId, map("groupNo", 1)).get("groups").get(0).get("members").size()).isEqualTo(4);
            call(t, HttpMethod.PUT, tg + "/members/" + dingId, map("groupNo", 0));

            // 启用：全组完成才开网
            String gate = "/api/teacher/quiz/lessons/" + lesson + "/net-gate";
            call(t, HttpMethod.PUT, gate, map("pageId", quiz, "passScore", 5, "maxTries", 3, "pageIds", new long[]{hw}, "homeworkPass", 100, "requireGroup", true));
            await("10.9.0.1", "blocked");
            await("10.9.0.2", "blocked");
            await("10.9.0.3", "blocked");
            // 开网要求全组完成时锁定：不能自己换组或退出
            assertThat(call(t, HttpMethod.GET, tg, null).get("locked").asBoolean()).isTrue();
            assertThat(raw(st.get(0), HttpMethod.POST, sg + "/join", map("groupNo", 3)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(raw(st.get(0), HttpMethod.POST, sg + "/join", map("groupNo", 0)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            long test = call(t, HttpMethod.GET, "/api/teacher/quiz/lessons/" + lesson + "/pages/" + quiz + "/test", null).get("id").asLong();
            call(t, HttpMethod.PUT, "/api/teacher/quiz/tests/" + test + "/status", map("status", "OPEN"));
            String up = "/api/student/homework/lessons/" + lesson + "/pages/" + hw;

            // 甲、乙做完，丙只交了卷：全组还是断网，甲看到丙差什么
            for (int i = 0; i < 2; i++) finish(st.get(i), test, q, up);
            quizOnly(st.get(2), test, q);
            stays("10.9.0.1", "blocked");
            assertThat(row(t, gate, "甲").get("state").asText()).isEqualTo("GROUP");
            assertThat(row(t, gate, "甲").get("groupNo").asInt()).isEqualTo(1);
            assertThat(row(t, gate, "丙").get("state").asText()).isEqualTo("PROGRAM");
            JsonNode info = call(st.get(0), HttpMethod.GET, "/api/student/quiz/tests/" + test, null).get("netGate");
            assertThat(info.get("requireGroup").asBoolean()).isTrue();
            assertThat(info.get("open").asBoolean()).isFalse();
            assertThat(info.get("waiting").size()).isEqualTo(1);
            assertThat(info.get("waiting").get(0).get("name").asText()).isEqualTo("丙");
            assertThat(info.get("waiting").get(0).get("missing").toString()).contains("程序一").doesNotContain("测验");
            // 学生“完成情况”：甲看到全组三人，丙还差程序一，自己排第一个
            JsonNode prog = call(st.get(0), HttpMethod.GET, "/api/student/lessons/" + lesson + "/net-gate/progress", null);
            assertThat(prog.get("enabled").asBoolean()).isTrue();
            assertThat(prog.get("groupNo").asInt()).isEqualTo(1);
            assertThat(prog.get("members").size()).isEqualTo(3);
            assertThat(prog.get("members").get(0).get("me").asBoolean()).isTrue();
            for (JsonNode m : prog.get("members")) {
                if ("丙".equals(m.get("name").asText())) {
                    assertThat(m.get("done").asBoolean()).isFalse();
                    assertThat(m.get("quizPassed").asBoolean()).isTrue();
                    assertThat(m.get("missing").toString()).contains("程序一");
                    assertThat(m.get("missingItems").get(0).get("title").asText()).isEqualTo("程序一");
                    assertThat(m.get("missingItems").get(0).get("type").asText()).isEqualTo("HOMEWORK");
                } else {
                    assertThat(m.get("done").asBoolean()).as(m.toString()).isTrue();
                }
            }

            // 丙交程序：全组一起开网
            upload(st.get(2), up, "我搭好了");
            await("10.9.0.1", "normal");
            await("10.9.0.2", "normal");
            await("10.9.0.3", "normal");
            assertThat(row(t, gate, "甲").get("state").asText()).isEqualTo("PASS");
            assertThat(call(st.get(0), HttpMethod.GET, "/api/student/quiz/tests/" + test, null).get("netGate").get("open").asBoolean()).isTrue();

            // 没进组的丁做完也不开网
            finish(st.get(3), test, q, up);
            stays("10.9.0.4", "blocked");
            assertThat(row(t, gate, "丁").get("state").asText()).isEqualTo("NO_GROUP");

            // 第 2 组：有一人没做，做完的等；手动放行没做的人后，他算完成，全组开网
            List<Integer> second = new ArrayList<>();
            for (JsonNode m : call(t, HttpMethod.GET, tg, null).get("groups").get(1).get("members")) {
                for (int i = 4; i < 8; i++) if (names[i].equals(m.get("name").asText())) second.add(i);
            }
            finish(st.get(second.get(0)), test, q, up);
            finish(st.get(second.get(1)), test, q, up);
            String doneIp = "10.9.0." + (second.get(0) + 1);
            String lazyName = names[second.get(2)];
            stays(doneIp, "blocked");
            call(t, HttpMethod.PUT, gate + "/students/" + row(t, gate, lazyName).get("studentId").asLong() + "/override", map("mode", "OPEN"));
            await(doneIp, "normal");
            await("10.9.0." + (second.get(2) + 1), "normal");

            // 恢复按成绩：又等他；他请假后不算在组内，其他人开网
            call(t, HttpMethod.PUT, gate + "/students/" + row(t, gate, lazyName).get("studentId").asLong() + "/override", map("mode", (Object) null));
            await(doneIp, "blocked");
            db.update("UPDATE lesson_attendance SET status = 'LEAVE' WHERE lesson_id = ? AND student_id = (SELECT x.student_id FROM lesson_attendance x JOIN users u ON u.id = x.student_id WHERE x.lesson_id = lesson_attendance.lesson_id AND u.name = ?)", lesson, lazyName);
            await(doneIp, "normal");

            // 老师把已完成的第 1 组的人调走再调入没做完的同学：全组重新判定，断网
            db.update("UPDATE lesson_attendance SET status = 'PRESENT' WHERE lesson_id = ? AND student_id = (SELECT x.student_id FROM lesson_attendance x JOIN users u ON u.id = x.student_id WHERE x.lesson_id = lesson_attendance.lesson_id AND u.name = ?)", lesson, lazyName);
            long lazyId = row(t, gate, lazyName).get("studentId").asLong();
            call(t, HttpMethod.PUT, tg + "/members/" + lazyId, map("groupNo", 1));
            await("10.9.0.1", "blocked");

            // 关掉要求：回到按个人判定
            call(t, HttpMethod.PUT, gate, map("pageId", quiz, "passScore", 5, "maxTries", 3, "pageIds", new long[]{hw}, "homeworkPass", 100, "requireGroup", false));
            await("10.9.0.1", "normal");
            await("10.9.0.4", "normal");
            assertThat(row(t, gate, "丁").get("state").asText()).isEqualTo("PASS");
            assertThat(call(t, HttpMethod.GET, gate, null).get("rule").get("requireGroup").asBoolean()).isFalse();
        } finally {
            ctr.stop(0);
        }
    }

    /** 重新开课：下课后还原到上课中，撤回自动记的缺席，恢复下课时自动停用的成绩开网规则。 */
    @Test
    void reopenEndedLesson() throws Exception {
        HttpServer ctr = fakeCtr();
        try {
            HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
            Map<String, String> settings = new LinkedHashMap<>();
            settings.put("net.enabled", "true");
            settings.put("net.ctrUrl", "http://127.0.0.1:" + ctr.getAddress().getPort());
            call(admin, HttpMethod.PUT, "/api/admin/settings", settings);
            long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2095", "label", "2095级", "active", true)).get("id").asLong();
            long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "重开班", "displayOrder", 1)).get("id").asLong();
            call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "重开老师", "loginName", "ro_t", "password", "ro-pass-1"));
            for (int i = 0; i < 2; i++) {
                call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", i == 0 ? "来了" : "没来", "studentNo", "0" + (i + 1),
                        "xjh", "RO000" + i, "classId", classId, "enrollYearId", yearId, "password", "ro-pass-1"));
            }
            HttpHeaders s0 = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"RO0000\",\"password\":\"ro-pass-1\"}");
            HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"ro_t\",\"password\":\"ro-pass-1\"}");
            long q = call(t, HttpMethod.POST, "/api/teacher/quiz/questions", map("qtype", "JUDGE", "stem", "1 字节 = 8 位（重开）", "answer", "T", "score", 5)).get("id").asLong();
            long paper = call(t, HttpMethod.POST, "/api/teacher/quiz/papers", map("name", "重开卷", "items", new Object[]{map("questionId", q, "score", 5)})).get("id").asLong();
            long course = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "重开课")).get("id").asLong();
            long quiz = call(t, HttpMethod.POST, "/api/teacher/courses/" + course + "/pages", map("title", "习题", "pageType", "QUIZ", "studentVisible", true, "refId", paper)).get("id").asLong();
            long lesson = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classId)).get("id").asLong();
            call(s0, HttpMethod.POST, "/api/student/lessons/" + lesson + "/join", map());
            String gate = "/api/teacher/quiz/lessons/" + lesson + "/net-gate";
            call(t, HttpMethod.PUT, gate, map("pageId", quiz, "passScore", 5, "maxTries", 3));
            // 学生“完成情况”：只有自己，习题还没做
            JsonNode prog = call(s0, HttpMethod.GET, "/api/student/lessons/" + lesson + "/net-gate/progress", null);
            assertThat(prog.get("enabled").asBoolean()).isTrue();
            assertThat(prog.get("members").size()).isEqualTo(1);
            assertThat(prog.get("members").get(0).get("quizTried").asBoolean()).isFalse();

            call(t, HttpMethod.POST, "/api/teacher/lessons/" + lesson + "/end", map());
            assertThat(db.queryForObject("SELECT COUNT(*) FROM lesson_attendance WHERE lesson_id = ? AND status = 'ABSENT'", Long.class, lesson)).isEqualTo(1);
            for (int i = 0; i < 60 && call(t, HttpMethod.GET, gate, null).get("rule").get("enabled").asBoolean(); i++) Thread.sleep(500);
            assertThat(call(t, HttpMethod.GET, gate, null).get("rule").get("enabled").asBoolean()).as("下课后规则自动停用").isFalse();

            JsonNode re = call(t, HttpMethod.POST, "/api/teacher/lessons/" + lesson + "/reopen", map());
            assertThat(re.get("status").asText()).isEqualTo("ACTIVE");
            assertThat(re.get("endedAt").isNull()).isTrue();
            assertThat(db.queryForObject("SELECT COUNT(*) FROM lesson_attendance WHERE lesson_id = ? AND status = 'ABSENT'", Long.class, lesson)).as("自动缺席撤回").isEqualTo(0);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM lesson_attendance WHERE lesson_id = ? AND status = 'PRESENT'", Long.class, lesson)).isEqualTo(1);
            assertThat(call(t, HttpMethod.GET, gate, null).get("rule").get("enabled").asBoolean()).as("规则恢复").isTrue();
            // 正在上课时不能再“重新开课”；同班有别的课在上时也不行
            assertThat(raw(t, HttpMethod.POST, "/api/teacher/lessons/" + lesson + "/reopen", map()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            call(t, HttpMethod.POST, "/api/teacher/lessons/" + lesson + "/end", map());
            long other = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classId)).get("id").asLong();
            ResponseEntity<String> busy = raw(t, HttpMethod.POST, "/api/teacher/lessons/" + lesson + "/reopen", map());
            assertThat(busy.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(busy.getBody()).contains("正在上课");
            call(t, HttpMethod.POST, "/api/teacher/lessons/" + other + "/end", map());
        } finally {
            ctr.stop(0);
        }
    }

    /** 作品组内有人交就算：一人交了全部程序，其他人交测验和任务单，全组一起开网。 */
    @Test
    void sharedProgramsWithSheets() throws Exception {
        HttpServer ctr = fakeCtr();
        try {
            HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
            Map<String, String> settings = new LinkedHashMap<>();
            settings.put("net.enabled", "true");
            settings.put("net.ctrUrl", "http://127.0.0.1:" + ctr.getAddress().getPort());
            call(admin, HttpMethod.PUT, "/api/admin/settings", settings);
            long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2084", "label", "2084级", "active", true)).get("id").asLong();
            long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "共享班", "displayOrder", 1)).get("id").asLong();
            call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "共享老师", "loginName", "sp_t", "password", "sp-pass-1"));
            String[] names = {"子", "丑", "寅", "卯"};
            List<HttpHeaders> st = new ArrayList<>();
            for (int i = 0; i < names.length; i++) {
                call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", names[i], "studentNo", "0" + (i + 1),
                        "xjh", "SP000" + i, "classId", classId, "enrollYearId", yearId, "password", "sp-pass-1"));
                st.add(session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"SP000" + i + "\",\"password\":\"sp-pass-1\"}"));
            }
            HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"sp_t\",\"password\":\"sp-pass-1\"}");

            long q = call(t, HttpMethod.POST, "/api/teacher/quiz/questions", map("qtype", "JUDGE", "stem", "1 字节 = 8 位（共享）", "answer", "T", "score", 5)).get("id").asLong();
            long paper = call(t, HttpMethod.POST, "/api/teacher/quiz/papers", map("name", "共享卷", "items", new Object[]{map("questionId", q, "score", 5)})).get("id").asLong();
            long course = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "共享课")).get("id").asLong();
            String pages = "/api/teacher/courses/" + course + "/pages";
            long sheet = call(t, HttpMethod.POST, pages, map("title", "任务单一", "pageType", "SURVEY", "studentVisible", true)).get("id").asLong();
            call(t, HttpMethod.PUT, "/api/teacher/activity/pages/" + sheet + "/survey", map("nameMode", "TEACHER", "showResult", false,
                    "items", new Object[]{map("qtype", "FILL", "stem", "读数是多少")}));
            String item = call(t, HttpMethod.GET, "/api/teacher/activity/pages/" + sheet + "/config", null).get("items").get(0).get("id").asText();
            long hw1 = call(t, HttpMethod.POST, pages, map("title", "程序一", "pageType", "HOMEWORK", "studentVisible", true)).get("id").asLong();
            long hw2 = call(t, HttpMethod.POST, pages, map("title", "程序二", "pageType", "HOMEWORK", "studentVisible", true)).get("id").asLong();
            long quiz = call(t, HttpMethod.POST, pages, map("title", "习题", "pageType", "QUIZ", "studentVisible", true, "refId", paper)).get("id").asLong();
            for (long hw : new long[]{hw1, hw2}) {
                call(t, HttpMethod.PUT, "/api/teacher/grading/pages/" + hw + "/rules", map("rules", new Object[]{map("type", "TEXT_CONTAINS", "param1", "搭好了", "score", 100)}));
            }
            long lesson = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classId)).get("id").asLong();
            for (HttpHeaders s : st) call(s, HttpMethod.POST, "/api/student/lessons/" + lesson + "/join", map());
            for (int i = 0; i < names.length; i++) {
                String ip = "10.9.1." + (i + 1);
                db.update("UPDATE lesson_attendance SET ip_address = ? WHERE lesson_id = ? AND student_id = (SELECT x.student_id FROM lesson_attendance x JOIN users u ON u.id = x.student_id WHERE x.lesson_id = lesson_attendance.lesson_id AND u.name = ?)", ip, lesson, names[i]);
                net.put(ip, "normal");
            }
            String tg = "/api/teacher/groups/lessons/" + lesson;
            String sg = "/api/student/groups/lessons/" + lesson;
            call(t, HttpMethod.PUT, tg + "/settings", map("groupCount", 2, "joinOpen", true, "maxSize", 3));
            for (int i = 0; i < 3; i++) call(st.get(i), HttpMethod.POST, sg + "/join", map("groupNo", 1));
            call(st.get(3), HttpMethod.POST, sg + "/join", map("groupNo", 2));

            String gate = "/api/teacher/quiz/lessons/" + lesson + "/net-gate";
            // 作品页、课堂调查页之外的页面不能选
            assertThat(raw(t, HttpMethod.PUT, gate, map("pageId", quiz, "passScore", 5, "maxTries", 3, "pageIds", new long[]{quiz})).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            call(t, HttpMethod.PUT, gate, map("pageId", quiz, "passScore", 5, "maxTries", 3, "pageIds", new long[]{hw1, hw2, sheet},
                    "homeworkPass", 100, "requireGroup", true, "groupPrograms", true));
            JsonNode rule = call(t, HttpMethod.GET, gate, null).get("rule");
            assertThat(rule.get("groupPrograms").asBoolean()).isTrue();
            assertThat(rule.get("pages").toString()).contains("SURVEY").contains("HOMEWORK");
            for (int i = 1; i <= 4; i++) await("10.9.1." + i, "blocked");
            long test = call(t, HttpMethod.GET, "/api/teacher/quiz/lessons/" + lesson + "/pages/" + quiz + "/test", null).get("id").asLong();
            call(t, HttpMethod.PUT, "/api/teacher/quiz/tests/" + test + "/status", map("status", "OPEN"));
            String up = "/api/student/homework/lessons/" + lesson + "/pages/";
            String sv = "/api/student/activity/lessons/" + lesson + "/pages/" + sheet + "/survey";
            Map<String, Object> answer = map("answers", map(item, "1200"));

            // 子：测验 + 任务单 + 程序一；丑、寅只交测验
            quizOnly(st.get(0), test, q);
            call(st.get(0), HttpMethod.PUT, sv, answer);
            upload(st.get(0), up + hw1, "我搭好了");
            quizOnly(st.get(1), test, q);
            quizOnly(st.get(2), test, q);
            stays("10.9.1.1", "blocked");
            assertThat(row(t, gate, "丑").get("state").asText()).isEqualTo("SHEET");
            assertThat(row(t, gate, "子").get("state").asText()).isEqualTo("GROUP");
            assertThat(row(t, gate, "子").get("groupMissing").toString()).contains("程序二").doesNotContain("程序一");
            assertThat(row(t, gate, "丑").get("programs").toString()).contains("\"groupOk\":true");
            JsonNode info = call(st.get(1), HttpMethod.GET, "/api/student/quiz/tests/" + test, null).get("netGate");
            assertThat(info.get("groupPrograms").asBoolean()).isTrue();
            assertThat(info.get("missingPrograms").toString()).contains("任务单一").doesNotContain("程序");
            info = call(st.get(0), HttpMethod.GET, "/api/student/quiz/tests/" + test, null).get("netGate");
            assertThat(info.get("missingPrograms").size()).isEqualTo(0);
            assertThat(info.get("groupMissing").toString()).contains("程序二");
            assertThat(info.get("waiting").toString()).contains("丑").contains("寅").contains("任务单一");

            // 丑、寅交任务单，子再交程序二：全组一起开网，丑、寅自己没交程序
            call(st.get(1), HttpMethod.PUT, sv, answer);
            call(st.get(2), HttpMethod.PUT, sv, answer);
            stays("10.9.1.2", "blocked");
            upload(st.get(0), up + hw2, "我搭好了");
            for (int i = 1; i <= 3; i++) await("10.9.1." + i, "normal");
            assertThat(row(t, gate, "寅").get("state").asText()).isEqualTo("PASS");
            assertThat(call(st.get(2), HttpMethod.GET, "/api/student/quiz/tests/" + test, null).get("netGate").get("open").asBoolean()).isTrue();

            // 第 2 组只有卯：交了测验和任务单、没交程序，不开网
            quizOnly(st.get(3), test, q);
            call(st.get(3), HttpMethod.PUT, sv, answer);
            stays("10.9.1.4", "blocked");
            assertThat(row(t, gate, "卯").get("groupMissing").size()).isEqualTo(2);

            // 子请假后不算在组内，他交的程序也不算：丑、寅断网；回来后恢复
            db.update("UPDATE lesson_attendance SET status = 'LEAVE' WHERE lesson_id = ? AND student_id = (SELECT x.student_id FROM lesson_attendance x JOIN users u ON u.id = x.student_id WHERE x.lesson_id = lesson_attendance.lesson_id AND u.name = ?)", lesson, "子");
            await("10.9.1.2", "blocked");
            db.update("UPDATE lesson_attendance SET status = 'PRESENT' WHERE lesson_id = ? AND student_id = (SELECT x.student_id FROM lesson_attendance x JOIN users u ON u.id = x.student_id WHERE x.lesson_id = lesson_attendance.lesson_id AND u.name = ?)", lesson, "子");
            await("10.9.1.2", "normal");

            // 关掉共享：回到每人都要交程序，丑断网
            call(t, HttpMethod.PUT, gate, map("pageId", quiz, "passScore", 5, "maxTries", 3, "pageIds", new long[]{hw1, hw2, sheet},
                    "homeworkPass", 100, "requireGroup", true, "groupPrograms", false));
            await("10.9.1.2", "blocked");
            assertThat(row(t, gate, "丑").get("state").asText()).isEqualTo("PROGRAM");
            // 不按小组时不能共享作品；只要任务单时丑开网
            call(t, HttpMethod.PUT, gate, map("pageId", quiz, "passScore", 5, "maxTries", 3, "pageIds", new long[]{sheet},
                    "homeworkPass", 100, "requireGroup", false, "groupPrograms", true));
            await("10.9.1.2", "normal");
            assertThat(call(t, HttpMethod.GET, gate, null).get("rule").get("groupPrograms").asBoolean()).isFalse();
        } finally {
            ctr.stop(0);
        }
    }

    private void quizOnly(HttpHeaders s, long test, long q) throws Exception {
        call(s, HttpMethod.GET, "/api/student/quiz/tests/" + test, null);
        call(s, HttpMethod.PUT, "/api/student/quiz/tests/" + test, map("answers", new Object[]{map("questionId", q, "answer", "T")}, "submit", true));
    }

    private void finish(HttpHeaders s, long test, long q, String up) throws Exception {
        quizOnly(s, test, q);
        upload(s, up, "我搭好了");
    }
}
