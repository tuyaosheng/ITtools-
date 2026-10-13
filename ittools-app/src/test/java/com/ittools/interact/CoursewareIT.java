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

/** 课件包页面：上传 zip 解压、自动识别主页、zip-slip 防护、公开地址与沙箱响应头。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CoursewareIT extends AbstractPostgresIT {
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
    void coursewarePackage() throws Exception {
        java.nio.charset.Charset utf8 = java.nio.charset.StandardCharsets.UTF_8;
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2044", "label", "2044级", "active", true)).get("id").asLong();
        long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "课件班", "displayOrder", 1)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "课件老师", "loginName", "cw_t", "password", "cw-pass-1"));
        student(admin, "课件生", "CWS001", classId, yearId);
        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"cw_t\",\"password\":\"cw-pass-1\"}");
        HttpHeaders s = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"CWS001\",\"password\":\"msg-pass-1\"}");
        long course = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "课件课")).get("id").asLong();
        long page = call(t, HttpMethod.POST, "/api/teacher/courses/" + course + "/pages", map("title", "Storyline", "pageType", "STORYLINE", "studentVisible", false)).get("id").asLong();
        long text = call(t, HttpMethod.POST, "/api/teacher/courses/" + course + "/pages", map("title", "图文", "pageType", "TEXT", "studentVisible", true)).get("id").asLong();
        String up = "/api/teacher/courseware/pages/" + page;

        assertThat(upload(t, up, "x".getBytes(utf8), "a.rar").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(upload(t, "/api/teacher/courseware/pages/" + text, zip(utf8, "index.html", "<p>x</p>"), "a.zip").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<String> slip = upload(t, up, zip(utf8, "../../evil.html", "x"), "evil.zip");
        assertThat(slip.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(slip.getBody()).contains("非法路径");

        // 顶层目录自动去掉，自动识别 story.html
        ResponseEntity<String> ok = upload(t, up, zip(utf8, "我的课件/story.html", "<html><body>STORY</body></html>",
                "我的课件/story_content/data.js", "var d = 1;", "我的课件/other.html", "<p>other</p>"), "课件.zip");
        assertThat(ok.getStatusCode()).as(ok.getBody()).isEqualTo(HttpStatus.OK);
        JsonNode cfg = om.readTree(ok.getBody()).get("data");
        String url = cfg.get("url").asText();
        assertThat(url).matches("/courseware/[0-9a-f]{32}/story\\.html");
        assertThat(cfg.get("htmlFiles").toString()).contains("other.html").contains("story.html");

        // 公开地址（无需登录），带 CSP sandbox
        ResponseEntity<String> f = rest.getForEntity(url, String.class);
        assertThat(f.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(f.getBody()).contains("STORY");
        assertThat(f.getHeaders().getFirst("Content-Security-Policy")).startsWith("sandbox allow-scripts");
        assertThat(f.getHeaders().getContentType().toString()).startsWith("text/html");
        String base = url.substring(0, url.lastIndexOf('/') + 1);
        assertThat(rest.getForEntity(base + "story_content/data.js", String.class).getBody()).isEqualTo("var d = 1;");
        assertThat(rest.getForEntity(base + "..%2F..%2Fapplication.yml", String.class).getStatusCode()).as("编码的路径穿越被拒绝").isNotEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity(base + "nope.html", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // 兼容模式：去掉沙箱响应头；标记文件本身不可下载
        call(t, HttpMethod.PUT, up + "/compat", map("compat", true));
        assertThat(rest.getForEntity(url, String.class).getHeaders().getFirst("Content-Security-Policy")).isNull();
        assertThat(rest.getForEntity(base + ".ittools-compat", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(t, HttpMethod.PUT, up + "/compat", map("compat", false)).get("compat").asBoolean()).isFalse();
        assertThat(rest.getForEntity(url, String.class).getHeaders().getFirst("Content-Security-Policy")).startsWith("sandbox");

        // 修改主页
        call(t, HttpMethod.PUT, up + "/entry", map("entry", "other.html"));
        assertThat(call(t, HttpMethod.GET, up, null).get("url").asText()).endsWith("/other.html");
        fails(t, HttpMethod.PUT, up + "/entry", map("entry", "../x.html"));

        // GBK 文件名（Windows 自带压缩），重新上传后旧包目录删除
        ResponseEntity<String> gbk = upload(t, up, zip(java.nio.charset.Charset.forName("GBK"), "课件主页.html", "<p>GBK</p>"), "gbk.zip");
        assertThat(gbk.getStatusCode()).as(gbk.getBody()).isEqualTo(HttpStatus.OK);
        String url2 = om.readTree(gbk.getBody()).get("data").get("url").asText();
        assertThat(url2).endsWith("/课件主页.html");
        assertThat(rest.getForEntity(java.net.URI.create("http://localhost:" + port + new java.net.URI(null, null, url2, null).toASCIIString()), String.class).getBody()).contains("GBK");
        assertThat(rest.getForEntity(url, String.class).getStatusCode()).as("旧包已删除").isEqualTo(HttpStatus.NOT_FOUND);

        // 学生：页面未开放不能取地址；开放后可以
        long lesson = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classId)).get("id").asLong();
        call(s, HttpMethod.POST, "/api/student/lessons/" + lesson + "/join", map());
        fails(s, HttpMethod.GET, "/api/student/courseware/lessons/" + lesson + "/pages/" + page, null);
        call(t, HttpMethod.PUT, "/api/teacher/lessons/" + lesson + "/pages/" + page, map("open", true));
        assertThat(call(s, HttpMethod.GET, "/api/student/courseware/lessons/" + lesson + "/pages/" + page, null).get("url").asText()).isEqualTo(url2);

        // 复制课程：包被共享，原页面重新上传时不删除仍被副本引用的目录
        call(t, HttpMethod.POST, "/api/teacher/courses/" + course + "/copy", map());
        upload(t, up, zip(utf8, "index.html", "<p>v3</p>"), "v3.zip");
        assertThat(rest.getForEntity(java.net.URI.create("http://localhost:" + port + new java.net.URI(null, null, url2, null).toASCIIString()), String.class).getStatusCode())
                .as("副本仍引用旧包").isEqualTo(HttpStatus.OK);
    }
}
