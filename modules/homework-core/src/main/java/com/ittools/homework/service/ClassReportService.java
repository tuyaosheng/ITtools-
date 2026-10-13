package com.ittools.homework.service;

import com.ittools.classroom.service.ClassBindingService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/**
 * 班级学习情况综合查询（原 QueryResults：出席 / 作品 / 操作题 / 课后作业 / 测验 五张报表 + 期末统计）。
 * 各类得分统一折算为百分制（作品教师评分本身即百分制，自动评分按 auto_max 折算；其余为 得分 / 满分 × 100），期末总评 = 各项百分制按权重加权平均（缺项不计权重）。
 */
@Service
public class ClassReportService {
    public static final List<String> KINDS = java.util.Arrays.asList("ATTENDANCE", "WORKS", "PYTHON", "HOMEWORK", "TEST");

    private final NamedParameterJdbcTemplate jdbc;
    private final ClassBindingService bindings;

    public ClassReportService(NamedParameterJdbcTemplate jdbc, ClassBindingService bindings) {
        this.jdbc = jdbc;
        this.bindings = bindings;
    }

    private void check(Long teacherId, Long classId) {
        if (!bindings.teacherCanUseClass(teacherId, classId)) throw new IllegalArgumentException("无权查看该班级");
    }

    private static String lessonScope(Long termId) {
        return "SELECT id FROM lesson WHERE class_id = :k" + (termId == null ? "" : " AND term_id = :term");
    }

    private static String testScope(Long termId) {
        return termId == null ? "" : " AND t.lesson_id IN (" + lessonScope(termId) + ")";
    }

    private List<Map<String, Object>> students(Long classId) {
        return jdbc.queryForList("SELECT id AS \"studentId\", student_no AS \"studentNo\", name FROM users "
                + "WHERE class_id = :k AND role = 'STUDENT' AND NOT graduated ORDER BY student_no NULLS LAST, name", p("k", classId));
    }

    /** 明细报表：行=学生，列=课堂/作品/题目/作业/测验，单元格=状态或得分。 */
    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long teacherId, Long classId, Long termId, String kind) {
        check(teacherId, classId);
        return detailUnchecked(classId, termId, kind);
    }

    private Map<String, Object> detailUnchecked(Long classId, Long termId, String kind) {
        if (!KINDS.contains(kind)) throw new IllegalArgumentException("未知报表类型");
        String ls = lessonScope(termId);
        org.springframework.jdbc.core.namedparam.MapSqlParameterSource args = p("k", classId, "term", termId);
        List<Map<String, Object>> columns;
        List<Map<String, Object>> cells;
        switch (kind) {
            case "ATTENDANCE":
                columns = jdbc.queryForList("SELECT l.id::text AS key, c.name || ' ' || to_char(l.started_at, 'MM-DD') AS title, 0 AS full "
                        + "FROM lesson l JOIN course c ON c.id = l.course_id WHERE l.id IN (" + ls + ") ORDER BY l.started_at", args);
                cells = jdbc.queryForList("SELECT a.lesson_id::text AS key, a.student_id AS \"studentId\", a.status AS value, a.score "
                        + "FROM lesson_attendance a WHERE a.lesson_id IN (" + ls + ")", args);
                break;
            case "WORKS":
                columns = jdbc.queryForList("SELECT DISTINCT s.lesson_id || '-' || s.page_id AS key, cp.title || ' ' || to_char(l.started_at, 'MM-DD') AS title, "
                        + "100 AS full, l.started_at FROM submission s JOIN lesson l ON l.id = s.lesson_id JOIN course_page cp ON cp.id = s.page_id "
                        + "WHERE s.lesson_id IN (" + ls + ") ORDER BY l.started_at, key", args);
                cells = jdbc.queryForList("SELECT s.lesson_id || '-' || s.page_id AS key, s.student_id AS \"studentId\", "
                        + "COALESCE(s.teacher_score, ROUND(s.auto_score * 100.0 / NULLIF(s.auto_max, 0), 1)) AS score, s.essence FROM submission s WHERE s.lesson_id IN (" + ls + ")", args);
                break;
            case "PYTHON":
                columns = jdbc.queryForList("SELECT DISTINCT ps.lesson_id || '-' || ps.page_id AS key, cp.title || ' ' || to_char(l.started_at, 'MM-DD') AS title, "
                        + "100 AS full, l.started_at FROM py_submission ps JOIN lesson l ON l.id = ps.lesson_id JOIN course_page cp ON cp.id = ps.page_id "
                        + "WHERE ps.lesson_id IN (" + ls + ") ORDER BY l.started_at, key", args);
                cells = jdbc.queryForList("SELECT ps.lesson_id || '-' || ps.page_id AS key, ps.student_id AS \"studentId\", "
                        + "ROUND(ps.score * 100.0 / NULLIF(ps.max_score, 0), 1) AS score FROM py_submission ps WHERE ps.lesson_id IN (" + ls + ") AND ps.score IS NOT NULL", args);
                break;
            default:
                args.addValue("kind", kind);
                columns = jdbc.queryForList("SELECT t.id::text AS key, t.title, t.finalized_at IS NOT NULL AS finalized, (SELECT COALESCE(SUM(pq.score), 0) FROM paper_question pq WHERE pq.paper_id = t.paper_id) AS full "
                        + "FROM quiz_test t WHERE t.class_id = :k AND t.kind = :kind AND t.status <> 'STANDBY'" + testScope(termId) + " ORDER BY t.created_at", args);
                cells = jdbc.queryForList("SELECT qa.test_id::text AS key, qa.student_id AS \"studentId\", qa.score, qa.status AS value "
                        + "FROM quiz_attempt qa JOIN quiz_test t ON t.id = qa.test_id WHERE t.class_id = :k AND t.kind = :kind AND t.status <> 'STANDBY'" + testScope(termId), args);
        }
        Map<Long, Map<String, Object>> byStudent = new LinkedHashMap<>();
        for (Map<String, Object> s : students(classId)) {
            Map<String, Object> row = new LinkedHashMap<>(s);
            row.put("cells", new HashMap<String, Object>());
            byStudent.put(((Number) s.get("studentId")).longValue(), row);
        }
        for (Map<String, Object> c : cells) {
            Map<String, Object> row = byStudent.get(((Number) c.get("studentId")).longValue());
            if (row == null) continue;
            Map<String, Object> cell = new LinkedHashMap<>(c);
            cell.remove("studentId");
            cell.remove("key");
            @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) row.get("cells");
            m.put(String.valueOf(c.get("key")), cell);
        }
        for (Map<String, Object> col : columns) col.remove("started_at");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", kind);
        out.put("columns", columns);
        out.put("rows", new ArrayList<>(byStudent.values()));
        return out;
    }

    /** 期末统计：每人各项汇总 + 百分制 + 加权总评（课后作业只计已结分的）。weights 依次为 出席/作品/操作题/课后作业/测验。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> summary(Long teacherId, Long classId, Long termId, double[] weights) {
        check(teacherId, classId);
        return summaryUnchecked(classId, termId, weights);
    }

    private List<Map<String, Object>> summaryUnchecked(Long classId, Long termId, double[] weights) {
        String ls = lessonScope(termId);
        String ts = testScope(termId);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT u.id AS \"studentId\", u.student_no AS \"studentNo\", u.name, "
                + "(SELECT COUNT(*) FROM lesson WHERE id IN (" + ls + ")) AS lessons, "
                // 已下课却没有考勤记录的课堂（缺勤记录被“消除”、或学生后来才转入）不计入出勤率分母
                + "(SELECT COUNT(*) FROM lesson l2 WHERE l2.id IN (" + ls + ") AND l2.status = 'ENDED' "
                + "  AND NOT EXISTS (SELECT 1 FROM lesson_attendance a WHERE a.lesson_id = l2.id AND a.student_id = u.id)) AS excused, "
                + "(SELECT COUNT(*) FROM lesson_attendance a WHERE a.student_id = u.id AND a.lesson_id IN (" + ls + ") AND a.status = 'PRESENT') AS present, "
                + "(SELECT COUNT(*) FROM lesson_attendance a WHERE a.student_id = u.id AND a.lesson_id IN (" + ls + ") AND a.status = 'LEAVE') AS leave, "
                + "(SELECT ROUND(AVG(a.score), 1) FROM lesson_attendance a WHERE a.student_id = u.id AND a.lesson_id IN (" + ls + ") AND a.score IS NOT NULL) AS \"lessonScore\", "
                + "(SELECT COUNT(*) FROM submission s WHERE s.student_id = u.id AND s.lesson_id IN (" + ls + ")) AS works, "
                + "(SELECT COUNT(*) FROM submission s WHERE s.student_id = u.id AND s.lesson_id IN (" + ls + ") AND s.essence) AS essence, "
                + "(SELECT ROUND(AVG(COALESCE(s.teacher_score, ROUND(s.auto_score * 100.0 / NULLIF(s.auto_max, 0), 1))), 1) FROM submission s "
                + "  WHERE s.student_id = u.id AND s.lesson_id IN (" + ls + ")) AS \"workPct\", "
                + "(SELECT COUNT(*) FROM py_submission ps WHERE ps.student_id = u.id AND ps.lesson_id IN (" + ls + ") AND ps.score IS NOT NULL) AS python, "
                + "(SELECT ROUND(AVG(ps.score * 100.0 / NULLIF(ps.max_score, 0)), 1) FROM py_submission ps WHERE ps.student_id = u.id "
                + "  AND ps.lesson_id IN (" + ls + ") AND ps.score IS NOT NULL) AS \"pythonPct\", "
                + "(SELECT COUNT(*) FROM quiz_test t WHERE t.class_id = :k AND t.kind = 'HOMEWORK' AND t.status <> 'STANDBY'" + ts + ") AS \"homeworkTotal\", "
                + "(SELECT COUNT(*) FROM quiz_attempt qa JOIN quiz_test t ON t.id = qa.test_id WHERE qa.student_id = u.id AND t.class_id = :k "
                + "  AND t.kind = 'HOMEWORK' AND qa.status IN ('SUBMITTED','RETURNED')" + ts + ") AS \"homeworkDone\", "
                + "(SELECT ROUND(AVG(qa.score * 100.0 / NULLIF((SELECT SUM(pq.score) FROM paper_question pq WHERE pq.paper_id = t.paper_id), 0)), 1) "
                + "  FROM quiz_attempt qa JOIN quiz_test t ON t.id = qa.test_id WHERE qa.student_id = u.id AND t.class_id = :k AND t.kind = 'HOMEWORK' "
                + "  AND qa.status = 'SUBMITTED' AND qa.score IS NOT NULL AND t.finalized_at IS NOT NULL" + ts + ") AS \"homeworkPct\", "
                + "(SELECT COUNT(*) FROM quiz_test t WHERE t.class_id = :k AND t.kind = 'TEST' AND t.status <> 'STANDBY'" + ts + ") AS \"testTotal\", "
                + "(SELECT COUNT(*) FROM quiz_attempt qa JOIN quiz_test t ON t.id = qa.test_id WHERE qa.student_id = u.id AND t.class_id = :k "
                + "  AND t.kind = 'TEST' AND qa.status = 'SUBMITTED'" + ts + ") AS \"testDone\", "
                + "(SELECT ROUND(AVG(qa.score * 100.0 / NULLIF((SELECT SUM(pq.score) FROM paper_question pq WHERE pq.paper_id = t.paper_id), 0)), 1) "
                + "  FROM quiz_attempt qa JOIN quiz_test t ON t.id = qa.test_id WHERE qa.student_id = u.id AND t.class_id = :k AND t.kind = 'TEST' "
                + "  AND qa.status = 'SUBMITTED' AND qa.score IS NOT NULL" + ts + ") AS \"testPct\" "
                + "FROM users u WHERE u.class_id = :k AND u.role = 'STUDENT' AND NOT u.graduated ORDER BY u.student_no NULLS LAST, u.name",
                p("k", classId, "term", termId));
        for (Map<String, Object> r : rows) {
            long lessons = ((Number) r.get("lessons")).longValue() - ((Number) r.get("excused")).longValue();
            r.put("lessons", lessons);
            Double att = lessons == 0 ? null : ((Number) r.get("present")).doubleValue() * 100 / lessons;
            r.put("attendancePct", att == null ? null : round(att));
            Double[] parts = {att, num(r.get("workPct")), num(r.get("pythonPct")), num(r.get("homeworkPct")), num(r.get("testPct"))};
            double sum = 0, w = 0;
            for (int i = 0; i < parts.length; i++) {
                if (parts[i] == null || weights[i] <= 0) continue;
                sum += parts[i] * weights[i];
                w += weights[i];
            }
            r.put("total", w == 0 ? null : round(sum / w));
        }
        List<Map<String, Object>> ranked = new ArrayList<>(rows);
        ranked.sort((a, b) -> Double.compare(num0(b.get("total")), num0(a.get("total"))));
        int rank = 0;
        Double prev = null;
        for (int i = 0; i < ranked.size(); i++) {
            Double t = num(ranked.get(i).get("total"));
            if (t == null) continue;
            if (!t.equals(prev)) rank = i + 1;
            prev = t;
            ranked.get(i).put("rank", rank);
        }
        return rows;
    }

    /** 学生个人综合学情：期末统计一行 + 五类明细（仅本人）。teacherId 为空表示学生本人查看。 */
    @Transactional(readOnly = true)
    public Map<String, Object> studentReport(Long teacherId, Long studentId, Long termId, double[] weights) {
        List<Long> k = jdbc.queryForList("SELECT class_id FROM users WHERE id = :s AND role = 'STUDENT' AND class_id IS NOT NULL", p("s", studentId), Long.class);
        if (k.isEmpty()) throw new IllegalArgumentException("学生不存在或未分班");
        Long classId = k.get(0);
        Long viewer = teacherId == null ? -1L : teacherId; // -1：学生本人查看，不做教师班级权限检查
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> sum = viewer < 0 ? summaryUnchecked(classId, termId, weights) : summary(viewer, classId, termId, weights);
        Map<String, Object> me = null;
        for (Map<String, Object> r : sum) if (((Number) r.get("studentId")).longValue() == studentId) me = r;
        out.put("summary", me);
        out.put("classSize", sum.size());
        Map<String, Object> details = new LinkedHashMap<>();
        for (String kind : KINDS) {
            Map<String, Object> d = viewer < 0 ? detailUnchecked(classId, termId, kind) : detail(viewer, classId, termId, kind);
            @SuppressWarnings("unchecked") List<Map<String, Object>> cols = (List<Map<String, Object>>) d.get("columns");
            @SuppressWarnings("unchecked") List<Map<String, Object>> rows = (List<Map<String, Object>>) d.get("rows");
            Map<String, Object> cells = new HashMap<>();
            for (Map<String, Object> r : rows) if (((Number) r.get("studentId")).longValue() == studentId) {
                @SuppressWarnings("unchecked") Map<String, Object> c = (Map<String, Object>) r.get("cells");
                cells = c;
            }
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> col : cols) {
                Map<String, Object> it = new LinkedHashMap<>(col);
                @SuppressWarnings("unchecked") Map<String, Object> cell = (Map<String, Object>) cells.get(String.valueOf(col.get("key")));
                // 学生本人：课后作业须教师结分后才显示得分（与作业页面一致）
                if (teacherId == null && cell != null && "HOMEWORK".equals(kind) && !Boolean.TRUE.equals(col.get("finalized"))) {
                    cell = new LinkedHashMap<>(cell);
                    cell.put("score", null);
                }
                it.put("cell", cell);
                items.add(it);
            }
            details.put(kind, items);
        }
        out.put("details", details);
        return out;
    }

    private static Double num(Object o) {
        return o == null ? null : ((Number) o).doubleValue();
    }

    private static double num0(Object o) {
        return o == null ? -1 : ((Number) o).doubleValue();
    }

    private static double round(double d) {
        return BigDecimal.valueOf(d).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    @Transactional(readOnly = true)
    public byte[] export(Long teacherId, Long classId, Long termId, double[] weights) {
        String[] head = {"学号", "姓名", "出席", "出席率%", "请假", "课堂表现", "作品数", "精华", "作品%", "操作题数", "操作题%",
                "作业 已交/布置", "作业%", "测验 已考/总数", "测验%", "总评", "名次"};
        String[] keys = {"studentNo", "name", "present", "attendancePct", "leave", "lessonScore", "works", "essence", "workPct", "python", "pythonPct",
                "homeworkDone/homeworkTotal", "homeworkPct", "testDone/testTotal", "testPct", "total", "rank"};
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sh = wb.createSheet("期末统计");
            writeRow(sh.createRow(0), head);
            int r = 1;
            for (Map<String, Object> row : summary(teacherId, classId, termId, weights)) {
                org.apache.poi.ss.usermodel.Row x = sh.createRow(r++);
                for (int i = 0; i < keys.length; i++) {
                    if (keys[i].contains("/")) {
                        String[] ab = keys[i].split("/");
                        x.createCell(i).setCellValue(row.get(ab[0]) + "/" + row.get(ab[1]));
                    } else setCell(x.createCell(i), row.get(keys[i]));
                }
            }
            String[] names = {"出席情况", "作品情况", "操作题情况", "课后作业情况", "测验情况"};
            for (int k = 0; k < KINDS.size(); k++) {
                Map<String, Object> d = detail(teacherId, classId, termId, KINDS.get(k));
                org.apache.poi.ss.usermodel.Sheet ds = wb.createSheet(names[k]);
                @SuppressWarnings("unchecked") List<Map<String, Object>> cols = (List<Map<String, Object>>) d.get("columns");
                org.apache.poi.ss.usermodel.Row h = ds.createRow(0);
                h.createCell(0).setCellValue("学号");
                h.createCell(1).setCellValue("姓名");
                for (int i = 0; i < cols.size(); i++) h.createCell(i + 2).setCellValue(String.valueOf(cols.get(i).get("title")));
                int rr = 1;
                @SuppressWarnings("unchecked") List<Map<String, Object>> drows = (List<Map<String, Object>>) d.get("rows");
                for (Map<String, Object> row : drows) {
                    org.apache.poi.ss.usermodel.Row x = ds.createRow(rr++);
                    setCell(x.createCell(0), row.get("studentNo"));
                    setCell(x.createCell(1), row.get("name"));
                    @SuppressWarnings("unchecked") Map<String, Map<String, Object>> cells = (Map<String, Map<String, Object>>) row.get("cells");
                    for (int i = 0; i < cols.size(); i++) {
                        Map<String, Object> c = cells.get(String.valueOf(cols.get(i).get("key")));
                        Object v = c == null ? null : "ATTENDANCE".equals(KINDS.get(k)) ? attLabel(c.get("value")) : c.get("score");
                        setCell(x.createCell(i + 2), v);
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String attLabel(Object status) {
        if ("PRESENT".equals(status)) return "出席";
        if ("LEAVE".equals(status)) return "请假";
        if ("ABSENT".equals(status)) return "缺席";
        return status == null ? "" : String.valueOf(status);
    }

    private static void writeRow(org.apache.poi.ss.usermodel.Row row, String[] vals) {
        for (int i = 0; i < vals.length; i++) row.createCell(i).setCellValue(vals[i]);
    }

    private static void setCell(org.apache.poi.ss.usermodel.Cell c, Object v) {
        if (v instanceof Number) c.setCellValue(((Number) v).doubleValue());
        else c.setCellValue(v == null ? "" : String.valueOf(v));
    }
}
