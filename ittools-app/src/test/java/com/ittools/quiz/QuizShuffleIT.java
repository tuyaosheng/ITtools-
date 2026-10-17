package com.ittools.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** 每人乱序：题目/选项顺序按学生固定打乱，学生按显示字母作答，批改与教师端按原选项。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuizShuffleIT extends AbstractPostgresIT {
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

    private JsonNode call(HttpHeaders s, HttpMethod m, String url, Object body) throws Exception {
        ResponseEntity<String> r = rest.exchange(url, m, new HttpEntity<>(body == null ? null : om.writeValueAsString(body), s), String.class);
        assertThat(r.getStatusCode()).as(url + " → " + r.getBody()).isEqualTo(HttpStatus.OK);
        return om.readTree(r.getBody()).get("data");
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static String letterOf(JsonNode q, String optionText) {
        for (int i = 0; i < q.get("options").size(); i++) if (q.get("options").get(i).asText().equals(optionText)) return String.valueOf((char) ('A' + i));
        throw new AssertionError("没有选项 " + optionText);
    }

    private static List<String> view(JsonNode qs) {
        List<String> l = new ArrayList<>();
        for (JsonNode q : qs) l.add(q.get("id").asText() + ":" + q.get("options"));
        return l;
    }

    @Test
    void shuffledPerStudentAndGradedByOriginalOptions() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2073", "label", "2073级", "active", true)).get("id").asLong();
        long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "乱序班", "displayOrder", 1)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "乱序老师", "loginName", "sh_t", "password", "sh-pass-1"));
        for (int i = 1; i <= 2; i++) {
            call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", "乱序生" + i, "studentNo", "0" + i,
                    "xjh", "SH000" + i, "classId", classId, "enrollYearId", yearId, "password", "sh-pass-1"));
        }
        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"sh_t\",\"password\":\"sh-pass-1\"}");
        HttpHeaders s1 = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"SH0001\",\"password\":\"sh-pass-1\"}");
        HttpHeaders s2 = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"SH0002\",\"password\":\"sh-pass-1\"}");

        List<Map<String, Object>> items = new ArrayList<>();
        long single = 0, multi = 0;
        for (int i = 0; i < 6; i++) {
            long id = call(t, HttpMethod.POST, "/api/teacher/quiz/questions", map("qtype", "SINGLE", "stem", "单选" + i,
                    "options", Arrays.asList("甲" + i, "乙" + i, "丙" + i, "丁" + i, "戊" + i), "answer", "B", "score", 10)).get("id").asLong();
            if (i == 0) single = id;
            items.add(map("questionId", id, "score", 10));
        }
        multi = call(t, HttpMethod.POST, "/api/teacher/quiz/questions", map("qtype", "MULTI", "stem", "多选",
                "options", Arrays.asList("键盘", "鼠标", "显示器", "扫描仪"), "answer", "ABD", "score", 10)).get("id").asLong();
        items.add(map("questionId", multi, "score", 10));
        long judge = call(t, HttpMethod.POST, "/api/teacher/quiz/questions", map("qtype", "JUDGE", "stem", "判断", "answer", "T", "score", 10)).get("id").asLong();
        items.add(map("questionId", judge, "score", 10));
        long paper = call(t, HttpMethod.POST, "/api/teacher/quiz/papers", map("name", "乱序卷", "items", items)).get("id").asLong();
        JsonNode test = call(t, HttpMethod.POST, "/api/teacher/quiz/tests", map("paperId", paper, "classIds", new long[]{classId}, "kind", "TEST",
                "mode", "INSTANT", "showAnswer", true, "shuffleQuestions", true, "shuffleOptions", true)).get(0);
        long testId = test.get("id").asLong();
        assertThat(test.get("shuffleQuestions").asBoolean()).isTrue();

        String url = "/api/student/quiz/tests/" + testId;
        JsonNode a1 = call(s1, HttpMethod.GET, url, null).get("questions");
        JsonNode a1again = call(s1, HttpMethod.GET, url, null).get("questions");
        JsonNode a2 = call(s2, HttpMethod.GET, url, null).get("questions");
        assertThat(view(a1again)).as("同一学生每次顺序不变").isEqualTo(view(a1));
        assertThat(view(a2)).as("不同学生顺序不同").isNotEqualTo(view(a1));
        assertThat(a1.size()).isEqualTo(8);

        // 学生 1：全部按显示字母答对；学生 2：多选题少选一个
        for (HttpHeaders s : Arrays.asList(s1, s2)) {
            JsonNode qs = call(s, HttpMethod.GET, url, null).get("questions");
            List<Map<String, Object>> answers = new ArrayList<>();
            for (JsonNode q : qs) {
                String qt = q.get("qtype").asText();
                Object ans;
                if ("SINGLE".equals(qt)) ans = letterOf(q, "乙" + q.get("stem").asText().substring(2));
                else if ("MULTI".equals(qt)) {
                    List<String> l = new ArrayList<>(Arrays.asList(letterOf(q, "键盘"), letterOf(q, "鼠标")));
                    if (s == s1) l.add(letterOf(q, "扫描仪"));
                    Collections.sort(l);
                    ans = String.join("", l);
                } else ans = "T";
                answers.add(map("questionId", q.get("id").asLong(), "answer", ans));
            }
            JsonNode r = call(s, HttpMethod.PUT, url, map("answers", answers, "submit", true));
            assertThat(r.get("attempt").get("score").asDouble()).isEqualTo(s == s1 ? 80.0 : 70.0);
            // 交卷后返回的已答、标准答案都是显示字母，学生看到的对得上
            for (JsonNode q : r.get("questions")) {
                if ("SINGLE".equals(q.get("qtype").asText())) {
                    assertThat(q.get("answer").asText()).isEqualTo(q.get("given").asText());
                    assertThat(q.get("options").get(q.get("answer").asText().charAt(0) - 'A').asText()).startsWith("乙");
                }
            }
        }

        // 教师端看到的是原选项字母
        JsonNode res = call(t, HttpMethod.GET, "/api/teacher/quiz/tests/" + testId, null).get("results");
        long attempt1 = -1;
        for (JsonNode r : res) if ("乱序生1".equals(r.get("name").asText())) attempt1 = r.get("attemptId").asLong();
        assertThat(attempt1).isPositive();
        JsonNode detail = call(t, HttpMethod.GET, "/api/teacher/quiz/attempts/" + attempt1, null);
        for (JsonNode q : detail.get("questions")) {
            if (q.get("id").asLong() == single) assertThat(q.get("given").asText()).isEqualTo("B");
            if (q.get("id").asLong() == multi) assertThat(q.get("given").asText()).isEqualTo("ABD");
            assertThat(q.get("correct").asBoolean()).isTrue();
        }
        // 逐题分析按原选项统计
        JsonNode analysis = call(t, HttpMethod.GET, "/api/teacher/quiz/tests/" + testId + "/analysis", null);
        assertThat(analysis.toString()).contains("\"B\"");
    }
}
