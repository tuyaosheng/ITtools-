package com.ittools.resource.service;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/** 走班选课（原 teacher/xkmanage.aspx、student/zbxk.aspx、it_zbxkkmname）。由管理员组织。 */
@Service
public class ElectiveService {
    private final NamedParameterJdbcTemplate jdbc;

    public ElectiveService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> activities() {
        return jdbc.queryForList("SELECT a.id, a.name, a.description, a.year_id AS \"yearId\", sy.label AS \"yearLabel\", a.status, "
                + "a.created_at AS \"createdAt\", (SELECT COUNT(*) FROM elective_choice c WHERE c.activity_id = a.id) AS \"chosenCount\" "
                + "FROM elective_activity a LEFT JOIN school_year sy ON sy.id = a.year_id ORDER BY a.id DESC", p());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> activity(Long id) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT a.id, a.name, a.description, a.year_id AS \"yearId\", a.status "
                + "FROM elective_activity a WHERE a.id = :id", p("id", id));
        if (l.isEmpty()) throw new IllegalArgumentException("选课活动不存在");
        Map<String, Object> a = l.get(0);
        a.put("subjects", subjects(id));
        return a;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> subjects(Long activityId) {
        return jdbc.queryForList("SELECT s.id, s.name, s.capacity, s.schedule, s.teacher_name AS \"teacherName\", s.description, "
                + "(SELECT COUNT(*) FROM elective_choice c WHERE c.subject_id = s.id) AS \"chosen\" FROM elective_subject s "
                + "WHERE s.activity_id = :a ORDER BY s.id", p("a", activityId));
    }

    @Transactional
    public Map<String, Object> save(Long adminId, Long id, String name, String description, Long yearId, List<Map<String, Object>> subjects) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("活动名称不能为空");
        if (id == null) {
            id = jdbc.queryForObject("INSERT INTO elective_activity(name, description, year_id, created_by) VALUES (:n, :d, :y, :u) RETURNING id",
                    p("n", name.trim(), "d", description, "y", yearId, "u", adminId), Long.class);
        } else {
            jdbc.update("UPDATE elective_activity SET name = :n, description = :d, year_id = :y WHERE id = :id",
                    p("n", name.trim(), "d", description, "y", yearId, "id", id));
        }
        java.util.Set<Long> keep = new java.util.HashSet<>();
        for (Map<String, Object> s : subjects) {
            String sn = s.get("name") == null ? "" : String.valueOf(s.get("name")).trim();
            if (sn.isEmpty()) throw new IllegalArgumentException("科目名称不能为空");
            int cap = s.get("capacity") == null ? 0 : ((Number) s.get("capacity")).intValue();
            if (cap <= 0) throw new IllegalArgumentException("科目人数上限须大于 0");
            Object sid = s.get("id");
            if (sid == null) {
                keep.add(jdbc.queryForObject("INSERT INTO elective_subject(activity_id, name, capacity, schedule, teacher_name, description) "
                                + "VALUES (:a, :n, :c, :s, :t, :d) RETURNING id",
                        p("a", id, "n", sn, "c", cap, "s", s.get("schedule"), "t", s.get("teacherName"), "d", s.get("description")), Long.class));
            } else {
                Long subjectId = ((Number) sid).longValue();
                Long chosen = jdbc.queryForObject("SELECT COUNT(*) FROM elective_choice WHERE subject_id = :s", p("s", subjectId), Long.class);
                if (chosen != null && cap < chosen) throw new IllegalArgumentException(sn + " 已有 " + chosen + " 人选，人数上限不能更小");
                jdbc.update("UPDATE elective_subject SET name = :n, capacity = :c, schedule = :s, teacher_name = :t, description = :d "
                                + "WHERE id = :id AND activity_id = :a",
                        p("n", sn, "c", cap, "s", s.get("schedule"), "t", s.get("teacherName"), "d", s.get("description"), "id", subjectId, "a", id));
                keep.add(subjectId);
            }
        }
        for (Long sid : jdbc.queryForList("SELECT id FROM elective_subject WHERE activity_id = :a", p("a", id), Long.class)) {
            if (!keep.contains(sid)) jdbc.update("DELETE FROM elective_subject WHERE id = :id", p("id", sid));
        }
        return activity(id);
    }

    @Transactional
    public void setStatus(Long id, String status) {
        if (!"DRAFT".equals(status) && !"OPEN".equals(status) && !"CLOSED".equals(status)) throw new IllegalArgumentException("无效状态");
        jdbc.update("UPDATE elective_activity SET status = :s WHERE id = :id", p("s", status, "id", id));
    }

    @Transactional
    public void delete(Long id) {
        jdbc.update("DELETE FROM elective_activity WHERE id = :id", p("id", id));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> roster(Long activityId) {
        return jdbc.queryForList("SELECT s.name AS \"subjectName\", u.name AS \"studentName\", u.student_no AS \"studentNo\", "
                + "sy.label || k.name AS \"className\", c.created_at AS \"chosenAt\" FROM elective_choice c "
                + "JOIN elective_subject s ON s.id = c.subject_id JOIN users u ON u.id = c.student_id "
                + "LEFT JOIN klass k ON k.id = u.class_id LEFT JOIN school_year sy ON sy.id = k.school_year_id "
                + "WHERE c.activity_id = :a ORDER BY s.id, 4, u.student_no", p("a", activityId));
    }

    @Transactional(readOnly = true)
    public byte[] exportRoster(Long activityId) {
        Map<String, Object> a = activity(activityId);
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet s = wb.createSheet("选课名单");
            s.createRow(0).createCell(0).setCellValue(String.valueOf(a.get("name")));
            String[] head = {"科目", "班级", "学号", "姓名", "选课时间"};
            org.apache.poi.ss.usermodel.Row h = s.createRow(1);
            for (int i = 0; i < head.length; i++) h.createCell(i).setCellValue(head[i]);
            int r = 2;
            for (Map<String, Object> x : roster(activityId)) {
                org.apache.poi.ss.usermodel.Row row = s.createRow(r++);
                row.createCell(0).setCellValue(String.valueOf(x.get("subjectName")));
                row.createCell(1).setCellValue(x.get("className") == null ? "" : String.valueOf(x.get("className")));
                row.createCell(2).setCellValue(x.get("studentNo") == null ? "" : String.valueOf(x.get("studentNo")));
                row.createCell(3).setCellValue(String.valueOf(x.get("studentName")));
                row.createCell(4).setCellValue(String.valueOf(x.get("chosenAt")));
            }
            wb.write(out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- 学生 ----

    /** 学生可参加的选课活动（开放中/已结束，面向本年级或全部）及本人选择。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> studentActivities(Long studentId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT a.id, a.name, a.description, a.status FROM elective_activity a "
                + "WHERE a.status IN ('OPEN', 'CLOSED') AND (a.year_id IS NULL OR a.year_id = (SELECT enroll_year_id FROM users WHERE id = :s) "
                + "OR a.year_id = (SELECT k.school_year_id FROM users u JOIN klass k ON k.id = u.class_id WHERE u.id = :s)) ORDER BY a.id DESC",
                p("s", studentId));
        for (Map<String, Object> a : l) {
            a.put("subjects", subjects(((Number) a.get("id")).longValue()));
            List<Long> c = jdbc.queryForList("SELECT subject_id FROM elective_choice WHERE activity_id = :a AND student_id = :s",
                    p("a", a.get("id"), "s", studentId), Long.class);
            a.put("mySubjectId", c.isEmpty() ? null : c.get(0));
        }
        return l;
    }

    /** 选课 / 改选（先到先得，满员不可选）。 */
    @Transactional
    public void choose(Long studentId, Long activityId, Long subjectId) {
        Map<String, Object> a = jdbc.queryForMap("SELECT status FROM elective_activity WHERE id = :id FOR UPDATE", p("id", activityId));
        if (!"OPEN".equals(a.get("status"))) throw new IllegalArgumentException("选课未开放或已结束");
        boolean visible = studentActivities(studentId).stream().anyMatch(x -> activityId.equals(((Number) x.get("id")).longValue()));
        if (!visible) throw new IllegalArgumentException("你不能参加该选课");
        if (subjectId == null) {
            jdbc.update("DELETE FROM elective_choice WHERE activity_id = :a AND student_id = :s", p("a", activityId, "s", studentId));
            return;
        }
        Map<String, Object> sub = jdbc.queryForMap("SELECT capacity, activity_id FROM elective_subject WHERE id = :id FOR UPDATE", p("id", subjectId));
        if (!activityId.equals(((Number) sub.get("activity_id")).longValue())) throw new IllegalArgumentException("科目不属于该活动");
        Long taken = jdbc.queryForObject("SELECT COUNT(*) FROM elective_choice WHERE subject_id = :s AND student_id <> :me",
                p("s", subjectId, "me", studentId), Long.class);
        if (taken != null && taken >= ((Number) sub.get("capacity")).longValue()) throw new IllegalArgumentException("该科目已满员");
        jdbc.update("INSERT INTO elective_choice(activity_id, student_id, subject_id) VALUES (:a, :s, :sub) "
                + "ON CONFLICT (activity_id, student_id) DO UPDATE SET subject_id = EXCLUDED.subject_id, created_at = now()",
                p("a", activityId, "s", studentId, "sub", subjectId));
    }
}
