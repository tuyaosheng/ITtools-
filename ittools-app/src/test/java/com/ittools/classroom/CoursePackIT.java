package com.ittools.classroom;

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

/** 课程包导出/导入与课程复制：页面、附件、课件包、页面扩展设置（评分规则、考试方式）一起迁移。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CoursePackIT extends AbstractPostgresIT {
    @Autowired
    TestRestTemplate rest;
    @org.springframework.boot.test.web.server.LocalServerPort
    int port;
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

    private void fails(HttpHeaders s, HttpMethod m, String url, Object body) throws Exception {
        assertThat(raw(s, m, url, body).getStatusCode()).as(url).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private long student(HttpHeaders admin, String name, String xjh, long classId, long yearId) throws Exception {
        return call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", name, "studentNo", xjh.substring(xjh.length() - 2),
                "xjh", xjh, "classId", classId, "enrollYearId", yearId, "password", "msg-pass-1")).get("id").asLong();
    }

    private static byte[] zip(java.nio.charset.Charset cs, String... nameContent) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(bos, cs)) {
            for (int i = 0; i < nameContent.length; i += 2) {
                z.putNextEntry(new java.util.zip.ZipEntry(nameContent[i]));
                z.write(nameContent[i + 1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    private ResponseEntity<String> upload(HttpHeaders s, String url, byte[] data, String filename) {
        org.springframework.util.MultiValueMap<String, Object> form = new org.springframework.util.LinkedMultiValueMap<>();
        form.add("file", new org.springframework.core.io.ByteArrayResource(data) { @Override public String getFilename() { return filename; } });
        HttpHeaders mp = new HttpHeaders();
        mp.putAll(s);
        mp.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(form, mp), String.class);
    }

    @Test
    void exportImportAndCopy() throws Exception {
        java.nio.charset.Charset utf8 = java.nio.charset.StandardCharsets.UTF_8;
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "打包老师", "loginName", "pk_a", "password", "pk-pass-1"));
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "导入老师", "loginName", "pk_b", "password", "pk-pass-1"));
        HttpHeaders a = session("{\"loginType\":\"TEACHER\",\"loginName\":\"pk_a\",\"password\":\"pk-pass-1\"}");
        HttpHeaders b = session("{\"loginType\":\"TEACHER\",\"loginName\":\"pk_b\",\"password\":\"pk-pass-1\"}");

        long course = call(a, HttpMethod.POST, "/api/teacher/courses", map("name", "打包课", "category", "Python", "description", "第一单元")).get("id").asLong();
        String pages = "/api/teacher/courses/" + course + "/pages";
        long text = call(a, HttpMethod.POST, pages, map("title", "资料", "pageType", "FILE", "content", "<p>看资料</p>", "studentVisible", true)).get("id").asLong();
        assertThat(upload(a, "/api/teacher/pages/" + text + "/file", "资料内容".getBytes(utf8), "资料.txt").getStatusCode()).isEqualTo(HttpStatus.OK);
        long hw = call(a, HttpMethod.POST, pages, map("title", "交作品", "pageType", "HOMEWORK", "studentVisible", true, "score", 10)).get("id").asLong();
        call(a, HttpMethod.PUT, "/api/teacher/homework/pages/" + hw + "/config", map("allowedExts", "py,txt", "peerReview", true, "showClassmates", false, "autoGrade", true));
        call(a, HttpMethod.PUT, "/api/teacher/grading/pages/" + hw + "/rules", map("rules", new Object[]{map("type", "FILE_EXT", "param1", "py", "score", 5, "note", "类型正确")}));
        long q = call(a, HttpMethod.POST, "/api/teacher/quiz/questions", map("qtype", "JUDGE", "stem", "1+1=2", "answer", "T", "score", 5)).get("id").asLong();
        long paper = call(a, HttpMethod.POST, "/api/teacher/quiz/papers", map("name", "私有试卷", "items", new Object[]{map("questionId", q, "score", 5)})).get("id").asLong();
        long quiz = call(a, HttpMethod.POST, pages, map("title", "小测", "pageType", "QUIZ", "studentVisible", true, "refId", paper, "score", 5)).get("id").asLong();
        call(a, HttpMethod.PUT, "/api/teacher/quiz/pages/" + quiz + "/config", map("mode", "INSTANT", "timeLimit", 15, "showAnswer", false));
        long survey = call(a, HttpMethod.POST, pages, map("title", "调查", "pageType", "SURVEY", "studentVisible", true)).get("id").asLong();
        call(a, HttpMethod.PUT, "/api/teacher/activity/pages/" + survey + "/survey", map("nameMode", "ALL", "items", new Object[]{map("qtype", "JUDGE", "stem", "喜欢吗")}));
        long sl = call(a, HttpMethod.POST, pages, map("title", "课件", "pageType", "STORYLINE", "studentVisible", true)).get("id").asLong();
        upload(a, "/api/teacher/courseware/pages/" + sl, zip(utf8, "story.html", "<p>STORY-A</p>", "img/a.txt", "A"), "c.zip");
        String slUrl = call(a, HttpMethod.GET, "/api/teacher/courseware/pages/" + sl, null).get("url").asText();

        // ---- 复制课程：页面扩展设置一起复制 ----
        long copy = call(a, HttpMethod.POST, "/api/teacher/courses/" + course + "/copy", null).get("id").asLong();
        JsonNode cp = call(a, HttpMethod.GET, "/api/teacher/courses/" + copy, null).get("pages");
        long cpHw = cp.get(1).get("id").asLong(), cpQuiz = cp.get(2).get("id").asLong();
        assertThat(call(a, HttpMethod.GET, "/api/teacher/grading/pages/" + cpHw + "/rules", null).size()).isEqualTo(1);
        assertThat(call(a, HttpMethod.GET, "/api/teacher/homework/pages/" + cpHw + "/config", null).get("allowedExts").asText()).isEqualTo("py,txt");
        assertThat(call(a, HttpMethod.GET, "/api/teacher/quiz/pages/" + cpQuiz + "/config", null).get("mode").asText()).isEqualTo("INSTANT");

        // ---- 导出 ----
        ResponseEntity<byte[]> ex = rest.exchange("/api/teacher/courses/" + course + "/pack", HttpMethod.GET, new HttpEntity<>(a), byte[].class);
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.OK);
        byte[] pack = ex.getBody();
        java.util.List<String> names = new java.util.ArrayList<>();
        try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(pack), utf8)) {
            for (java.util.zip.ZipEntry e; (e = zin.getNextEntry()) != null; ) names.add(e.getName());
        }
        assertThat(names.get(0)).isEqualTo("course.json");
        assertThat(names).contains("files/1/资料.txt").anyMatch(n -> n.startsWith("courseware/") && n.endsWith("/img/a.txt"));
        // 别人的私有课程不能导出
        assertThat(rest.exchange("/api/teacher/courses/" + course + "/pack", HttpMethod.GET, new HttpEntity<>(b), String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // ---- 另一位教师导入 ----
        assertThat(upload(b, "/api/teacher/courses/import-pack", zip(utf8, "x.txt", "x"), "bad.zip").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<String> imp = upload(b, "/api/teacher/courses/import-pack", pack, "打包课.ittools.zip");
        assertThat(imp.getStatusCode()).as(imp.getBody()).isEqualTo(HttpStatus.OK);
        JsonNode res = om.readTree(imp.getBody()).get("data");
        assertThat(res.get("pages").asInt()).isEqualTo(5);
        assertThat(res.get("warnings").toString()).as("别人的私有试卷不可用").contains("私有试卷");
        long nc = res.get("course").get("id").asLong();
        JsonNode np = call(b, HttpMethod.GET, "/api/teacher/courses/" + nc, null);
        assertThat(np.get("name").asText() + np.get("category").asText()).isEqualTo("打包课Python");
        JsonNode p0 = np.get("pages").get(0), p1 = np.get("pages").get(1), p2 = np.get("pages").get(2), p3 = np.get("pages").get(3), p4 = np.get("pages").get(4);
        assertThat(p0.get("fileName").asText()).isEqualTo("资料.txt");
        ResponseEntity<String> f = rest.exchange("/api/classroom/pages/" + p0.get("id").asLong() + "/file", HttpMethod.GET, new HttpEntity<>(b), String.class);
        assertThat(f.getBody()).isEqualTo("资料内容");
        assertThat(call(b, HttpMethod.GET, "/api/teacher/grading/pages/" + p1.get("id").asLong() + "/rules", null).get(0).get("param1").asText()).isEqualTo("py");
        assertThat(call(b, HttpMethod.GET, "/api/teacher/homework/pages/" + p1.get("id").asLong() + "/config", null).get("peerReview").asBoolean()).isTrue();
        assertThat(p2.get("refId").isNull()).isTrue();
        assertThat(call(b, HttpMethod.GET, "/api/teacher/quiz/pages/" + p2.get("id").asLong() + "/config", null).get("timeLimit").asInt()).isEqualTo(15);
        assertThat(call(b, HttpMethod.GET, "/api/teacher/activity/pages/" + p3.get("id").asLong() + "/config", null).get("items").get(0).get("stem").asText()).isEqualTo("喜欢吗");
        String newUrl = call(b, HttpMethod.GET, "/api/teacher/courseware/pages/" + p4.get("id").asLong(), null).get("url").asText();
        assertThat(newUrl).isNotEqualTo(slUrl).endsWith("/story.html");
        assertThat(rest.getForEntity(newUrl, String.class).getBody()).isEqualTo("<p>STORY-A</p>");
    }
}
