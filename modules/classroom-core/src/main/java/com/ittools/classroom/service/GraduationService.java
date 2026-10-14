package com.ittools.classroom.service;

import com.ittools.platform.storage.FileStorageService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/**
 * 毕业生管理（原 admin/studentgraduate）：整班设为毕业 / 恢复在读；“释放资源空间”删除毕业生的学习记录与文件，
 * 保留账号和精华作品（原系统：删除毕业学生的所有学习信息，保留用户名和精华作品）。
 */
@Service
public class GraduationService {
    /** 释放时删除的学生学习记录：表名 → 学生列。 */
    static final String[][] LEARNING_TABLES = {
            {"lesson_attendance", "student_id"}, {"quiz_attempt", "student_id"}, {"practice_log", "student_id"}, {"practice_session", "student_id"},
            {"wrong_mastered", "student_id"}, {"py_submission", "student_id"}, {"survey_answer", "student_id"}, {"take_question_draw", "student_id"},
            {"roll_call", "student_id"}, {"page_dwell", "student_id"}, {"student_activity", "student_id"}, {"help_request", "student_id"},
            {"answer_first_entry", "student_id"}, {"study_note", "author_id"}, {"elective_choice", "student_id"}, {"lesson_group_preset", "student_id"},
            {"lab_student_seat", "user_id"}, {"login_log", "user_id"}, {"sys_message_recipient", "user_id"}, {"qa_question", "student_id"},
            {"peer_review", "reviewer_id"}
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final FileStorageService storage;

    public GraduationService(NamedParameterJdbcTemplate jdbc, FileStorageService storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    /** 各班在读/毕业人数与可释放的文件大小。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> summary() {
        return jdbc.queryForList("SELECT k.id AS \"classId\", k.name, y.id AS \"yearId\", y.label AS \"yearLabel\", "
                + "COUNT(u.id) AS students, COUNT(u.id) FILTER (WHERE u.graduated) AS graduated, "
                + "COALESCE(SUM((SELECT COALESCE(SUM(s.file_size), 0) FROM submission s WHERE s.student_id = u.id AND NOT s.essence) "
                + "  + (SELECT COALESCE(SUM(o.file_size), 0) FROM op_practice o WHERE o.student_id = u.id) "
                + "  + (SELECT COALESCE(SUM(d.file_size), 0) FROM disk_file d WHERE d.owner_id = u.id)) FILTER (WHERE u.graduated), 0) AS \"releasableBytes\" "
                + "FROM klass k JOIN school_year y ON y.id = k.school_year_id LEFT JOIN users u ON u.class_id = k.id AND u.role = 'STUDENT' "
                + "GROUP BY k.id, k.name, y.id, y.label, y.year_code, k.display_order ORDER BY y.year_code, k.display_order, k.name", p());
    }

    /** 整班设为毕业（或恢复在读）。毕业学生不再出现在登录名单、课堂与报表中。 */
    @Transactional
    public int setGraduated(List<Long> classIds, boolean graduated) {
        if (classIds == null || classIds.isEmpty()) throw new IllegalArgumentException("请选择班级");
        return jdbc.update("UPDATE users SET graduated = :g, updated_at = now() WHERE role = 'STUDENT' AND class_id IN (:k)",
                p("g", graduated, "k", classIds));
    }

    /** 释放资源空间：只处理所选班级中已毕业的学生。 */
    @Transactional
    public Map<String, Object> release(List<Long> classIds) {
        if (classIds == null || classIds.isEmpty()) throw new IllegalArgumentException("请选择班级");
        List<Long> ids = jdbc.queryForList("SELECT id FROM users WHERE role = 'STUDENT' AND graduated AND class_id IN (:k)", p("k", classIds), Long.class);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("students", ids.size());
        if (ids.isEmpty()) {
            out.put("records", 0);
            out.put("files", 0);
            out.put("bytes", 0);
            return out;
        }
        MapSqlParameterSource s = p("s", ids);
        List<Map<String, Object>> files = new ArrayList<>();
        files.addAll(jdbc.queryForList("SELECT file_path, file_size FROM submission WHERE student_id IN (:s) AND NOT essence", s));
        files.addAll(jdbc.queryForList("SELECT file_path, file_size FROM op_practice WHERE student_id IN (:s)", s));
        files.addAll(jdbc.queryForList("SELECT file_path, file_size FROM disk_file WHERE owner_id IN (:s)", s));
        int records = 0;
        records += jdbc.update("DELETE FROM submission WHERE student_id IN (:s) AND NOT essence", s);
        records += jdbc.update("DELETE FROM op_practice WHERE student_id IN (:s)", s);
        records += jdbc.update("DELETE FROM disk_file WHERE owner_id IN (:s)", s);
        jdbc.update("UPDATE help_request SET helper_id = NULL WHERE helper_id IN (:s)", s);
        for (String[] t : LEARNING_TABLES) records += jdbc.update("DELETE FROM " + t[0] + " WHERE " + t[1] + " IN (:s)", s);
        long bytes = 0;
        for (Map<String, Object> f : files) if (f.get("file_size") instanceof Number) bytes += ((Number) f.get("file_size")).longValue();
        // 数据库事务提交后再删文件，避免回滚后记录指向已删除的文件
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (Map<String, Object> f : files) storage.delete((String) f.get("file_path"));
                    }
                });
        out.put("records", records);
        out.put("files", files.size());
        out.put("bytes", bytes);
        return out;
    }
}
