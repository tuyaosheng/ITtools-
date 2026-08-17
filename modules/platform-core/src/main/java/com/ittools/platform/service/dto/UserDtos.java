package com.ittools.platform.service.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The task brief specifies these as Java 17 records, but this project
 * targets Java 8 (no records). Mirrors KlassDtos/SchoolYearDtos (Tasks 8/9):
 *
 *  - UserView is RETURNED (serialized to JSON) - Jackson's default
 *    serializer introspects standard bean getters, not record-style fluent
 *    accessors, so it's a plain immutable class with bean getters
 *    (getId()/getRole()/getName()/getLoginName()/getStudentNo()/getXjh()/
 *    getClassId()/getClassName()/isGraduated()/isEnabled()).
 *  - CreateUserCommand/UpdateUserCommand/ResetPasswordCommand are
 *    @RequestBody (DESERIALIZED from JSON) - they need
 *    @JsonCreator/@JsonProperty on an all-args constructor, exactly like
 *    KlassCommand/SchoolYearCommand. UserService (per the brief) calls
 *    fluent accessors (role()/name()/loginName()/studentNo()/xjh()/classId()/
 *    enrollYearId()/password()), so each command class also carries fluent
 *    accessors named to match, in addition to the Jackson wiring.
 */
public class UserDtos {

    public static class UserView {
        private final Long id;
        private final String role;
        private final String name;
        private final String loginName;
        private final String studentNo;
        private final String xjh;
        private final Long classId;
        private final String className;
        private final boolean graduated;
        private final boolean enabled;

        public UserView(Long id, String role, String name, String loginName, String studentNo,
                         String xjh, Long classId, String className, boolean graduated, boolean enabled) {
            this.id = id;
            this.role = role;
            this.name = name;
            this.loginName = loginName;
            this.studentNo = studentNo;
            this.xjh = xjh;
            this.classId = classId;
            this.className = className;
            this.graduated = graduated;
            this.enabled = enabled;
        }

        public Long getId() { return id; }
        public String getRole() { return role; }
        public String getName() { return name; }
        public String getLoginName() { return loginName; }
        public String getStudentNo() { return studentNo; }
        public String getXjh() { return xjh; }
        public Long getClassId() { return classId; }
        public String getClassName() { return className; }
        public boolean isGraduated() { return graduated; }
        public boolean isEnabled() { return enabled; }
    }

    public static class CreateUserCommand {
        private final String role;
        private final String name;
        private final String loginName;
        private final String studentNo;
        private final String xjh;
        private final Long classId;
        private final Long enrollYearId;
        private final String password;

        @JsonCreator
        public CreateUserCommand(
                @JsonProperty("role") String role,
                @JsonProperty("name") String name,
                @JsonProperty("loginName") String loginName,
                @JsonProperty("studentNo") String studentNo,
                @JsonProperty("xjh") String xjh,
                @JsonProperty("classId") Long classId,
                @JsonProperty("enrollYearId") Long enrollYearId,
                @JsonProperty("password") String password) {
            this.role = role;
            this.name = name;
            this.loginName = loginName;
            this.studentNo = studentNo;
            this.xjh = xjh;
            this.classId = classId;
            this.enrollYearId = enrollYearId;
            this.password = password;
        }

        public String role() { return role; }
        public String name() { return name; }
        public String loginName() { return loginName; }
        public String studentNo() { return studentNo; }
        public String xjh() { return xjh; }
        public Long classId() { return classId; }
        public Long enrollYearId() { return enrollYearId; }
        public String password() { return password; }
    }

    /**
     * FULL-REPLACE semantics: {@link com.ittools.platform.service.UserService#update}
     * overwrites classId/enrollYearId unconditionally, so omitting
     * {@code classId} or {@code enrollYearId} in the request body CLEARS
     * (nulls out) the user's current class/enrollment rather than leaving it
     * unchanged. Callers (the frontend) must always send the complete
     * object on PUT, not a partial patch.
     */
    public static class UpdateUserCommand {
        private final String name;
        private final String loginName;
        private final String studentNo;
        private final String xjh;
        private final Long classId;
        private final Long enrollYearId;

        @JsonCreator
        public UpdateUserCommand(
                @JsonProperty("name") String name,
                @JsonProperty("loginName") String loginName,
                @JsonProperty("studentNo") String studentNo,
                @JsonProperty("xjh") String xjh,
                @JsonProperty("classId") Long classId,
                @JsonProperty("enrollYearId") Long enrollYearId) {
            this.name = name;
            this.loginName = loginName;
            this.studentNo = studentNo;
            this.xjh = xjh;
            this.classId = classId;
            this.enrollYearId = enrollYearId;
        }

        public String name() { return name; }
        public String loginName() { return loginName; }
        public String studentNo() { return studentNo; }
        public String xjh() { return xjh; }
        public Long classId() { return classId; }
        public Long enrollYearId() { return enrollYearId; }
    }

    public static class ResetPasswordCommand {
        private final String password;

        @JsonCreator
        public ResetPasswordCommand(@JsonProperty("password") String password) {
            this.password = password;
        }

        public String password() { return password; }
    }
}
