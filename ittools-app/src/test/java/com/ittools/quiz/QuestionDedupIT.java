package com.ittools.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ittools.support.AbstractPostgresIT;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** 题库防重复：保存前查重（重复/相似）、Excel 导入跳过重复、查重分组与合并（保留引用最多的题、引用改指向、标签合并）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuestionDedupIT extends AbstractPostgresIT {
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

    private JsonNode upload(HttpHeaders s, String url, byte[] xlsx) throws Exception {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(xlsx) { @Override public String getFilename() { return "题库.xlsx"; } });
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

    /** 题库导入模板的 4 道示例题，再在第 5 行追加一道与第 1 行只差空白和全半角标点的题。 */
    private byte[] templateWithDupRow(byte[] xlsx) throws Exception {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.getSheetAt(0);
            Row src = s.getRow(1), r = s.createRow(5);
            for (int i = 0; i < src.getLastCellNum(); i++) {
                if (src.getCell(i) != null) r.createCell(i).setCellValue(src.getCell(i).toString());
            }
            r.getCell(1).setCellValue("  IP 地址 由多少位 二进制数组成?");
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void preventAndMergeDuplicates() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "查重老师", "loginName", "dup_t1", "password", "dup-pass-1"));
        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"dup_t1\",\"password\":\"dup-pass-1\"}");
        String qs = "/api/teacher/quiz/questions";

        // ---- 手动新增：重复题需确认（force）才能保存；选项相同但顺序不同也算重复，选项不同只算相似 ----
        long j1 = call(t, HttpMethod.POST, qs, map("qtype", "JUDGE", "stem", "CPU 是中央处理器。", "answer", "T", "tags", new String[]{"硬件"})).get("id").asLong();
        Map<String, Object> j2Body = map("qtype", "JUDGE", "stem", "<p>CPU是　中央处理器</p>", "answer", "T");
        JsonNode chk = call(t, HttpMethod.POST, qs + "/check-duplicate", j2Body);
        assertThat(chk.get("same").size()).isEqualTo(1);
        assertThat(chk.get("same").get(0).get("id").asLong()).isEqualTo(j1);
        ResponseEntity<String> rej = raw(t, HttpMethod.POST, qs, j2Body);
        assertThat(rej.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rej.getBody()).contains("已有相同的题目", "#" + j1);
        j2Body.put("force", true);
        long j2 = call(t, HttpMethod.POST, qs, j2Body).get("id").asLong();
        assertThat(call(t, HttpMethod.POST, qs + "/check-duplicate", map("id", j1, "qtype", "JUDGE", "stem", "CPU 是中央处理器")).get("same").size())
                .as("编辑时排除自己").isEqualTo(1);

        long s1 = call(t, HttpMethod.POST, qs, map("qtype", "SINGLE", "stem", "print(7//2) 的结果是", "options", new String[]{"3", "3.5", "4"}, "answer", "A")).get("id").asLong();
        long s2 = call(t, HttpMethod.POST, qs, map("qtype", "SINGLE", "stem", "print(7//2)的结果是？", "options", new String[]{"4", "3.5", "3"}, "answer", "C", "force", true)).get("id").asLong();
        chk = call(t, HttpMethod.POST, qs + "/check-duplicate", map("qtype", "SINGLE", "stem", "print(7//2) 的结果是", "options", new String[]{"1", "2"}));
        assertThat(chk.get("same").size()).isEqualTo(0);
        assertThat(chk.get("similar").size()).as("题干相同、选项不同只提示").isEqualTo(2);
        assertThat(call(t, HttpMethod.POST, qs + "/check-duplicate", map("qtype", "SINGLE", "stem", "print(7%2) 的结果是", "options", new String[]{"3", "3.5", "4"}))
                .get("same").size()).as("代码里的运算符不能被当成标点去掉").isEqualTo(0);

        // ---- Excel 导入：文件内重复和库内重复都跳过 ----
        ResponseEntity<byte[]> tpl = rest.exchange(qs + "/template", HttpMethod.GET, new HttpEntity<>(t), byte[].class);
        byte[] xlsx = templateWithDupRow(tpl.getBody());
        JsonNode imp = upload(t, qs + "/import", xlsx);
        assertThat(imp.get("imported").asInt()).isEqualTo(4);
        assertThat(imp.get("skipped").asInt()).as("第 5 行与第 1 行重复").isEqualTo(1);
        imp = upload(t, qs + "/import", xlsx);
        assertThat(imp.get("imported").asInt()).isEqualTo(0);
        assertThat(imp.get("skipped").asInt()).isEqualTo(5);

        // ---- 查重与合并：保留被试卷引用的 j2（虽然 j1 更早）；s1、s2 同在一份试卷，该试卷保持原样 ----
        long p1 = call(t, HttpMethod.POST, "/api/teacher/quiz/papers", map("name", "卷一", "items",
                new Object[]{map("questionId", j2, "score", 5)})).get("id").asLong();
        call(t, HttpMethod.POST, "/api/teacher/quiz/papers", map("name", "卷三", "items", new Object[]{map("questionId", j2, "score", 5)}));
        long p4 = call(t, HttpMethod.POST, "/api/teacher/quiz/papers", map("name", "卷四", "items",
                new Object[]{map("questionId", j1, "score", 3)})).get("id").asLong();
        long p2 = call(t, HttpMethod.POST, "/api/teacher/quiz/papers", map("name", "卷二", "items",
                new Object[]{map("questionId", s1, "score", 5), map("questionId", s2, "score", 5)})).get("id").asLong();
        JsonNode dups = call(t, HttpMethod.GET, "/api/teacher/quiz/duplicates", null);
        assertThat(dups.size()).isEqualTo(2);
        for (JsonNode g : dups) {
            JsonNode first = g.get("questions").get(0);
            assertThat(first.get("keep").asBoolean()).isTrue();
            if ("JUDGE".equals(g.get("qtype").asText())) assertThat(first.get("id").asLong()).isEqualTo(j2);
            else assertThat(first.get("id").asLong()).as("引用一样多时保留最早的").isEqualTo(s1);
        }
        JsonNode m = call(t, HttpMethod.POST, "/api/teacher/quiz/duplicates/merge", map("ids", new long[]{j1}));
        assertThat(m.get("groups").asInt()).isEqualTo(1);
        assertThat(m.get("merged").asInt()).isEqualTo(1);
        assertThat(call(t, HttpMethod.GET, "/api/teacher/quiz/duplicates", null).size()).isEqualTo(1);
        JsonNode p4q = call(t, HttpMethod.GET, "/api/teacher/quiz/papers/" + p4, null).get("questions");
        assertThat(p4q.get(0).get("id").asLong()).as("试卷引用改指向保留题").isEqualTo(j2);
        assertThat(p4q.get(0).get("score").asInt()).as("卷面分值不变").isEqualTo(3);
        JsonNode kept = call(t, HttpMethod.GET, qs + "?includeShared=false&keyword=中央处理器", null);
        assertThat(kept.size()).isEqualTo(1);
        assertThat(kept.get(0).get("id").asLong()).isEqualTo(j2);
        assertThat(kept.get(0).get("tags").toString()).as("标签合并").contains("硬件");
        assertThat(call(t, HttpMethod.GET, qs + "?status=trash", null).get(0).get("id").asLong()).isEqualTo(j1);

        call(t, HttpMethod.POST, "/api/teacher/quiz/duplicates/merge", map());
        assertThat(call(t, HttpMethod.GET, "/api/teacher/quiz/duplicates", null).size()).isEqualTo(0);
        assertThat(call(t, HttpMethod.GET, "/api/teacher/quiz/papers/" + p2, null).get("questions").size()).as("同卷两道重复题不动").isEqualTo(2);
        assertThat(call(t, HttpMethod.GET, "/api/teacher/quiz/papers/" + p1, null).get("questions").get(0).get("id").asLong()).isEqualTo(j2);
    }
}
