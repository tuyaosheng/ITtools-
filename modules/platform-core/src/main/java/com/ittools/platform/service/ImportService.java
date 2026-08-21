package com.ittools.platform.service;

import com.ittools.platform.domain.Klass;
import com.ittools.platform.domain.Role;
import com.ittools.platform.domain.SchoolYear;
import com.ittools.platform.domain.User;
import com.ittools.platform.repository.KlassRepository;
import com.ittools.platform.repository.SchoolYearRepository;
import com.ittools.platform.repository.UserRepository;
import com.ittools.platform.service.dto.ImportResult;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Batch-imports students and teachers from an uploaded spreadsheet
 * (.xlsx/.xls/.csv). Valid rows are persisted; invalid rows are collected
 * with a per-row reason (partial success). Passwords are BCrypt-hashed.
 */
@Service
public class ImportService {

    private static final String DEFAULT_PASSWORD = "123456";

    private final UserRepository users;
    private final KlassRepository classes;
    private final SchoolYearRepository years;
    private final PasswordEncoder encoder;
    private final SpreadsheetReader reader = new SpreadsheetReader();

    public ImportService(UserRepository users, KlassRepository classes,
                         SchoolYearRepository years, PasswordEncoder encoder) {
        this.users = users;
        this.classes = classes;
        this.years = years;
        this.encoder = encoder;
    }

    @Transactional
    public ImportResult importStudents(InputStream in, String filename) {
        List<List<String>> rows = readRows(in, filename);
        Map<String, Integer> col = headerIndex(rows.get(0));
        requireColumns(col, "姓名", "年级码", "班级");

        ImportResult result = new ImportResult();
        Map<String, SchoolYear> yearCache = new HashMap<String, SchoolYear>();
        Map<String, Klass> classCache = new HashMap<String, Klass>();
        Set<String> seenXjh = new HashSet<String>();
        Set<String> seenClassName = new HashSet<String>();

        for (int r = 1; r < rows.size(); r++) {
            List<String> cells = rows.get(r);
            String name = get(cells, col, "姓名");
            String studentNo = get(cells, col, "学号");
            String xjh = get(cells, col, "学籍号");
            String yearCode = get(cells, col, "年级码");
            String className = get(cells, col, "班级");
            String password = get(cells, col, "初始密码");

            if (isBlankRow(name, studentNo, xjh, yearCode, className, password)) {
                continue; // skip trailing empty rows
            }
            result.incrementTotal();
            int dataRow = r; // 1-based data row number (row 0 is the header)

            if (name.isEmpty()) { result.addError(dataRow, "缺少姓名"); continue; }
            if (yearCode.isEmpty()) { result.addError(dataRow, "缺少年级码"); continue; }
            if (className.isEmpty()) { result.addError(dataRow, "缺少班级"); continue; }

            if (!xjh.isEmpty()) {
                if (seenXjh.contains(xjh) || users.findByXjh(xjh).isPresent()) {
                    result.addError(dataRow, "学籍号重复：" + xjh);
                    continue;
                }
            }

            String nameKey = yearCode + "|" + className + "|" + name;
            if (seenClassName.contains(nameKey)) {
                result.addError(dataRow, "同班同名：" + name);
                continue;
            }

            SchoolYear year = resolveYear(yearCache, yearCode);
            Klass klass = resolveClass(classCache, year, yearCode, className);

            if (users.findByKlass_IdAndNameAndRole(klass.getId(), name, Role.STUDENT).isPresent()) {
                result.addError(dataRow, "同班同名：" + name);
                continue;
            }

            User u = new User();
            u.setRole(Role.STUDENT);
            u.setName(name);
            u.setStudentNo(emptyToNull(studentNo));
            u.setXjh(emptyToNull(xjh));
            u.setKlass(klass);
            u.setEnrollYear(year);
            u.setPasswordHash(encoder.encode(password.isEmpty() ? DEFAULT_PASSWORD : password));
            u.setEnabled(true);
            users.save(u);

            if (!xjh.isEmpty()) seenXjh.add(xjh);
            seenClassName.add(nameKey);
            result.incrementImported();
        }
        return result;
    }

    @Transactional
    public ImportResult importTeachers(InputStream in, String filename) {
        List<List<String>> rows = readRows(in, filename);
        Map<String, Integer> col = headerIndex(rows.get(0));
        requireColumns(col, "姓名", "登录名");

        ImportResult result = new ImportResult();
        Set<String> seenLogin = new HashSet<String>();

        for (int r = 1; r < rows.size(); r++) {
            List<String> cells = rows.get(r);
            String name = get(cells, col, "姓名");
            String loginName = get(cells, col, "登录名");
            String password = get(cells, col, "初始密码");

            if (isBlankRow(name, loginName, password)) {
                continue;
            }
            result.incrementTotal();
            int dataRow = r;

            if (name.isEmpty()) { result.addError(dataRow, "缺少姓名"); continue; }
            if (loginName.isEmpty()) { result.addError(dataRow, "缺少登录名"); continue; }
            if (seenLogin.contains(loginName) || users.findByLoginName(loginName).isPresent()) {
                result.addError(dataRow, "登录名重复：" + loginName);
                continue;
            }

            User u = new User();
            u.setRole(Role.TEACHER);
            u.setName(name);
            u.setLoginName(loginName);
            u.setPasswordHash(encoder.encode(password.isEmpty() ? DEFAULT_PASSWORD : password));
            u.setEnabled(true);
            users.save(u);

            seenLogin.add(loginName);
            result.incrementImported();
        }
        return result;
    }

    // ---- helpers ----------------------------------------------------------

    private List<List<String>> readRows(InputStream in, String filename) {
        List<List<String>> rows;
        try {
            rows = reader.read(in, filename);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析文件：" + e.getMessage());
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }
        return rows;
    }

    private Map<String, Integer> headerIndex(List<String> header) {
        Map<String, Integer> col = new HashMap<String, Integer>();
        for (int c = 0; c < header.size(); c++) {
            String h = header.get(c) == null ? "" : header.get(c).trim();
            if (!h.isEmpty()) col.put(h, c);
        }
        return col;
    }

    private void requireColumns(Map<String, Integer> col, String... required) {
        for (String r : required) {
            if (!col.containsKey(r)) {
                throw new IllegalArgumentException("模板表头缺少必要列：" + r);
            }
        }
    }

    private String get(List<String> cells, Map<String, Integer> col, String name) {
        Integer idx = col.get(name);
        if (idx == null || idx >= cells.size()) return "";
        String v = cells.get(idx);
        return v == null ? "" : v.trim();
    }

    private boolean isBlankRow(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) return false;
        }
        return true;
    }

    private String emptyToNull(String v) {
        return (v == null || v.isEmpty()) ? null : v;
    }

    private SchoolYear resolveYear(Map<String, SchoolYear> cache, String yearCode) {
        SchoolYear cached = cache.get(yearCode);
        if (cached != null) return cached;
        Optional<SchoolYear> existing = years.findAll().stream()
                .filter(y -> y.getYearCode().equals(yearCode)).findFirst();
        SchoolYear year = existing.orElseGet(() ->
                years.save(new SchoolYear(yearCode, yearCode + "级", true)));
        cache.put(yearCode, year);
        return year;
    }

    private Klass resolveClass(Map<String, Klass> cache, SchoolYear year, String yearCode, String className) {
        String key = yearCode + "|" + className;
        Klass cached = cache.get(key);
        if (cached != null) return cached;
        Optional<Klass> existing = classes.findBySchoolYear_IdOrderByDisplayOrderAscNameAsc(year.getId())
                .stream().filter(k -> k.getName().equals(className)).findFirst();
        Klass klass = existing.orElseGet(() ->
                classes.save(new Klass(year, className, 0)));
        cache.put(key, klass);
        return klass;
    }
}
