package com.ittools.platform.service.dto;

/**
 * The task brief specifies YearOption/ClassOption/StudentOption as Java 17
 * records, but this project targets Java 8 (no records) AND these are
 * Jackson-serialized to JSON in the /api/public/* responses. Jackson's
 * default serializer introspects standard bean getters (getId()/getName()/
 * etc.), not record-style fluent accessors - so these are plain immutable
 * classes with bean getters and an all-args constructor, matching the call
 * sites in LookupService (constructor argument order must match).
 */
public class LookupDtos {

    public static class YearOption {
        private final Long id;
        private final String yearCode;
        private final String label;

        public YearOption(Long id, String yearCode, String label) {
            this.id = id; this.yearCode = yearCode; this.label = label;
        }

        public Long getId() { return id; }
        public String getYearCode() { return yearCode; }
        public String getLabel() { return label; }
    }

    public static class ClassOption {
        private final Long id;
        private final String name;

        public ClassOption(Long id, String name) {
            this.id = id; this.name = name;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
    }

    public static class StudentOption {
        private final Long id;
        private final String name;

        public StudentOption(Long id, String name) {
            this.id = id; this.name = name;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
    }
}
