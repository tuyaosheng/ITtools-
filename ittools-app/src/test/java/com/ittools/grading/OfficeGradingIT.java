package com.ittools.grading;

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

/** 第5批：细粒度 Office 阅卷、成绩显示控制、评分点通过率、课后操作题、阅卷参数读取与规则检验。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OfficeGradingIT extends AbstractPostgresIT {
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

    private ResponseEntity<String> upload(HttpHeaders s, String url, byte[] data, String filename) {
        org.springframework.util.MultiValueMap<String, Object> form = new org.springframework.util.LinkedMultiValueMap<>();
        form.add("file", new org.springframework.core.io.ByteArrayResource(data) { @Override public String getFilename() { return filename; } });
        HttpHeaders mp = new HttpHeaders();
        mp.putAll(s);
        mp.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(form, mp), String.class);
    }

    private static byte[] xlsx(boolean good) throws Exception {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.xssf.usermodel.XSSFSheet sh = wb.createSheet("成绩表");
            org.apache.poi.xssf.usermodel.XSSFRow r = sh.createRow(0);
            for (int i = 0; i < 3; i++) r.createCell(i);
            r.getCell(0).setCellValue("成绩表");
            if (good) {
                org.apache.poi.xssf.usermodel.XSSFFont f = wb.createFont();
                f.setBold(true);
                org.apache.poi.xssf.usermodel.XSSFCellStyle st = wb.createCellStyle();
                st.setFont(f);
                r.getCell(0).setCellStyle(st);
                sh.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress.valueOf("A1:C1"));
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void officeOperationQuestions() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2047", "label", "2047级", "active", true)).get("id").asLong();
        long classA = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "操作1班", "displayOrder", 1)).get("id").asLong();
        long classB = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "操作2班", "displayOrder", 2)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "操作老师", "loginName", "op_t", "password", "op-pass-1"));
        student(admin, "甲", "OPS001", classA, yearId);
        student(admin, "乙", "OPS002", classA, yearId);
        student(admin, "丙", "OPS103", classB, yearId);
        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"op_t\",\"password\":\"op-pass-1\"}");
        HttpHeaders a = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"OPS001\",\"password\":\"msg-pass-1\"}");
        HttpHeaders b = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"OPS002\",\"password\":\"msg-pass-1\"}");
        HttpHeaders c = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"OPS103\",\"password\":\"msg-pass-1\"}");

        long course = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "Excel 操作")).get("id").asLong();
        long page = call(t, HttpMethod.POST, "/api/teacher/courses/" + course + "/pages",
                map("title", "成绩表排版", "pageType", "HOMEWORK", "studentVisible", true, "content", "<p>标题加粗并合并居中</p>")).get("id").asLong();
        upload(t, "/api/teacher/pages/" + page + "/file", xlsx(false), "素材.xlsx");
        Object[] rules = {map("type", "XLSX_CELL_BOLD", "param1", "A1", "score", 40, "note", "标题加粗"),
                map("type", "XLSX_MERGED", "param1", "A1:C1", "score", 60, "note", "合并单元格")};
        call(t, HttpMethod.PUT, "/api/teacher/grading/pages/" + page + "/rules", map("rules", rules));

        // ---- 阅卷参数读取、用标准答案检验规则 ----
        ResponseEntity<String> ins = upload(t, "/api/teacher/grading/inspect", xlsx(true), "答案.xlsx");
        assertThat(ins.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ins.getBody()).contains("A1:C1").contains("加粗");
        org.springframework.util.MultiValueMap<String, Object> form = new org.springframework.util.LinkedMultiValueMap<>();
        form.add("file", new org.springframework.core.io.ByteArrayResource(xlsx(true)) { @Override public String getFilename() { return "答案.xlsx"; } });
        form.add("rules", om.writeValueAsString(rules));
        HttpHeaders mp = new HttpHeaders();
        mp.putAll(t);
        mp.setContentType(MediaType.MULTIPART_FORM_DATA);
        JsonNode test = om.readTree(rest.exchange("/api/teacher/grading/test", HttpMethod.POST, new HttpEntity<>(form, mp), String.class).getBody()).get("data");
        assertThat(test.get("score").asDouble()).isEqualTo(100.0);

        // ---- 课堂提交：只显示分数 ----
        call(t, HttpMethod.PUT, "/api/teacher/homework/pages/" + page + "/config", map("autoGrade", true, "autoVisibility", "SCORE", "practice", true));
        long lesson = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classA)).get("id").asLong();
        call(a, HttpMethod.POST, "/api/student/lessons/" + lesson + "/join", map());
        call(b, HttpMethod.POST, "/api/student/lessons/" + lesson + "/join", map());
        String su = "/api/student/homework/lessons/" + lesson + "/pages/" + page;
        assertThat(upload(a, su, xlsx(true), "a.xlsx").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(upload(b, su, xlsx(false), "b.xlsx").getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode mine = call(a, HttpMethod.GET, su, null).get("submission");
        assertThat(mine.get("autoScore").asDouble()).isEqualTo(100.0);
        assertThat(mine.get("autoDetail").isNull()).as("SCORE 模式不显示评分点").isTrue();
        // 教师端评分点通过率
        JsonNode stats = call(t, HttpMethod.GET, "/api/teacher/homework/lessons/" + lesson + "/pages/" + page + "/rule-stats", null);
        assertThat(stats.get(0).get("note").asText()).isEqualTo("标题加粗");
        assertThat(stats.get(0).get("rate").asDouble()).isEqualTo(50.0);
        assertThat(stats.get(1).get("passed").asInt() + "/" + stats.get(1).get("total").asInt()).isEqualTo("1/2");
        // 不显示成绩：我的作品中也看不到
        call(t, HttpMethod.PUT, "/api/teacher/homework/pages/" + page + "/config", map("autoGrade", true, "autoVisibility", "NONE", "practice", true));
        assertThat(call(a, HttpMethod.GET, "/api/student/homework/mine", null).get(0).get("autoScore").isNull()).isTrue();
        call(t, HttpMethod.PUT, "/api/teacher/homework/pages/" + page + "/config", map("autoGrade", true, "autoVisibility", "ALL", "practice", true));

        // ---- 课后操作题：上过该课程的班级才能看到 ----
        assertThat(call(c, HttpMethod.GET, "/api/student/op-practice", null).size()).isEqualTo(0);
        fails(c, HttpMethod.GET, "/api/student/op-practice/" + page, null);
        JsonNode list = call(b, HttpMethod.GET, "/api/student/op-practice", null);
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).get("materialName").asText()).isEqualTo("素材.xlsx");
        ResponseEntity<String> mat = rest.exchange("/api/classroom/pages/" + page + "/file", HttpMethod.GET, new HttpEntity<>(b), String.class);
        assertThat(mat.getStatusCode()).as("学生可下载素材").isEqualTo(HttpStatus.OK);
        JsonNode p1 = om.readTree(upload(b, "/api/student/op-practice/" + page, xlsx(false), "b1.xlsx").getBody()).get("data").get("mine");
        assertThat(p1.get("autoScore").asDouble()).isEqualTo(0.0);
        assertThat(p1.get("autoDetail").asText()).contains("合并单元格");
        JsonNode p2 = om.readTree(upload(b, "/api/student/op-practice/" + page, xlsx(true), "b2.xlsx").getBody()).get("data").get("mine");
        assertThat(p2.get("bestScore").asDouble()).isEqualTo(100.0);
        JsonNode p3 = om.readTree(upload(b, "/api/student/op-practice/" + page, xlsx(false), "b3.xlsx").getBody()).get("data").get("mine");
        assertThat(p3.get("autoScore").asDouble() + "/" + p3.get("bestScore").asDouble() + "/" + p3.get("attempts").asInt()).as("保留最好成绩").isEqualTo("0.0/100.0/3");

        // 教师：课后练习报表
        assertThat(call(t, HttpMethod.GET, "/api/teacher/op-practice/pages", null).get(0).get("submissions").asInt()).isEqualTo(1);
        JsonNode rep = call(t, HttpMethod.GET, "/api/teacher/op-practice/pages/" + page + "?classId=" + classA, null);
        assertThat(rep.get("students").size()).isEqualTo(2);
        assertThat(rep.get("students").get(1).get("attempts").asInt()).isEqualTo(3);
        assertThat(rep.get("rules").get(1).get("rate").asDouble()).isEqualTo(0.0);

        // 关闭课后练习后学生看不到
        call(t, HttpMethod.PUT, "/api/teacher/homework/pages/" + page + "/config", map("autoGrade", true, "practice", false));
        assertThat(call(b, HttpMethod.GET, "/api/student/op-practice", null).size()).isEqualTo(0);
    }
}
