package com.ittools.platform.service.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The task brief specifies PermissionView/ModulePermissionCommand as Java 17
 * records, but this project targets Java 8 (no records). The two DTOs play
 * opposite Jackson roles, so they need different translations (same split
 * ApiResponse/SchoolYearDtos already established):
 *
 *  - PermissionView is RETURNED (serialized to JSON) - Jackson's default
 *    serializer introspects standard bean getters, not record-style fluent
 *    accessors, so it's a plain immutable class with bean getters
 *    (getId()/getCode()/getName()/getModule()).
 *  - ModulePermissionCommand is a @RequestBody (DESERIALIZED from JSON) - it
 *    needs @JsonCreator/@JsonProperty on an all-args constructor, exactly
 *    like SchoolYearCommand. PermissionService (per the brief) calls
 *    c.scope()/c.scopeRefId()/c.permissionId()/c.enabled(), so it also
 *    carries fluent accessors named to match, in addition to the Jackson
 *    wiring.
 */
public class PermissionDtos {

    public static class PermissionView {
        private final Long id;
        private final String code;
        private final String name;
        private final String module;

        public PermissionView(Long id, String code, String name, String module) {
            this.id = id;
            this.code = code;
            this.name = name;
            this.module = module;
        }

        public Long getId() { return id; }
        public String getCode() { return code; }
        public String getName() { return name; }
        public String getModule() { return module; }
    }

    public static class ModulePermissionCommand {
        private final String scope;
        private final Long scopeRefId;
        private final Long permissionId;
        private final boolean enabled;

        @JsonCreator
        public ModulePermissionCommand(
                @JsonProperty("scope") String scope,
                @JsonProperty("scopeRefId") Long scopeRefId,
                @JsonProperty("permissionId") Long permissionId,
                @JsonProperty("enabled") boolean enabled) {
            this.scope = scope;
            this.scopeRefId = scopeRefId;
            this.permissionId = permissionId;
            this.enabled = enabled;
        }

        public String scope() { return scope; }
        public Long scopeRefId() { return scopeRefId; }
        public Long permissionId() { return permissionId; }
        public boolean enabled() { return enabled; }
    }
}
