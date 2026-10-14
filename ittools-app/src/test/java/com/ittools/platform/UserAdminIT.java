package com.ittools.platform;

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

/** 第6批：个人资料与头像、表格批量修改学生、毕业生管理与释放空间、第三方程序登录、学期修复。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserAdminIT extends AbstractPostgresIT {
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

    private static byte[] png(int w, int h) throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.ORANGE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static byte[] zipOf(String[] names, byte[][] data) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(bos)) {
            for (int i = 0; i < names.length; i++) {
                z.putNextEntry(new java.util.zip.ZipEntry(names[i]));
                z.write(data[i]);
                z.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    /** 不跟随重定向，返回 [状态码, Location, Set-Cookie]。 */
    private String[] noFollow(String path) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("http://localhost:" + port + path).openConnection();
        c.setInstanceFollowRedirects(false);
        String[] r = {String.valueOf(c.getResponseCode()), c.getHeaderField("Location"), c.getHeaderField("Set-Cookie")};
        c.disconnect();
        return r;
    }

    @Test
    void userAdministration() throws Exception {
        HttpHeaders admin = session("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}");
        long yearId = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2048", "label", "2048级", "active", true)).get("id").asLong();
        long yearId2 = call(admin, HttpMethod.POST, "/api/admin/school-years", map("yearCode", "2049", "label", "2049级", "active", true)).get("id").asLong();
        long classA = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "资料1班", "displayOrder", 1)).get("id").asLong();
        long classB = call(admin, HttpMethod.POST, "/api/admin/classes", map("schoolYearId", yearId, "name", "资料2班", "displayOrder", 2)).get("id").asLong();
        call(admin, HttpMethod.POST, "/api/admin/users", map("role", "TEACHER", "name", "资料老师", "loginName", "ua_t", "password", "ua-pass-1"));
        long s1 = student(admin, "张一", "UAS001", classA, yearId);
        long s2 = student(admin, "王二", "UAS002", classA, yearId);
        long s3 = student(admin, "李三", "UAS003", classB, yearId);
        HttpHeaders t = session("{\"loginType\":\"TEACHER\",\"loginName\":\"ua_t\",\"password\":\"ua-pass-1\"}");
        HttpHeaders a = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"UAS001\",\"password\":\"msg-pass-1\"}");

        // ---- 个人资料 ----
        JsonNode prof = call(a, HttpMethod.PUT, "/api/account/profile", map("name", "改名", "gender", "男", "birthday", "2010-05-01", "bio", "我爱编程"));
        assertThat(prof.get("name").asText()).as("学生不能改姓名").isEqualTo("张一");
        assertThat(prof.get("gender").asText() + prof.get("bio").asText() + prof.get("className").asText()).isEqualTo("男我爱编程资料1班");
        assertThat(prof.get("birthday").asText()).startsWith("2010-05-01");
        fails(a, HttpMethod.PUT, "/api/account/profile", map("gender", "未知"));
        assertThat(call(t, HttpMethod.PUT, "/api/account/profile", map("name", "资料老师（改）")).get("name").asText()).isEqualTo("资料老师（改）");

        // ---- 头像：学生默认不能自行上传；教师/管理员可以 ----
        assertThat(upload(a, "/api/account/avatar", png(400, 300), "me.png").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(upload(t, "/api/account/avatar", "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8), "x.png").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(upload(t, "/api/teacher/classes/" + classA + "/students/" + s2 + "/avatar", png(600, 800), "s2.png").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(upload(t, "/api/teacher/classes/" + classA + "/students/" + s3 + "/avatar", png(10, 10), "s3.png").getStatusCode())
                .as("不在本班").isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<byte[]> av = rest.exchange("/api/users/" + s2 + "/avatar", HttpMethod.GET, new HttpEntity<>(a), byte[].class);
        assertThat(av.getStatusCode()).isEqualTo(HttpStatus.OK);
        java.awt.image.BufferedImage got = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(av.getBody()));
        assertThat(got.getWidth() + "x" + got.getHeight()).as("缩放到 240 以内").isEqualTo("180x240");
        assertThat(rest.exchange("/api/users/" + s1 + "/avatar", HttpMethod.GET, new HttpEntity<>(a), byte[].class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(t, HttpMethod.GET, "/api/teacher/classes/" + classA + "/students", null).get(1).get("hasAvatar").asBoolean()).isTrue();
        JsonNode imp = om.readTree(upload(admin, "/api/admin/avatars/import",
                zipOf(new String[]{"photos/UAS001.jpg", "UAS999.png", "readme.txt"}, new byte[][]{png(50, 50), png(50, 50), new byte[]{1}}), "p.zip").getBody()).get("data");
        assertThat(imp.get("imported").asInt()).isEqualTo(1);
        assertThat(imp.get("unmatched").toString()).contains("UAS999.png");
        call(admin, HttpMethod.PUT, "/api/admin/settings", map("student.allowAvatarChange", "true", "student.allowPasswordChange", "false"));
        try {
            assertThat(upload(a, "/api/account/avatar", png(100, 100), "me.png").getStatusCode()).isEqualTo(HttpStatus.OK);
            fails(a, HttpMethod.POST, "/api/account/password", map("oldPassword", "msg-pass-1", "newPassword", "abcd1234"));
        } finally {
            call(admin, HttpMethod.PUT, "/api/admin/settings", map("student.allowAvatarChange", "false", "student.allowPasswordChange", "true"));
        }

        // ---- 表格批量修改：改名、改学号、换班、互换学籍号、改入学年份 ----
        ResponseEntity<byte[]> ex = rest.exchange("/api/admin/batch-edit/students/export?yearId=" + yearId, HttpMethod.GET, new HttpEntity<>(admin), byte[].class);
        byte[] edited;
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(ex.getBody()))) {
            org.apache.poi.ss.usermodel.Sheet sh = wb.getSheetAt(0);
            assertThat(sh.getLastRowNum()).isEqualTo(3);
            for (org.apache.poi.ss.usermodel.Row r : sh) {
                if (r.getRowNum() == 0) continue;
                long id = (long) r.getCell(0).getNumericCellValue();
                if (id == s1) { r.getCell(3).setCellValue("张壹"); r.getCell(1).setCellValue("UAS002"); }
                if (id == s2) { r.getCell(1).setCellValue("UAS001"); r.getCell(6).setCellValue("资料2班"); r.getCell(2).setCellValue("03"); }
                if (id == s3) { r.getCell(7).setCellValue("2049级"); }
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            wb.write(out);
            edited = out.toByteArray();
        }
        JsonNode pv = om.readTree(upload(admin, "/api/admin/batch-edit/students/preview", edited, "edit.xlsx").getBody()).get("data");
        assertThat(pv.get("errors").toString()).as("王二转入2班后学号 03 与李三重复").contains("学号 03 重复");
        assertThat(upload(admin, "/api/admin/batch-edit/students/apply", edited, "edit.xlsx").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(edited))) {
            for (org.apache.poi.ss.usermodel.Row r : wb.getSheetAt(0)) {
                if (r.getRowNum() > 0 && (long) r.getCell(0).getNumericCellValue() == s2) r.getCell(2).setCellValue("08");
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            wb.write(out);
            edited = out.toByteArray();
        }
        pv = om.readTree(upload(admin, "/api/admin/batch-edit/students/preview", edited, "edit.xlsx").getBody()).get("data");
        assertThat(pv.get("errors").size()).as(pv.toString()).isEqualTo(0);
        assertThat(pv.get("changes").size()).isEqualTo(3);
        JsonNode ap = om.readTree(upload(admin, "/api/admin/batch-edit/students/apply", edited, "edit.xlsx").getBody()).get("data");
        assertThat(ap.get("applied").asInt()).isEqualTo(3);
        JsonNode u2 = user(admin, s2);
        assertThat(u2.get("xjh").asText() + u2.get("classId").asLong() + u2.get("studentNo").asText()).isEqualTo("UAS001" + classB + "08");
        assertThat(user(admin, s1).get("name").asText()).isEqualTo("张壹");
        assertThat(user(admin, s3).get("enrollYearId").asLong()).isEqualTo(yearId2);

        // ---- 学期修复 ----
        long course = call(t, HttpMethod.POST, "/api/teacher/courses", map("name", "资料课")).get("id").asLong();
        long lesson = call(t, HttpMethod.POST, "/api/teacher/lessons", map("courseId", course, "classId", classA)).get("id").asLong();
        jdbcNullTerm(lesson);
        JsonNode nt = call(admin, HttpMethod.GET, "/api/admin/tools/lessons-without-term", null);
        assertThat(nt.toString()).contains("资料课");
        long term = call(admin, HttpMethod.POST, "/api/admin/terms", map("yearName", "2048-2049", "termName", "第一学期", "displayOrder", 9)).asLong();
        assertThat(call(admin, HttpMethod.POST, "/api/admin/tools/lessons-without-term", map("lessonIds", new long[]{lesson}, "termId", term)).asInt()).isEqualTo(1);

        // ---- 毕业：整班毕业、释放空间（保留账号与精华作品） ----
        long page = call(t, HttpMethod.POST, "/api/teacher/courses/" + course + "/pages", map("title", "作品", "pageType", "HOMEWORK", "studentVisible", true)).get("id").asLong();
        HttpHeaders a2 = session("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"UAS002\",\"password\":\"msg-pass-1\"}");   // 张壹（学籍号已换）
        call(a2, HttpMethod.POST, "/api/student/lessons/" + lesson + "/join", map());
        upload(a2, "/api/student/homework/lessons/" + lesson + "/pages/" + page, "作品".getBytes(java.nio.charset.StandardCharsets.UTF_8), "w.txt");
        long subId = call(t, HttpMethod.GET, "/api/teacher/homework/lessons/" + lesson + "/pages/" + page, null).get("students").get(0).get("id").asLong();
        long page2 = call(t, HttpMethod.POST, "/api/teacher/courses/" + course + "/pages", map("title", "作品2", "pageType", "HOMEWORK", "studentVisible", true)).get("id").asLong();
        upload(a2, "/api/student/homework/lessons/" + lesson + "/pages/" + page2, "精华".getBytes(java.nio.charset.StandardCharsets.UTF_8), "e.txt");
        long essId = -1;
        for (JsonNode x : call(t, HttpMethod.GET, "/api/teacher/homework/lessons/" + lesson + "/pages/" + page2, null).get("students")) if (x.hasNonNull("id")) essId = x.get("id").asLong();
        call(t, HttpMethod.PUT, "/api/teacher/homework/submissions/" + essId + "/essence", map("essence", true));
        call(t, HttpMethod.POST, "/api/teacher/lessons/" + lesson + "/end", map());

        assertThat(call(admin, HttpMethod.POST, "/api/admin/graduation/classes", map("classIds", new long[]{classA})).asInt()).isEqualTo(1);
        JsonNode sum = call(admin, HttpMethod.GET, "/api/admin/graduation", null);
        for (JsonNode k : sum) if (k.get("classId").asLong() == classA) {
            assertThat(k.get("graduated").asInt()).isEqualTo(1);
            assertThat(k.get("releasableBytes").asLong()).isGreaterThan(0);
        }
        assertThat(loginStatus("{\"loginType\":\"STUDENT_XJH\",\"xjh\":\"UAS002\",\"password\":\"msg-pass-1\"}")).as("毕业生不能登录").isNotEqualTo(200);
        JsonNode rel = call(admin, HttpMethod.POST, "/api/admin/graduation/release", map("classIds", new long[]{classA}));
        assertThat(rel.get("students").asInt()).isEqualTo(1);
        assertThat(rel.get("files").asInt()).isEqualTo(1);
        assertThat(rest.exchange("/api/homework/submissions/" + subId + "/file", HttpMethod.GET, new HttpEntity<>(t), String.class).getStatusCode()).isNotEqualTo(HttpStatus.OK);
        assertThat(rest.exchange("/api/homework/submissions/" + essId + "/file", HttpMethod.GET, new HttpEntity<>(t), String.class).getBody()).as("精华作品保留").isEqualTo("精华");
        assertThat(user(admin, s1).get("graduated").asBoolean()).as("账号保留").isTrue();
        call(admin, HttpMethod.POST, "/api/admin/graduation/classes", map("classIds", new long[]{classA}, "graduated", false));

        // ---- 第三方程序登录 ----
        long now = System.currentTimeMillis() / 1000;
        String[] r0 = noFollow("/api/auth/sso?u=UAS003&t=" + now + "&s=00");
        assertThat(r0[1]).as("未开启").contains("/login?sso=");
        String secret = call(admin, HttpMethod.POST, "/api/admin/settings/sso-secret", null).get("secret").asText();
        call(admin, HttpMethod.PUT, "/api/admin/settings", map("sso.enabled", "true"));
        try {
            String sig = com.ittools.platform.security.SsoService.sign(secret, "UAS003", now);
            String[] ok = noFollow("/api/auth/sso?u=UAS003&t=" + now + "&s=" + sig + "&r=/student/tests");
            assertThat(ok[0]).isEqualTo("302");
            assertThat(ok[1]).endsWith("/student/tests");
            HttpHeaders sso = new HttpHeaders();
            sso.add(HttpHeaders.COOKIE, ok[2].split(";", 2)[0]);
            assertThat(call(sso, HttpMethod.GET, "/api/auth/me", null).get("displayName").asText()).isEqualTo("李三");
            assertThat(noFollow("/api/auth/sso?u=UAS003&t=" + now + "&s=" + sig)[1]).as("一次性").contains("/login?sso=");
            String bad = com.ittools.platform.security.SsoService.sign("wrong-secret-0000000000", "UAS003", now);
            assertThat(noFollow("/api/auth/sso?u=UAS003&t=" + now + "&s=" + bad)[1]).contains("/login?sso=");
            long old = now - 600;
            assertThat(noFollow("/api/auth/sso?u=UAS003&t=" + old + "&s=" + com.ittools.platform.security.SsoService.sign(secret, "UAS003", old))[1]).as("过期").contains("/login?sso=");
            long t2 = now + 1;
            assertThat(noFollow("/api/auth/sso?u=UAS003&t=" + t2 + "&s=" + com.ittools.platform.security.SsoService.sign(secret, "UAS003", t2) + "&r=//evil.com")[1])
                    .as("不允许跳到站外").endsWith("/student/home");
        } finally {
            call(admin, HttpMethod.PUT, "/api/admin/settings", map("sso.enabled", "false"));
        }
    }

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private void jdbcNullTerm(long lessonId) {
        jdbcTemplate.update("UPDATE lesson SET term_id = NULL WHERE id = ?", lessonId);
    }

    private JsonNode user(HttpHeaders admin, long id) throws Exception {
        for (JsonNode u : call(admin, HttpMethod.GET, "/api/admin/users?role=STUDENT", null)) if (u.get("id").asLong() == id) return u;
        throw new AssertionError("找不到用户 " + id);
    }

    /** 用原始连接登录，取状态码（401 时 TestRestTemplate 的默认客户端会抛异常）。 */
    private int loginStatus(String body) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("http://localhost:" + port + "/api/auth/login").openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.getOutputStream().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        int code = c.getResponseCode();
        c.disconnect();
        return code;
    }
}
