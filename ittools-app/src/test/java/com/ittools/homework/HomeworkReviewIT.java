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

/** 作品批阅细节：作品名/自评、好评中评差评、发回重做（课后也能重交）、偷懒模式批量打分。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HomeworkReviewIT extends AbstractPostgresIT {
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

    private ResponseEntity<String> submit(HttpHeaders s, String url, String title, String selfScore) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource("作品内容".getBytes(StandardCharsets.UTF_8)) { @Override public String getFilename() { return "work.txt"; } });
        form.add("title", title);
        form.add("selfScore", selfScore);
        form.add("description", "我的作品简介");
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
    void reviewDetails() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2036", "label", "2036级", "active", true)).get("id").asLong();
        long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "作品班", "displayOrder", 1)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "作品老师", "loginName", "rv_t", "password", "rv-pass-1"));
        for (int i = 1; i <= 2; i++) {
            call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", "作品生" + i, "studentNo", "0" + i,
                    "xjh", "RV000" + i, "classId", classId, "enrollYearId", yearId, "password", "rv-pass-1"));
        }
        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"rv_t\",\"password\":\"rv-pass-1\"}");
        HttpHeaders s1 = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"RV0001\",\"password\":\"rv-pass-1\"}");
        HttpHeaders s2 = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"RV0002\",\"password\":\"rv-pass-1\"}");
        long course = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "作品课")).get("id").asLong();
        long page = call(t, HttpMethod.POST, "/api/teacher/courses/" + course + "/pages",
                map("title", "交作品", "pageType", "HOMEWORK", "studentVisible", true, "score", 100)).get("id").asLong();
        long lesson = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classId)).get("id").asLong();
        call(s1, HttpMethod.POST, "/api/student/lessons/" + lesson + "/join", map());
        call(s2, HttpMethod.POST, "/api/student/lessons/" + lesson + "/join", map());
        String url = "/api/student/homework/lessons/" + lesson + "/pages/" + page;

        assertThat(submit(s1, url, "我的家乡", "120").getStatusCode()).as("自评超范围").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(submit(s1, url, "我的家乡", "85").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(submit(s2, url, "校园一角", "70").getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode subs = call(t, HttpMethod.GET, "/api/teacher/homework/lessons/" + lesson + "/pages/" + page, null).get("students");
        assertThat(subs.toString()).contains("我的家乡").contains("\"selfScore\":85");
        long sub1 = -1, sub2 = -1;
        for (JsonNode x : subs) {
            if ("我的家乡".equals(x.path("title").asText())) sub1 = x.get("id").asLong();
            if ("校园一角".equals(x.path("title").asText())) sub2 = x.get("id").asLong();
        }

        // 好评 + 评语
        call(t, HttpMethod.PUT, "/api/teacher/homework/submissions/" + sub1 + "/grade", map("score", 90, "comment", "构图好", "rating", "GOOD"));
        // 偷懒模式：批量给未批改的打分，已批改的跳过
        assertThat(call(t, HttpMethod.POST, "/api/teacher/homework/submissions/batch-grade",
                map("ids", new long[]{sub1, sub2}, "score", 60, "rating", "OK", "comment", "继续努力", "skipGraded", true)).asInt()).isEqualTo(1);
        subs = call(t, HttpMethod.GET, "/api/teacher/homework/lessons/" + lesson + "/pages/" + page, null).get("students");
        for (JsonNode x : subs) {
            if (x.get("id").asLong() == sub1) assertThat(x.get("rating").asText() + x.get("teacherScore").asDouble()).isEqualTo("GOOD90.0");
            if (x.get("id").asLong() == sub2) assertThat(x.get("rating").asText() + x.get("teacherScore").asDouble()).isEqualTo("OK60.0");
        }

        // 下课后：未开放补交，普通学生不能重交；被发回重做的可以
        call(t, HttpMethod.POST, "/api/teacher/lessons/" + lesson + "/end", map());
        call(admin, HttpMethod.PUT, "/api/admin/settings", map("homework.allowLate", "false"));
        try {
        assertThat(submit(s1, url, "我的家乡", "85").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        call(t, HttpMethod.POST, "/api/teacher/homework/submissions/" + sub2 + "/return", map("note", "颜色再调整"));
        JsonNode view = call(s2, HttpMethod.GET, url, null);
        assertThat(view.toString()).contains("颜色再调整").contains("\"returned\":true");
        assertThat(submit(s2, url, "校园一角（修改版）", "80").getStatusCode()).isEqualTo(HttpStatus.OK);
        view = call(s2, HttpMethod.GET, url, null);
        assertThat(view.toString()).contains("\"returned\":false").contains("修改版");
        } finally {
            call(admin, HttpMethod.PUT, "/api/admin/settings", map("homework.allowLate", "true"));
        }
    }
}
