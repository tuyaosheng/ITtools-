package com.ittools.homework.service;

import com.ittools.classroom.service.LessonService;
import com.ittools.interact.service.MpyService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/**
 * 测验 + 操作组合统分（原 coursetestview）：一堂课里各测验页、作品提交页、Python 页、掌控板页的成绩合并成一张表，
 * 再加上课堂表现分，按学生汇总，可导出 Excel。
 */
@Service
public class LessonScoreService {
    private final NamedParameterJdbcTemplate jdbc;
    private final LessonService lessons;
    private final MpyService mpy;

    public LessonScoreService(NamedParameterJdbcTemplate jdbc, LessonService lessons, MpyService mpy) {
        this.jdbc = jdbc;
        this.lessons = lessons;
        this.mpy = mpy;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> scores(Long teacherId, Long lessonId) {
        Map<String, Object> lesson = lessons.requireTeacher(teacherId, lessonId);
        List<Map<String, Object>> columns = new ArrayList<>();
        Map<String, Map<Long, Object>> values = new HashMap<>();
        for (Map<String, Object> pg : jdbc.queryForList("SELECT id, title, page_type, score, ref_id FROM course_page WHERE course_id = :c "
                + "AND page_type IN ('QUIZ', 'HOMEWORK', 'PYTHON', 'MPYTHON') ORDER BY seq", p("c", lesson.get("courseId")))) {
            Long pageId = ((Number) pg.get("id")).longValue();
            String type = (String) pg.get("page_type");
            Map<Long, Object> v = new HashMap<>();
            Object full;
            if ("QUIZ".equals(type)) {
                List<Map<String, Object>> t = jdbc.queryForList("SELECT id, (SELECT COALESCE(SUM(score), 0) FROM paper_question pq WHERE pq.paper_id = t.paper_id) AS full "
                        + "FROM quiz_test t WHERE lesson_id = :l AND page_id = :p", p("l", lessonId, "p", pageId));
                if (t.isEmpty()) continue;   // 本堂课没有开放过该测验
                full = t.get(0).get("full");
                for (Map<String, Object> r : jdbc.queryForList("SELECT student_id, score FROM quiz_attempt WHERE test_id = :t AND status = 'SUBMITTED'",
                        p("t", t.get(0).get("id")))) v.put(((Number) r.get("student_id")).longValue(), r.get("score"));
            } else if ("HOMEWORK".equals(type)) {
                List<Map<String, Object>> rows = jdbc.queryForList("SELECT student_id, COALESCE(teacher_score, auto_score) AS score, auto_max FROM submission "
                        + "WHERE lesson_id = :l AND page_id = :p", p("l", lessonId, "p", pageId));
                if (rows.isEmpty()) continue;
                int pageScore = ((Number) pg.get("score")).intValue();
                full = pageScore > 0 ? pageScore : rows.get(0).get("auto_max");
                for (Map<String, Object> r : rows) v.put(((Number) r.get("student_id")).longValue(), r.get("score"));
            } else if ("MPYTHON".equals(type)) {
                int pageScore = ((Number) pg.get("score")).intValue();
                Map<Long, Double> sc = mpy.lessonScores(lessonId, pageId, pageScore);
                if (sc.isEmpty()) continue;
                full = mpy.fullScore(pageId, pageScore);
                v.putAll(sc);
            } else {
                List<Map<String, Object>> rows = jdbc.queryForList("SELECT student_id, score, max_score FROM py_submission "
                        + "WHERE lesson_id = :l AND page_id = :p AND score IS NOT NULL", p("l", lessonId, "p", pageId));
                if (rows.isEmpty()) continue;
                full = rows.get(0).get("max_score");
                for (Map<String, Object> r : rows) v.put(((Number) r.get("student_id")).longValue(), r.get("score"));
            }
            String key = "p" + pageId;
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("key", key);
            col.put("title", pg.get("title"));
            col.put("type", type);
            col.put("full", full);
            columns.add(col);
            values.put(key, v);
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT s.id AS \"studentId\", s.student_no AS \"studentNo\", s.name, a.status, a.score AS \"lessonScore\" "
                + "FROM users s LEFT JOIN lesson_attendance a ON a.lesson_id = :l AND a.student_id = s.id "
                + "WHERE s.class_id = :k AND s.role = 'STUDENT' AND NOT s.graduated ORDER BY s.student_no NULLS LAST, s.name",
                p("l", lessonId, "k", lesson.get("classId")));
        for (Map<String, Object> r : rows) {
            Long sid = ((Number) r.get("studentId")).longValue();
            double total = 0;
            Map<String, Object> scores = new LinkedHashMap<>();
            for (Map<String, Object> c : columns) {
                Object v = values.get(c.get("key")).get(sid);
                scores.put((String) c.get("key"), v);
                if (v != null) total += ((Number) v).doubleValue();
            }
            if (r.get("lessonScore") != null) total += ((Number) r.get("lessonScore")).doubleValue();
            r.put("scores", scores);
            r.put("total", Math.round(total * 10) / 10.0);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("columns", columns);
        out.put("rows", rows);
        return out;
    }

    /**
     * 座位图标记（原 viewcoursestuseat：已阅 / 前10名 / 精品）：每位学生本堂课提交作品数、未批阅数、精华数、课堂总分与是否前 10 名。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> seatFlags(Long teacherId, Long lessonId) {
        @SuppressWarnings("unchecked") List<Map<String, Object>> rows = (List<Map<String, Object>>) scores(teacherId, lessonId).get("rows");
        Map<Long, Map<String, Object>> sub = new HashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT student_id, COUNT(*) AS works, COUNT(*) FILTER (WHERE teacher_score IS NULL) AS ungraded, "
                + "COUNT(*) FILTER (WHERE essence) AS essence FROM submission WHERE lesson_id = :l GROUP BY student_id", p("l", lessonId))) {
            sub.put(((Number) r.get("student_id")).longValue(), r);
        }
        List<Double> totals = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Object t = r.get("total");
            if (t != null && ((Number) t).doubleValue() > 0) totals.add(((Number) t).doubleValue());
        }
        totals.sort(java.util.Collections.reverseOrder());
        double cut = totals.isEmpty() ? Double.MAX_VALUE : totals.get(Math.min(9, totals.size() - 1));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            long sid = ((Number) r.get("studentId")).longValue();
            Map<String, Object> s = sub.get(sid);
            double total = r.get("total") == null ? 0 : ((Number) r.get("total")).doubleValue();
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("studentId", sid);
            f.put("works", s == null ? 0 : s.get("works"));
            f.put("ungraded", s == null ? 0 : s.get("ungraded"));
            f.put("essence", s == null ? 0 : s.get("essence"));
            f.put("total", total);
            f.put("top10", total > 0 && total >= cut);
            out.add(f);
        }
        return out;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public byte[] export(Long teacherId, Long lessonId) {
        Map<String, Object> data = scores(teacherId, lessonId);
        List<Map<String, Object>> cols = (List<Map<String, Object>>) data.get("columns");
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sh = wb.createSheet("课堂成绩");
            org.apache.poi.ss.usermodel.Row h = sh.createRow(0);
            int c = 0;
            h.createCell(c++).setCellValue("学号");
            h.createCell(c++).setCellValue("姓名");
            for (Map<String, Object> col : cols) h.createCell(c++).setCellValue(col.get("title") + "（" + col.get("full") + "）");
            h.createCell(c++).setCellValue("课堂表现");
            h.createCell(c).setCellValue("合计");
            int r = 1;
            for (Map<String, Object> row : (List<Map<String, Object>>) data.get("rows")) {
                org.apache.poi.ss.usermodel.Row x = sh.createRow(r++);
                c = 0;
                x.createCell(c++).setCellValue(row.get("studentNo") == null ? "" : String.valueOf(row.get("studentNo")));
                x.createCell(c++).setCellValue(String.valueOf(row.get("name")));
                Map<String, Object> scores = (Map<String, Object>) row.get("scores");
                for (Map<String, Object> col : cols) {
                    Object v = scores.get(col.get("key"));
                    org.apache.poi.ss.usermodel.Cell cell = x.createCell(c++);
                    if (v != null) cell.setCellValue(((Number) v).doubleValue());
                }
                org.apache.poi.ss.usermodel.Cell ls = x.createCell(c++);
                if (row.get("lessonScore") != null) ls.setCellValue(((Number) row.get("lessonScore")).doubleValue());
                x.createCell(c).setCellValue(((Number) row.get("total")).doubleValue());
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
