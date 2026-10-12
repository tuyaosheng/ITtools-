package com.ittools.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 课后作业 / 测验完整流程：多班发布、成绩可见性、填空归类批改、发还重改、结分锁定、年级统计、错题本“我已掌握”、
 * 控制模式（待机/开考/结束）、提交即批改、指定题库刷题与学习记录刷题、教师报表、课堂组合统分。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuizFlowIT extends AbstractPostgresIT {
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

    private String fails(HttpHeaders s, HttpMethod m, String url, Object body) throws Exception {
        ResponseEntity<String> r = raw(s, m, url, body);
        assertThat(r.getStatusCode()).as(url + " → " + r.getBody()).isEqualTo(HttpStatus.BAD_REQUEST);
        return om.readTree(r.getBody()).get("message").asText();
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    long qSingle, qFill, qJudge;

    /** 作答：single/judge 为字母或 T/F，fill 为答案文本。 */
    private Object answers(String single, String fill, String judge) {
        List<Object> l = new ArrayList<>();
        l.add(map("questionId", qSingle, "answer", single));
        l.add(map("questionId", qFill, "answer", new String[]{fill}));
        l.add(map("questionId", qJudge, "answer", judge));
        return l;
    }

    private JsonNode submit(HttpHeaders s, long testId, Object answers) throws Exception {
        call(s, HttpMethod.GET, "/api/student/quiz/tests/" + testId, null);
        return call(s, HttpMethod.PUT, "/api/student/quiz/tests/" + testId, map("answers", answers, "submit", true));
    }

    @Test
    void homeworkAndTestLifecycle() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2035", "label", "2035级", "active", true)).get("id").asLong();
        long classA = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "作业甲", "displayOrder", 1)).get("id").asLong();
        long classB = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "作业乙", "displayOrder", 2)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "作业老师", "loginName", "hw_t", "password", "hw-pass-1"));
        String[][] students = {{"HWA01", "" + classA}, {"HWA02", "" + classA}, {"HWB01", "" + classB}};
        for (String[] st : students) {
            call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", "学生" + st[0], "studentNo", st[0].substring(3),
                    "xjh", st[0], "classId", Long.parseLong(st[1]), "enrollYearId", yearId, "password", "hw-pass-1"));
        }
        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"hw_t\",\"password\":\"hw-pass-1\"}");
        HttpHeaders a1 = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"HWA01\",\"password\":\"hw-pass-1\"}");
        HttpHeaders a2 = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"HWA02\",\"password\":\"hw-pass-1\"}");
        HttpHeaders b1 = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"HWB01\",\"password\":\"hw-pass-1\"}");

        String qs = "/api/teacher/quiz/questions";
        qSingle = call(t, HttpMethod.POST, qs, map("qtype", "SINGLE", "stem", "输出函数是", "options", new String[]{"input", "print"}, "answer", "B", "score", 5)).get("id").asLong();
        qFill = call(t, HttpMethod.POST, qs, map("qtype", "FILL", "stem", "Python 输出用 ____", "answer", new String[]{"print"}, "score", 5)).get("id").asLong();
        qJudge = call(t, HttpMethod.POST, qs, map("qtype", "JUDGE", "stem", "1 字节 = 8 位", "answer", "T", "score", 5)).get("id").asLong();
        long paper = call(t, HttpMethod.POST, "/api/teacher/quiz/papers", map("name", "第一单元作业", "items", new Object[]{
                map("questionId", qSingle, "score", 5), map("questionId", qFill, "score", 5), map("questionId", qJudge, "score", 5)})).get("id").asLong();

        // ---- 课后作业：同批发布两个班 ----
        fails(t, HttpMethod.POST, "/api/teacher/quiz/tests", map("paperId", paper, "classIds", new long[]{classA}, "kind", "HOMEWORK", "mode", "CONTROL"));
        JsonNode pub = call(t, HttpMethod.POST, "/api/teacher/quiz/tests", map("paperId", paper, "classIds", new long[]{classA, classB}, "kind", "HOMEWORK", "mode", "AUTO"));
        assertThat(pub.size()).isEqualTo(2);
        assertThat(pub.get(0).get("batchKey").asText()).isEqualTo(pub.get(1).get("batchKey").asText());
        long hwA = pub.get(0).get("id").asLong(), hwB = pub.get(1).get("id").asLong();
        assertThat(call(a1, HttpMethod.GET, "/api/student/quiz/tests?kind=HOMEWORK", null).size()).isEqualTo(1);
        assertThat(call(a1, HttpMethod.GET, "/api/student/quiz/tests?kind=TEST", null).size()).isEqualTo(0);

        JsonNode r1 = submit(a1, hwA, answers("B", "Print()", "F"));      // 填空写法不标准、判断错
        assertThat(r1.get("scoreVisible").asBoolean()).as("课后作业结分前不显示成绩").isFalse();
        assertThat(r1.get("attempt").get("score").isNull()).isTrue();
        assertThat(r1.get("answerRevealed").asBoolean()).isFalse();
        submit(a2, hwA, answers("A", "Print()", "T"));
        submit(b1, hwB, answers("B", "print", "T"));

        // ---- 填空按答案归类批改：须结分前；结分须先停止回收 ----
        assertThat(fails(t, HttpMethod.POST, "/api/teacher/quiz/tests/" + hwA + "/finalize", null)).contains("停止回收");
        call(t, HttpMethod.PUT, "/api/teacher/quiz/tests/" + hwA + "/status", map("status", "CLOSED"));
        JsonNode groups = call(t, HttpMethod.GET, "/api/teacher/quiz/tests/" + hwA + "/fill-groups", null);
        JsonNode g = groups.get(0).get("groups").get(0);
        assertThat(g.get("count").asInt()).isEqualTo(2);
        assertThat(g.get("blanks").get(0).asText()).isEqualTo("Print()");
        assertThat(call(t, HttpMethod.PUT, "/api/teacher/quiz/tests/" + hwA + "/fill-groups",
                map("questionId", qFill, "answer", g.get("answer").asText(), "score", 3)).asInt()).isEqualTo(2);

        // ---- 发还重改：停止回收后也能改；未改的题保留人工分 ----
        JsonNode res = call(t, HttpMethod.GET, "/api/teacher/quiz/tests/" + hwA, null).get("results");
        long att1 = res.get(0).get("attemptId").asLong();
        assertThat(res.get(0).get("score").asDouble()).isEqualTo(8.0);   // 5 + 3
        call(t, HttpMethod.POST, "/api/teacher/quiz/attempts/" + att1 + "/return", map("note", "判断题再想想"));
        JsonNode ret = call(a1, HttpMethod.GET, "/api/student/quiz/tests/" + hwA, null);
        assertThat(ret.get("canEdit").asBoolean()).isTrue();
        assertThat(ret.get("attempt").get("returnedNote").asText()).isEqualTo("判断题再想想");
        call(a1, HttpMethod.PUT, "/api/student/quiz/tests/" + hwA, map("answers", answers("B", "Print()", "T"), "submit", true));
        res = call(t, HttpMethod.GET, "/api/teacher/quiz/tests/" + hwA, null).get("results");
        assertThat(res.get(0).get("score").asDouble()).as("填空人工分保留 + 判断改对").isEqualTo(13.0);

        // ---- 结分：锁定、学生可见成绩与答案 ----
        call(t, HttpMethod.POST, "/api/teacher/quiz/tests/" + hwA + "/finalize", null);
        fails(t, HttpMethod.POST, "/api/teacher/quiz/attempts/" + att1 + "/return", map());
        fails(t, HttpMethod.PUT, "/api/teacher/quiz/attempts/" + att1 + "/questions/" + qFill, map("score", 5));
        fails(t, HttpMethod.PUT, "/api/teacher/quiz/tests/" + hwA + "/status", map("status", "OPEN"));
        JsonNode done = call(a1, HttpMethod.GET, "/api/student/quiz/tests/" + hwA, null);
        assertThat(done.get("scoreVisible").asBoolean()).isTrue();
        assertThat(done.get("attempt").get("score").asDouble()).isEqualTo(13.0);
        assertThat(done.get("answerRevealed").asBoolean()).isTrue();

        // ---- 年级统计 ----
        JsonNode stats = call(t, HttpMethod.GET, "/api/teacher/quiz/tests/" + hwA + "/grade-stats", null);
        assertThat(stats.get("classes").size()).isEqualTo(2);
        assertThat(stats.get("overall").get("n").asInt()).isEqualTo(3);
        assertThat(stats.get("overall").get("max").asDouble()).isEqualTo(15.0);

        // ---- 错题本：只收已公布答案的；“我已掌握”后隐藏 ----
        assertThat(call(b1, HttpMethod.GET, "/api/student/quiz/wrong-book", null).size()).as("乙班作业未结分，不公布").isEqualTo(0);
        JsonNode wb = call(a2, HttpMethod.GET, "/api/student/quiz/wrong-book?kind=HOMEWORK", null);
        assertThat(wb.size()).as("单选错 + 填空只得部分分").isEqualTo(2);
        call(a2, HttpMethod.PUT, "/api/student/quiz/wrong-book/" + qSingle, map("mastered", true));
        assertThat(call(a2, HttpMethod.GET, "/api/student/quiz/wrong-book", null).size()).isEqualTo(1);
        JsonNode all = call(a2, HttpMethod.GET, "/api/student/quiz/wrong-book?includeMastered=true", null);
        assertThat(all.size()).isEqualTo(2);
        assertThat(all.toString()).contains("\"mastered\":true");

        // ---- 控制模式：待机 → 开考 → 待机 → 开考 → 结束 ----
        long ctl = call(t, HttpMethod.POST, "/api/teacher/quiz/tests", map("paperId", paper, "classIds", new long[]{classA}, "kind", "TEST", "mode", "CONTROL"))
                .get(0).get("id").asLong();
        JsonNode wait = call(a1, HttpMethod.GET, "/api/student/quiz/tests/" + ctl, null);
        assertThat(wait.get("test").get("status").asText()).isEqualTo("STANDBY");
        assertThat(wait.get("questions").size()).as("待机时不下发试题").isEqualTo(0);
        assertThat(fails(a1, HttpMethod.PUT, "/api/student/quiz/tests/" + ctl, map("answers", answers("B", "print", "T"), "submit", true))).contains("开考");
        call(t, HttpMethod.PUT, "/api/teacher/quiz/tests/" + ctl + "/status", map("status", "OPEN"));
        assertThat(call(a1, HttpMethod.GET, "/api/student/quiz/tests/" + ctl, null).get("questions").size()).isEqualTo(3);
        call(t, HttpMethod.PUT, "/api/teacher/quiz/tests/" + ctl + "/status", map("status", "STANDBY"));
        assertThat(fails(a1, HttpMethod.PUT, "/api/student/quiz/tests/" + ctl, map("answers", answers("B", "print", "T"), "submit", false))).contains("暂停");
        call(t, HttpMethod.PUT, "/api/teacher/quiz/tests/" + ctl + "/status", map("status", "OPEN"));
        JsonNode ctlDone = call(a1, HttpMethod.PUT, "/api/student/quiz/tests/" + ctl, map("answers", answers("B", "print", "T"), "submit", true));
        assertThat(ctlDone.get("attempt").get("score").asDouble()).as("测验交卷即可看分").isEqualTo(15.0);
        assertThat(ctlDone.get("answerRevealed").asBoolean()).as("结束前不公布答案").isFalse();
        call(t, HttpMethod.PUT, "/api/teacher/quiz/tests/" + ctl + "/status", map("status", "CLOSED"));
        assertThat(call(a2, HttpMethod.GET, "/api/student/quiz/tests/" + ctl, null).get("attempt").isNull()).as("没参加的学生").isTrue();
        fails(t, HttpMethod.PUT, "/api/teacher/quiz/tests/" + hwA + "/status", map("status", "STANDBY"));

        // ---- 提交即批改：交卷立即公布答案 ----
        long inst = call(t, HttpMethod.POST, "/api/teacher/quiz/tests", map("paperId", paper, "classIds", new long[]{classA}, "mode", "INSTANT"))
                .get(0).get("id").asLong();
        JsonNode instDone = submit(a2, inst, answers("A", "print", "T"));
        assertThat(instDone.get("answerRevealed").asBoolean()).isTrue();
        assertThat(instDone.get("attempt").get("score").asDouble()).isEqualTo(10.0);

        // ---- 刷题：指定题库（由测验一键生成）、学习记录刷题、刷题记录 ----
        long setId = call(t, HttpMethod.POST, "/api/teacher/quiz/tests/" + hwA + "/practice-set", null).get("id").asLong();
        assertThat(call(b1, HttpMethod.GET, "/api/student/quiz/practice-sets", null).size()).as("只对甲班开放").isEqualTo(0);
        assertThat(call(a1, HttpMethod.GET, "/api/student/quiz/practice-sets", null).get(0).get("questionCount").asInt()).isEqualTo(3);
        JsonNode ses = call(a1, HttpMethod.POST, "/api/student/quiz/practice-sessions", map("source", "SET", "setId", setId, "count", 2));
        assertThat(ses.get("questions").size()).isEqualTo(2);
        assertThat(ses.get("questions").get(0).has("answer")).as("未交卷不含答案").isFalse();
        List<Object> pa = new ArrayList<>();
        for (JsonNode q : ses.get("questions")) {
            long id = q.get("id").asLong();
            pa.add(map("questionId", id, "answer", id == qSingle ? "B" : id == qJudge ? "T" : new String[]{"print"}));
        }
        JsonNode sub = call(a1, HttpMethod.POST, "/api/student/quiz/practice-sessions/" + ses.get("id").asLong() + "/submit", map("answers", pa));
        assertThat(sub.get("correct").asInt()).isEqualTo(2);
        assertThat(sub.get("questions").get(0).has("answer")).isTrue();
        fails(a1, HttpMethod.POST, "/api/student/quiz/practice-sessions/" + ses.get("id").asLong() + "/submit", map("answers", pa));
        JsonNode rec = call(a2, HttpMethod.POST, "/api/student/quiz/practice-sessions", map("source", "RECORD", "count", 5));
        assertThat(rec.get("total").asInt()).as("学习记录刷题排除已掌握的题").isEqualTo(2);
        assertThat(call(a1, HttpMethod.GET, "/api/student/quiz/practice-sessions", null).size()).isEqualTo(1);

        // ---- 教师报表 ----
        JsonNode report = call(t, HttpMethod.GET, "/api/teacher/quiz/practice-report?classId=" + classA, null);
        assertThat(report.get(0).get("answered").asInt()).isEqualTo(2);
        JsonNode rates = call(t, HttpMethod.GET, "/api/teacher/quiz/error-rates?minCount=1", null);
        assertThat(rates.size()).isEqualTo(3);
        call(t, HttpMethod.POST, qs, map("qtype", "JUDGE", "stem", "CPU 是中央处理器。", "answer", "T"));
        call(t, HttpMethod.POST, qs, map("qtype", "JUDGE", "stem", "<p>CPU是 中央处理器</p>", "answer", "T"));
        JsonNode dup = call(t, HttpMethod.GET, "/api/teacher/quiz/duplicates", null);
        assertThat(dup.size()).isEqualTo(1);
        assertThat(dup.get(0).get("questions").size()).isEqualTo(2);

        // ---- 课堂测验页：考试设置 + 课堂组合统分 ----
        long course = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "测验课")).get("id").asLong();
        long page = call(t, HttpMethod.POST, "/api/teacher/courses/" + course + "/pages",
                map("title", "随堂测", "pageType", "QUIZ", "refId", paper, "studentVisible", true)).get("id").asLong();
        assertThat(call(t, HttpMethod.GET, "/api/teacher/quiz/pages/" + page + "/config", null).get("mode").asText()).isEqualTo("CONTROL");
        call(t, HttpMethod.PUT, "/api/teacher/quiz/pages/" + page + "/config", map("mode", "INSTANT", "timeLimit", 10, "showAnswer", true));
        long lesson = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classA)).get("id").asLong();
        call(a1, HttpMethod.POST, "/api/student/lessons/" + lesson + "/join", map());
        JsonNode lq = call(a1, HttpMethod.GET, "/api/student/quiz/lessons/" + lesson + "/pages/" + page, null);
        assertThat(lq.get("test").get("mode").asText()).isEqualTo("INSTANT");
        assertThat(lq.get("test").get("timeLimit").asInt()).isEqualTo(10);
        call(a1, HttpMethod.PUT, "/api/student/quiz/tests/" + lq.get("test").get("id").asLong(), map("answers", answers("B", "print", "F"), "submit", true));
        JsonNode scores = call(t, HttpMethod.GET, "/api/teacher/lessons/" + lesson + "/scores", null);
        assertThat(scores.get("columns").size()).isEqualTo(1);
        String key = scores.get("columns").get(0).get("key").asText();
        JsonNode row = scores.get("rows").get(0);
        assertThat(row.get("scores").get(key).asDouble()).isEqualTo(10.0);
        assertThat(row.get("total").asDouble()).isEqualTo(10.0);
        assertThat(rest.exchange("/api/teacher/lessons/" + lesson + "/scores/export", HttpMethod.GET, new HttpEntity<>(t), byte[].class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
