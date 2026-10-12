package com.ittools.quiz;

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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 图片式题库：批量传图入库（去重、自然排序）、草稿设置答案、图片访问权限、标签星级、回收站、题库包。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuestionBankIT extends AbstractPostgresIT {
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

    private JsonNode upload(HttpHeaders s, String url, String filename, byte[] data, Map<String, String> fields) throws Exception {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(data) { @Override public String getFilename() { return filename; } });
        fields.forEach(form::add);
        HttpHeaders mp = new HttpHeaders();
        mp.putAll(s);
        mp.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> r = rest.exchange(url, HttpMethod.POST, new HttpEntity<>(form, mp), String.class);
        assertThat(r.getStatusCode()).as(url + " → " + r.getBody()).isEqualTo(HttpStatus.OK);
        return om.readTree(r.getBody()).get("data");
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static byte[] zip(String... nameAndContent) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(buf, StandardCharsets.UTF_8)) {
            for (int i = 0; i < nameAndContent.length; i += 2) {
                z.putNextEntry(new ZipEntry(nameAndContent[i]));
                z.write(nameAndContent[i + 1].getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return buf.toByteArray();
    }

    private static Map<String, String> fields(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void imageBankLifecycle() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2034", "label", "2034级", "active", true)).get("id").asLong();
        long classId = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "图班", "displayOrder", 1)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "图老师", "loginName", "img_t1", "password", "img-pass-1"));
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "图老师二", "loginName", "img_t2", "password", "img-pass-1"));
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "STUDENT", "name", "图学生", "studentNo", "01", "xjh", "IMG0001",
                "classId", classId, "enrollYearId", yearId, "password", "img-pass-1"));
        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"img_t1\",\"password\":\"img-pass-1\"}");
        HttpHeaders t2 = session("{\"loginType\":\"TEACHER\",\"loginName\":\"img_t2\",\"password\":\"img-pass-1\"}");
        HttpHeaders s = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"IMG0001\",\"password\":\"img-pass-1\"}");
        String qs = "/api/teacher/quiz/questions";

        // ---- 批量传图入库：自然排序、去重（重复只合并标签） ----
        byte[] zip = zip("10.png", "图片十", "2.png", "图片二", "1.png", "图片一", "说明.txt", "不是图片", "__MACOSX/._1.png", "垃圾");
        JsonNode r = upload(t, qs + "/import-images", "题目.zip", zip, fields("qtype", "SINGLE", "optionCount", "4", "category", "学考", "tags", "信息技术，学考"));
        assertThat(r.get("images").asInt()).isEqualTo(3);
        assertThat(r.get("imported").asInt()).isEqualTo(3);
        r = upload(t, qs + "/import-images", "题目.zip", zip, fields("qtype", "SINGLE", "tags", "复习"));
        assertThat(r.get("imported").asInt()).isEqualTo(0);
        assertThat(r.get("duplicates").asInt()).isEqualTo(3);
        JsonNode drafts = call(t, HttpMethod.GET, qs + "?status=draft&includeShared=false", null);
        assertThat(drafts.size()).isEqualTo(3);
        long q1 = drafts.get(2).get("id").asLong(), q2 = drafts.get(1).get("id").asLong(), q10 = drafts.get(0).get("id").asLong();
        assertThat(q1).as("自然排序：1.png 先入库").isLessThan(q2);
        assertThat(q2).isLessThan(q10);
        assertThat(drafts.get(0).get("hasImage").asBoolean()).isTrue();
        assertThat(drafts.get(0).get("stars").asInt()).isEqualTo(1);
        assertThat(drafts.get(0).get("tags").toString()).contains("信息技术", "学考", "复习");

        // 草稿不能进卷
        long paper = call(t, HttpMethod.POST, "/api/teacher/quiz/papers", map("name", "图片卷")).get("id").asLong();
        call(t, HttpMethod.POST, "/api/teacher/quiz/papers/" + paper + "/random", map("SINGLE", 5));
        assertThat(call(t, HttpMethod.GET, "/api/teacher/quiz/papers/" + paper, null).get("questions").size()).isEqualTo(0);

        // ---- 入库处理：设置答案后可用 ----
        for (long id : new long[]{q1, q2}) {
            call(t, HttpMethod.PUT, qs + "/" + id, map("qtype", "SINGLE", "stem", "", "options", new String[]{"", "", "", ""}, "answer", "B",
                    "score", 5, "category", "学考", "stars", id == q1 ? 3 : 1, "tags", new String[]{"信息技术", "学考"}));
        }
        assertThat(call(t, HttpMethod.GET, qs + "?status=draft&includeShared=false", null).size()).isEqualTo(1);
        call(t, HttpMethod.POST, "/api/teacher/quiz/papers/" + paper + "/random", map("SINGLE", 5, "tag", "信息技术", "minStars", 3));
        JsonNode pq = call(t, HttpMethod.GET, "/api/teacher/quiz/papers/" + paper, null).get("questions");
        assertThat(pq.size()).as("按标签+星级筛选只抽到 3 星那题").isEqualTo(1);
        assertThat(pq.get(0).get("id").asLong()).isEqualTo(q1);

        // ---- 图片访问权限 ----
        assertThat(rest.exchange("/api/quiz/questions/" + q2 + "/image", HttpMethod.GET, new HttpEntity<>(t), byte[].class).getBody())
                .isEqualTo("图片二".getBytes(StandardCharsets.UTF_8));
        assertThat(rest.exchange("/api/quiz/questions/" + q2 + "/image", HttpMethod.GET, new HttpEntity<>(s), String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        call(t, HttpMethod.POST, qs + "/batch", map("ids", new long[]{q2}, "shared", true));   // 共享后进入练习题库
        assertThat(rest.exchange("/api/quiz/questions/" + q2 + "/image", HttpMethod.GET, new HttpEntity<>(s), byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // ---- 标签 / 星级批量维护 ----
        call(t, HttpMethod.POST, qs + "/batch", map("ids", new long[]{q1, q2}, "stars", 4, "addTags", new String[]{"程序设计"}));
        call(t, HttpMethod.PUT, "/api/teacher/quiz/tags/rename", map("from", "复习", "to", "学考"));   // 合并到已有标签
        JsonNode tags = call(t, HttpMethod.GET, "/api/teacher/quiz/tags", null);
        assertThat(tags.toString()).doesNotContain("复习").contains("程序设计");
        assertThat(call(t, HttpMethod.GET, qs + "?includeShared=false&minStars=4", null).size()).isEqualTo(2);

        // ---- 回收站：仍参与去重；被试卷引用的题不能彻底删除 ----
        call(t, HttpMethod.DELETE, qs + "/" + q10, null);
        call(t, HttpMethod.DELETE, qs + "/" + q1, null);
        assertThat(call(t, HttpMethod.GET, qs + "?status=trash", null).size()).isEqualTo(2);
        r = upload(t, qs + "/import-images", "题目.zip", zip, fields("qtype", "SINGLE"));
        assertThat(r.get("imported").asInt()).as("回收站里的图片也去重").isEqualTo(0);
        JsonNode purge = call(t, HttpMethod.POST, qs + "/purge", map("ids", new long[]{q1, q10}));
        assertThat(purge.get("removed").asInt()).isEqualTo(1);   // q10 未被引用
        assertThat(purge.get("kept").asInt()).isEqualTo(1);      // q1 在试卷里
        assertThat(call(t, HttpMethod.POST, qs + "/restore", map("ids", new long[]{q1})).asInt()).isEqualTo(1);
        JsonNode stats = call(t, HttpMethod.GET, "/api/teacher/quiz/stats", null);
        assertThat(stats.get("total").asInt()).isEqualTo(2);
        assertThat(stats.get("trash").asInt()).isEqualTo(0);
        assertThat(stats.get("withImage").asInt()).isEqualTo(2);

        // ---- 题库包：导出给另一位老师导入，再次导入时去重合并 ----
        call(t, HttpMethod.POST, qs, map("qtype", "JUDGE", "stem", "CPU 是计算机的核心部件", "answer", "对", "tags", new String[]{"硬件"}));
        long textQ = call(t, HttpMethod.GET, qs + "?includeShared=false&keyword=CPU", null).get(0).get("id").asLong();
        ResponseEntity<byte[]> pack = rest.exchange("/api/teacher/quiz/questions/export", HttpMethod.POST,
                new HttpEntity<>(om.writeValueAsString(map("ids", new long[]{q1, q2, textQ})), t), byte[].class);
        assertThat(pack.getStatusCode()).isEqualTo(HttpStatus.OK);
        r = upload(t2, qs + "/import-pack", "pack.zip", pack.getBody(), fields("overwrite", "NONE"));
        assertThat(r.get("added").asInt()).isEqualTo(3);
        r = upload(t2, qs + "/import-pack", "pack.zip", pack.getBody(), fields("overwrite", "ALL"));
        assertThat(r.get("added").asInt()).isEqualTo(0);
        assertThat(r.get("merged").asInt()).isEqualTo(3);
        JsonNode t2q = call(t2, HttpMethod.GET, qs + "?includeShared=false", null);
        assertThat(t2q.size()).isEqualTo(3);
        assertThat(t2q.toString()).contains("CPU 是计算机的核心部件", "程序设计");
    }
}
