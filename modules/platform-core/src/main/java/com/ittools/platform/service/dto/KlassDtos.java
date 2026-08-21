package com.ittools.platform.service.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The task brief specifies KlassView/KlassCommand as Java 17 records, but
 * this project targets Java 8 (no records). Mirrors SchoolYearDtos (Task 8):
 *
 *  - KlassView is RETURNED (serialized to JSON) - Jackson's default
 *    serializer introspects standard bean getters, not record-style fluent
 *    accessors, so it's a plain immutable class with bean getters
 *    (getId()/getSchoolYearId()/getYearLabel()/getName()/getDisplayOrder()).
 *  - KlassCommand is a @RequestBody (DESERIALIZED from JSON) - it needs
 *    @JsonCreator/@JsonProperty on an all-args constructor, exactly like
 *    SchoolYearCommand. KlassService (per the brief) calls
 *    c.schoolYearId()/c.name()/c.displayOrder(), so it also carries fluent
 *    accessors named to match, in addition to the Jackson wiring.
 */
public class KlassDtos {

    public static class KlassView {
        private final Long id;
        private final Long schoolYearId;
        private final String yearLabel;
        private final String name;
        private final int displayOrder;

        public KlassView(Long id, Long schoolYearId, String yearLabel, String name, int displayOrder) {
            this.id = id;
            this.schoolYearId = schoolYearId;
            this.yearLabel = yearLabel;
            this.name = name;
            this.displayOrder = displayOrder;
        }

        public Long getId() { return id; }
        public Long getSchoolYearId() { return schoolYearId; }
        public String getYearLabel() { return yearLabel; }
        public String getName() { return name; }
        public int getDisplayOrder() { return displayOrder; }
    }

    public static class KlassCommand {
        private final Long schoolYearId;
        private final String name;
        private final int displayOrder;

        @JsonCreator
        public KlassCommand(
                @JsonProperty("schoolYearId") Long schoolYearId,
                @JsonProperty("name") String name,
                @JsonProperty("displayOrder") int displayOrder) {
            this.schoolYearId = schoolYearId;
            this.name = name;
            this.displayOrder = displayOrder;
        }

        public Long schoolYearId() { return schoolYearId; }
        public String name() { return name; }
        public int displayOrder() { return displayOrder; }
    }
}
