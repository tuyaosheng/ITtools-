package com.ittools.platform.service.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The task brief specifies SchoolYearView/SchoolYearCommand as Java 17
 * records, but this project targets Java 8 (no records). The two DTOs play
 * opposite Jackson roles, so they need different translations (same split
 * ApiResponse/LoginRequest already established):
 *
 *  - SchoolYearView is RETURNED (serialized to JSON) - Jackson's default
 *    serializer introspects standard bean getters, not record-style fluent
 *    accessors, so it's a plain immutable class with bean getters
 *    (getId()/getYearCode()/getLabel()/isActive()).
 *  - SchoolYearCommand is a @RequestBody (DESERIALIZED from JSON) - it needs
 *    @JsonCreator/@JsonProperty on an all-args constructor, exactly like
 *    LoginRequest (Task 5/6). SchoolYearService (per the brief) calls
 *    c.yearCode()/c.label()/c.active(), so it also carries fluent accessors
 *    named to match, in addition to the Jackson wiring.
 */
public class SchoolYearDtos {

    public static class SchoolYearView {
        private final Long id;
        private final String yearCode;
        private final String label;
        private final boolean active;

        public SchoolYearView(Long id, String yearCode, String label, boolean active) {
            this.id = id;
            this.yearCode = yearCode;
            this.label = label;
            this.active = active;
        }

        public Long getId() { return id; }
        public String getYearCode() { return yearCode; }
        public String getLabel() { return label; }
        public boolean isActive() { return active; }
    }

    public static class SchoolYearCommand {
        private final String yearCode;
        private final String label;
        private final boolean active;

        @JsonCreator
        public SchoolYearCommand(
                @JsonProperty("yearCode") String yearCode,
                @JsonProperty("label") String label,
                @JsonProperty("active") boolean active) {
            this.yearCode = yearCode;
            this.label = label;
            this.active = active;
        }

        public String yearCode() { return yearCode; }
        public String label() { return label; }
        public boolean active() { return active; }
    }
}
