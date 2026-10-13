package com.ittools.homework;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** 班级学习情况综合查询：五张明细报表、期末统计加权总评与名次、导出、学生个人学情。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClassReportIT extends AbstractPostgresIT {
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

    private ResponseEntity<String> submit(HttpHeaders s, String url) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource("作品内容".getBytes(StandardCharsets.UTF_8)) { @Override public String getFilename() { return "work.txt"; } });
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


    @Test
    void classReport() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2041", "label", "2041级", "active", true)).get("id").asLong();
        long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "报表班", "displayOrder", 1)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "报表老师", "loginName", "cr_t", "password", "cr-pass-1"));
        long st1 = 0;
        for (int i = 1; i <= 2; i++) {
            JsonNode u = call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", "报表生" + i, "studentNo", "0" + i,
                    "xjh", "CR000" + i, "classId", classId, "enrollYearId", yearId, "password", "cr-pass-1"));
            if (i == 1) st1 = u.get("id").asLong();
        }
        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"cr_t\",\"password\":\"cr-pass-1\"}");
        HttpHeaders s1 = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"CR0001\",\"password\":\"cr-pass-1\"}");
        HttpHeaders s2 = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"CR0002\",\"password\":\"cr-pass-1\"}");

        long course = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "报表课")).get("id").asLong();
        long page = call(t, HttpMethod.POST, "/api/teacher/courses/" + course + "/pages",
                map("title", "交作品", "pageType", "HOMEWORK", "studentVisible", true, "score", 100)).get("id").asLong();
        long lesson = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classId)).get("id").asLong();
        call(s1, HttpMethod.POST, "/api/student/lessons/" + lesson + "/join", map());
        assertThat(submit(s1, "/api/student/homework/lessons/" + lesson + "/pages/" + page).getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode subs = call(t, HttpMethod.GET, "/api/teacher/homework/lessons/" + lesson + "/pages/" + page, null).get("students");
        long sub = -1;
        for (JsonNode x : subs) if (x.hasNonNull("id")) sub = x.get("id").asLong();
        call(t, HttpMethod.PUT, "/api/teacher/homework/submissions/" + sub + "/grade", map("score", 80));

        long q = call(t, HttpMethod.POST, "/api/teacher/quiz/questions", map("qtype", "JUDGE", "stem", "1 字节 = 8 位", "answer", "T", "score", 10)).get("id").asLong();
        long paper = call(t, HttpMethod.POST, "/api/teacher/quiz/papers", map("name", "报表测验", "items", new Object[]{map("questionId", q, "score", 10)})).get("id").asLong();
        long test = call(t, HttpMethod.POST, "/api/teacher/quiz/tests", map("paperId", paper, "classIds", new long[]{classId}, "kind", "TEST", "mode", "AUTO"))
                .get(0).get("id").asLong();
        call(s1, HttpMethod.GET, "/api/student/quiz/tests/" + test, null);
        Object answers = new Object[]{map("questionId", q, "answer", "T")};
        call(s1, HttpMethod.PUT, "/api/student/quiz/tests/" + test, map("answers", answers, "submit", true));

        // 期末统计：出席 100、作品 80、测验 100，默认权重 10/30/20/20/20（缺项不计）→ (1000+2400+2000)/60 = 90
        JsonNode sum = call(t, HttpMethod.GET, "/api/teacher/reports/class/summary?classId=" + classId, null);
        assertThat(sum.size()).isEqualTo(2);
        JsonNode r1 = sum.get(0), r2 = sum.get(1);
        assertThat(r1.get("attendancePct").asDouble()).isEqualTo(100.0);
        assertThat(r1.get("workPct").asDouble()).isEqualTo(80.0);
        assertThat(r1.get("testPct").asDouble()).isEqualTo(100.0);
        assertThat(r1.get("testDone").asInt() + "/" + r1.get("testTotal").asInt()).isEqualTo("1/1");
        assertThat(r1.get("total").asDouble()).isEqualTo(90.0);
        assertThat(r1.get("rank").asInt()).isEqualTo(1);
        assertThat(r2.get("attendancePct").asDouble()).isEqualTo(0.0);
        assertThat(r2.get("rank").asInt()).isEqualTo(2);
        // 自定义权重：只看测验
        assertThat(call(t, HttpMethod.GET, "/api/teacher/reports/class/summary?classId=" + classId + "&w=0,0,0,0,100", null)
                .get(0).get("total").asDouble()).isEqualTo(100.0);

        // 五张明细报表
        JsonNode att = call(t, HttpMethod.GET, "/api/teacher/reports/class/detail?classId=" + classId + "&kind=ATTENDANCE", null);
        assertThat(att.get("columns").size()).isEqualTo(1);
        String lk = att.get("columns").get(0).get("key").asText();
        assertThat(att.get("rows").get(0).get("cells").get(lk).get("value").asText()).isEqualTo("PRESENT");
        assertThat(att.get("rows").get(1).get("cells").has(lk)).isFalse();
        JsonNode works = call(t, HttpMethod.GET, "/api/teacher/reports/class/detail?classId=" + classId + "&kind=WORKS", null);
        assertThat(works.get("rows").get(0).get("cells").get(lesson + "-" + page).get("score").asDouble()).isEqualTo(80.0);
        JsonNode tests = call(t, HttpMethod.GET, "/api/teacher/reports/class/detail?classId=" + classId + "&kind=TEST", null);
        assertThat(tests.get("columns").get(0).get("full").asDouble()).isEqualTo(10.0);
        assertThat(tests.get("rows").get(0).get("cells").get(String.valueOf(test)).get("score").asDouble()).isEqualTo(10.0);
        assertThat(call(t, HttpMethod.GET, "/api/teacher/reports/class/detail?classId=" + classId + "&kind=HOMEWORK", null).get("columns").size()).isEqualTo(0);
        assertThat(call(t, HttpMethod.GET, "/api/teacher/reports/class/detail?classId=" + classId + "&kind=PYTHON", null).get("columns").size()).isEqualTo(0);

        // 导出 xlsx（含期末统计 + 五张明细共 6 个工作表）
        ResponseEntity<byte[]> x = rest.exchange("/api/teacher/reports/class/export?classId=" + classId, HttpMethod.GET, new HttpEntity<>(t), byte[].class);
        assertThat(x.getStatusCode()).isEqualTo(HttpStatus.OK);
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(x.getBody()))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(6);
            assertThat(wb.getSheetAt(0).getRow(1).getCell(15).getNumericCellValue()).isEqualTo(90.0);
        }

        // 学生个人学情：教师查看 / 学生本人查看
        JsonNode rep = call(t, HttpMethod.GET, "/api/teacher/reports/students/" + st1, null);
        assertThat(rep.get("summary").get("total").asDouble()).isEqualTo(90.0);
        assertThat(rep.get("details").get("WORKS").get(0).get("cell").get("score").asDouble()).isEqualTo(80.0);
        JsonNode mine = call(s1, HttpMethod.GET, "/api/student/report", null);
        assertThat(mine.get("summary").get("rank").asInt()).isEqualTo(1);
        assertThat(mine.get("classSize").asInt()).isEqualTo(2);

        // 课后作业：结分前学生在个人学情里看不到得分、总评也不计入；教师可见
        long hw = call(t, HttpMethod.POST, "/api/teacher/quiz/tests", map("paperId", paper, "classIds", new long[]{classId}, "kind", "HOMEWORK", "mode", "AUTO"))
                .get(0).get("id").asLong();
        call(s1, HttpMethod.GET, "/api/student/quiz/tests/" + hw, null);
        call(s1, HttpMethod.PUT, "/api/student/quiz/tests/" + hw, map("answers", answers, "submit", true));
        assertThat(call(s1, HttpMethod.GET, "/api/student/report", null).get("details").get("HOMEWORK").get(0).get("cell").get("score").isNull()).isTrue();
        assertThat(call(t, HttpMethod.GET, "/api/teacher/reports/students/" + st1, null).get("details").get("HOMEWORK").get(0).get("cell").get("score").asDouble()).isEqualTo(10.0);
        JsonNode r1b = call(t, HttpMethod.GET, "/api/teacher/reports/class/summary?classId=" + classId, null).get(0);
        assertThat(r1b.get("homeworkDone").asInt()).isEqualTo(1);
        assertThat(r1b.get("homeworkPct").isNull()).isTrue();
        assertThat(r1b.get("total").asDouble()).isEqualTo(90.0);

        // 学生不能看班级报表
        ResponseEntity<String> denied = rest.exchange("/api/teacher/reports/class/summary?classId=" + classId, HttpMethod.GET, new HttpEntity<>(s2), String.class);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
