package com.ittools.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Python 操作题全流程：真实 Docker 沙箱评测（需要本机 Docker，与 Testcontainers 相同前提）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "ittools.python.runner=docker")
class PythonFlowIT extends AbstractPostgresIT {
    @Autowired
    TestRestTemplate rest;
    final ObjectMapper om = new ObjectMapper();

    // 项目约定：各测试类各自复制 cookie+CSRF 登录逻辑
    private HttpHeaders session(String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = rest.postForEntity("/api/auth/login", new HttpEntity<>(body, h), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
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
        JsonNode n = om.readTree(r.getBody());
        assertThat(r.getStatusCode()).as(url + " → " + r.getBody()).isEqualTo(HttpStatus.OK);
        return n.get("data");
    }

    private static java.util.Map<String, Object> map(Object... kv) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void teacherBuildsProblemAndStudentIsGradedInSandbox() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2031", "label", "2031级", "active", true)).get("id").asLong();
        long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "py班", "displayOrder", 1)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "Py老师", "loginName", "py_t", "password", "py-pass-1"));
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", "Py学生", "studentNo", "01", "xjh", "PY0001",
                "classId", classId, "enrollYearId", yearId, "password", "py-pass-1"));

        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"py_t\",\"password\":\"py-pass-1\"}");
        String ref = "a, b = map(int, input().split())\nprint(a + b)\n";
        // 教师用参考代码在沙箱中生成期望输出
        JsonNode runs = call(t, HttpMethod.POST, "/api/teacher/python/run", map("code", ref, "inputs", new String[]{"1 2", "10 20"}));
        assertThat(runs.get(0).get("stdout").asText().trim()).isEqualTo("3");
        assertThat(runs.get(1).get("stdout").asText().trim()).isEqualTo("30");

        JsonNode problem = call(t, HttpMethod.POST, "/api/teacher/python/problems", map(
                "title", "两数之和", "description", "<p>输入两个整数，输出和</p>", "starterCode", "# 在这里写代码\n",
                "referenceCode", ref, "timeLimitMs", 1000, "practice", true,
                "cases", new Object[]{
                        map("name", "样例", "input", "1 2", "expected", "3", "score", 40, "hidden", false),
                        map("name", "隐藏", "input", "10 20", "expected", "30\n", "score", 60, "hidden", true)}));
        long problemId = problem.get("id").asLong();
        assertThat(problem.get("totalScore").asInt()).isEqualTo(100);
        JsonNode mine = call(t, HttpMethod.GET, "/api/teacher/python/problems?keyword=两数", null);
        assertThat(mine.get(0).get("title").asText()).isEqualTo("两数之和");
        assertThat(mine.get(0).get("caseCount").asInt()).isEqualTo(2);

        long courseId = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "Python 入门")).get("id").asLong();
        long pageId = call(t, HttpMethod.POST, "/api/teacher/courses/" + courseId + "/pages",
                map("title", "两数之和", "pageType", "PYTHON", "refId", problemId, "studentVisible", true)).get("id").asLong();
        long lessonId = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", courseId, "classId", classId)).get("id").asLong();

        HttpHeaders s = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"PY0001\",\"password\":\"py-pass-1\"}");
        String base = "/api/student/python/lessons/" + lessonId + "/pages/" + pageId;
        JsonNode view = call(s, HttpMethod.GET, base, null);
        assertThat(view.get("referenceCode").isNull()).isTrue();
        assertThat(view.get("cases").get(0).get("input").asText()).isEqualTo("1 2");
        assertThat(view.get("cases").get(1).has("input")).as("隐藏点不暴露输入").isFalse();

        JsonNode wrong = call(s, HttpMethod.POST, base + "/submit", map("code", "print(0)")).get("submission");
        assertThat(wrong.get("score").asInt()).isEqualTo(0);
        assertThat(wrong.get("detail").get(0).get("status").asText()).isEqualTo("WA");
        assertThat(wrong.get("detail").get(1).has("stdout")).as("隐藏点不暴露输出").isFalse();

        JsonNode loop = call(s, HttpMethod.POST, base + "/submit", map("code", "while True:\n    pass\n")).get("submission");
        assertThat(loop.get("detail").get(0).get("status").asText()).isEqualTo("TLE");

        JsonNode net = call(s, HttpMethod.POST, base + "/submit", map("code",
                "import urllib.request\nurllib.request.urlopen('http://example.com', timeout=0.5)\nprint(3)\n")).get("submission");
        // 无网络：DNS 解析失败（RE）或挂起直到超时（TLE），总之访问不到外网
        assertThat(net.get("detail").get(0).get("passed").asBoolean()).as("沙箱无网络").isFalse();
        assertThat(net.get("detail").get(0).get("status").asText()).isIn("RE", "TLE");

        JsonNode ok = call(s, HttpMethod.POST, base + "/submit", map("code", ref)).get("submission");
        assertThat(ok.get("score").asInt()).isEqualTo(100);
        assertThat(ok.get("bestScore").asInt()).isEqualTo(100);
        assertThat(ok.get("submitCount").asInt()).isEqualTo(4);
        // 提交后继续改代码（自动保存的草稿）不影响已评分的代码
        call(s, HttpMethod.PUT, base + "/code", map("code", "print(0)"));

        JsonNode panel = call(t, HttpMethod.GET, "/api/teacher/python/lessons/" + lessonId + "/pages/" + pageId, null);
        JsonNode row = panel.get("rows").get(0);
        assertThat(row.get("score").asInt()).isEqualTo(100);
        assertThat(row.get("passes").toString()).isEqualTo("[true,true]");

        call(t, HttpMethod.PUT, "/api/teacher/python/lessons/" + lessonId + "/pages/" + pageId + "/reference", map("show", true));
        assertThat(call(s, HttpMethod.GET, base, null).get("referenceCode").asText()).isEqualTo(ref);

        // 课后题库：同一题目独立计分
        assertThat(call(s, HttpMethod.GET, "/api/student/python/practice", null).get(0).get("bestScore").isNull()).isTrue();
        JsonNode prac = call(s, HttpMethod.POST, "/api/student/python/practice/" + problemId + "/submit", map("code", ref)).get("submission");
        assertThat(prac.get("score").asInt()).isEqualTo(100);

        // 修改阅卷点 → 版本号自增，课堂成绩标记为需重测，重测后恢复
        problem = call(t, HttpMethod.PUT, "/api/teacher/python/problems/" + problemId, map(
                "title", "两数之和", "referenceCode", ref, "timeLimitMs", 1000, "practice", true,
                "cases", new Object[]{map("input", "1 2", "expected", "3", "score", 50), map("input", "-1 1", "expected", "0", "score", 50)}));
        assertThat(problem.get("version").asInt()).isEqualTo(2);
        assertThat(call(t, HttpMethod.GET, "/api/teacher/python/lessons/" + lessonId + "/pages/" + pageId, null)
                .get("rows").get(0).get("stale").asBoolean()).isTrue();
        assertThat(call(t, HttpMethod.POST, "/api/teacher/python/lessons/" + lessonId + "/pages/" + pageId + "/regrade", null).asInt()).isEqualTo(1);
        row = call(t, HttpMethod.GET, "/api/teacher/python/lessons/" + lessonId + "/pages/" + pageId, null).get("rows").get(0);
        assertThat(row.get("stale").asBoolean()).isFalse();
        assertThat(row.get("submitCount").asInt()).as("重测不计提交次数").isEqualTo(4);
        assertThat(row.get("score").asInt()).as("重测用提交评分的代码而非草稿").isEqualTo(100);
        JsonNode code = call(t, HttpMethod.GET, "/api/teacher/python/submissions/" + row.get("submissionId").asLong(), null);
        assertThat(code.get("submittedCode").asText()).isEqualTo(ref);
        assertThat(code.get("draftCode").asText()).isEqualTo("print(0)");

        // 已被课程页面使用的题目不能删除
        ResponseEntity<String> del = rest.exchange("/api/teacher/python/problems/" + problemId, HttpMethod.DELETE, new HttpEntity<>(t), String.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
