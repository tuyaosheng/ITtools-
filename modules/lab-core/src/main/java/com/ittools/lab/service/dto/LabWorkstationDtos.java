package com.ittools.lab.service.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Java 8: no record - plain classes. View用bean getter(供Jackson序列化)，
 * Command用@JsonCreator全参构造(供Jackson反序列化)，做法与 KlassDtos/SchoolYearDtos 一致。
 */
public class LabWorkstationDtos {

    public static class LabWorkstationView {
        private final Long id;
        private final String pcName;
        private final String ipAddress;

        public LabWorkstationView(Long id, String pcName, String ipAddress) {
            this.id = id;
            this.pcName = pcName;
            this.ipAddress = ipAddress;
        }

        public Long getId() { return id; }
        public String getPcName() { return pcName; }
        public String getIpAddress() { return ipAddress; }
    }

    public static class LabWorkstationCommand {
        private final String pcName;
        private final String ipAddress;

        @JsonCreator
        public LabWorkstationCommand(
                @JsonProperty("pcName") String pcName,
                @JsonProperty("ipAddress") String ipAddress) {
            this.pcName = pcName;
            this.ipAddress = ipAddress;
        }

        public String pcName() { return pcName; }
        public String ipAddress() { return ipAddress; }
    }
}
