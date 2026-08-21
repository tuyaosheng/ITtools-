package com.ittools.platform;

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

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminImportIT extends AbstractPostgresIT {

    @Autowired TestRestTemplate rest;

    private HttpHeaders adminSession() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = rest.postForEntity("/api/auth/login",
            new HttpEntity<>("{\"loginType\":\"ADMIN\",\"loginName\":\"admin\",\"password\":\"admin123\"}", h),
            String.class);
        HttpHeaders sess = new HttpHeaders();
        StringBuilder cookie = new StringBuilder();
        for (String c : login.getHeaders().get(HttpHeaders.SET_COOKIE)) {
            String pair = c.split(";", 2)[0];
            if (cookie.length() > 0) cookie.append("; ");
            cookie.append(pair);
            if (pair.startsWith("XSRF-TOKEN=")) {
                sess.add("X-XSRF-TOKEN", pair.substring("XSRF-TOKEN=".length()));
            }
        }
        sess.add(HttpHeaders.COOKIE, cookie.toString());
        return sess;
    }

    private byte[] oneStudentXlsx() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("students");
        String[] header = {"姓名", "学号", "学籍号", "年级码", "班级", "初始密码"};
        Row h = sheet.createRow(0);
        for (int c = 0; c < header.length; c++) h.createCell(c).setCellValue(header[c]);
        Row row = sheet.createRow(1);
        String[] data = {"赵六", "010", "X900010", "2088", "3班", ""};
        for (int c = 0; c < data.length; c++) row.createCell(c).setCellValue(data[c]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return out.toByteArray();
    }

    @Test
    void adminImportsStudentsViaMultipart() throws Exception {
        HttpHeaders sess = adminSession();
        sess.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource fileRes = new ByteArrayResource(oneStudentXlsx()) {
            @Override public String getFilename() { return "students.xlsx"; }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileRes);

        ResponseEntity<String> resp = rest.exchange("/api/admin/import/students",
            HttpMethod.POST, new HttpEntity<>(body, sess), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("\"imported\":1");
    }

    @Test
    void studentTemplateIsDownloadable() {
        HttpHeaders sess = adminSession();
        ResponseEntity<byte[]> resp = rest.exchange("/api/admin/import/students/template",
            HttpMethod.GET, new HttpEntity<>(sess), byte[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotEmpty();
        // .xlsx files begin with the ZIP magic bytes "PK"
        assertThat(new String(resp.getBody(), 0, 2)).isEqualTo("PK");
    }

    @Test
    void anonymousImportForbidden() throws Exception {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        ByteArrayResource fileRes = new ByteArrayResource(oneStudentXlsx()) {
            @Override public String getFilename() { return "students.xlsx"; }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileRes);
        ResponseEntity<String> resp = rest.exchange("/api/admin/import/students",
            HttpMethod.POST, new HttpEntity<>(body, h), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
