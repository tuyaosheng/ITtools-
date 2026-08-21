package com.ittools.platform;

import com.ittools.platform.domain.Klass;
import com.ittools.platform.domain.Role;
import com.ittools.platform.domain.SchoolYear;
import com.ittools.platform.domain.User;
import com.ittools.platform.repository.KlassRepository;
import com.ittools.platform.repository.SchoolYearRepository;
import com.ittools.platform.repository.UserRepository;
import com.ittools.platform.service.ImportService;
import com.ittools.platform.service.dto.ImportResult;
import com.ittools.support.AbstractPostgresIT;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ImportServiceIT extends AbstractPostgresIT {

    @Autowired ImportService importService;
    @Autowired UserRepository users;
    @Autowired SchoolYearRepository years;
    @Autowired KlassRepository classes;
    @Autowired PasswordEncoder encoder;

    private byte[] studentXlsx(String[][] rows) throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("students");
        String[] header = {"姓名", "学号", "学籍号", "年级码", "班级", "初始密码"};
        Row h = sheet.createRow(0);
        for (int c = 0; c < header.length; c++) h.createCell(c).setCellValue(header[c]);
        for (int r = 0; r < rows.length; r++) {
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < rows[r].length; c++) row.createCell(c).setCellValue(rows[r][c]);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return out.toByteArray();
    }

    @Test
    void importsValidStudentsAutoCreatesYearAndClassAndReportsErrorRows() throws Exception {
        String[][] rows = {
            {"张三", "001", "X100001", "2099", "1班", ""},        // valid, default password
            {"李四", "002", "X100002", "2099", "1班", "pw456"},   // valid, explicit password
            {"王五", "003", "X100001", "2099", "2班", ""},        // ERROR: duplicate xjh X100001
            {"", "004", "X100004", "2099", "1班", ""}             // ERROR: missing 姓名
        };
        byte[] file = studentXlsx(rows);

        ImportResult result = importService.importStudents(new ByteArrayInputStream(file), "students.xlsx");

        assertThat(result.getTotal()).isEqualTo(4);
        assertThat(result.getImported()).isEqualTo(2);
        assertThat(result.getFailed()).isEqualTo(2);
        assertThat(result.getErrors()).hasSize(2);
        // error rows carry the 1-based data row number (spreadsheet row minus the header)
        List<Integer> errorRows = result.getErrors().stream().map(ImportResult.RowError::getRow).sorted().collect(java.util.stream.Collectors.toList());
        assertThat(errorRows).containsExactly(3, 4);

        // year + class auto-created
        Optional<SchoolYear> y = years.findAll().stream().filter(sy -> sy.getYearCode().equals("2099")).findFirst();
        assertThat(y).isPresent();
        Optional<Klass> k = classes.findBySchoolYear_IdOrderByDisplayOrderAscNameAsc(y.get().getId())
                .stream().filter(cl -> cl.getName().equals("1班")).findFirst();
        assertThat(k).isPresent();

        // 张三 imported as STUDENT in 1班 with default password 123456 (BCrypt-hashed)
        User zhang = users.findByKlass_IdAndNameAndRole(k.get().getId(), "张三", Role.STUDENT)
                .orElseThrow(() -> new AssertionError("张三 not imported"));
        assertThat(zhang.getPasswordHash()).isNotEqualTo("123456");
        assertThat(encoder.matches("123456", zhang.getPasswordHash())).isTrue();
        assertThat(zhang.getXjh()).isEqualTo("X100001");

        // 李四 uses the explicit password
        User li = users.findByKlass_IdAndNameAndRole(k.get().getId(), "李四", Role.STUDENT)
                .orElseThrow(() -> new AssertionError("李四 not imported"));
        assertThat(encoder.matches("pw456", li.getPasswordHash())).isTrue();

        // the two error rows did NOT create users
        assertThat(users.findByXjh("X100004")).isEmpty();
    }

    private byte[] teacherXlsx(String[][] rows) throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("teachers");
        String[] header = {"姓名", "登录名", "初始密码"};
        Row h = sheet.createRow(0);
        for (int c = 0; c < header.length; c++) h.createCell(c).setCellValue(header[c]);
        for (int r = 0; r < rows.length; r++) {
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < rows[r].length; c++) row.createCell(c).setCellValue(rows[r][c]);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return out.toByteArray();
    }

    @Test
    void importsValidTeachersAndReportsDuplicateLoginAndMissingName() throws Exception {
        // Login names are deliberately unique to this test — the suite shares one
        // Testcontainers DB across classes, so a name another IT also creates would
        // collide and be (correctly) rejected as a duplicate.
        String[][] rows = {
            {"陈老师", "imp_teacher_a", ""},          // valid, default password
            {"王老师", "imp_teacher_b", "pw789"},     // valid, explicit password
            {"李老师", "imp_teacher_a", ""},          // ERROR: duplicate loginName
            {"", "imp_teacher_c", ""}                 // ERROR: missing 姓名
        };
        byte[] file = teacherXlsx(rows);

        ImportResult result = importService.importTeachers(new ByteArrayInputStream(file), "teachers.xlsx");

        assertThat(result.getTotal()).isEqualTo(4);
        assertThat(result.getImported()).isEqualTo(2);
        assertThat(result.getFailed()).isEqualTo(2);

        User chen = users.findByLoginName("imp_teacher_a").orElseThrow(() -> new AssertionError("imp_teacher_a not imported"));
        assertThat(chen.getRole()).isEqualTo(Role.TEACHER);
        assertThat(encoder.matches("123456", chen.getPasswordHash())).isTrue();

        User wang = users.findByLoginName("imp_teacher_b").orElseThrow(() -> new AssertionError("imp_teacher_b not imported"));
        assertThat(encoder.matches("pw789", wang.getPasswordHash())).isTrue();

        assertThat(users.findByLoginName("imp_teacher_c")).isEmpty();
    }
}
