package com.ittools;

import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationIT extends AbstractPostgresIT {
    @Autowired JdbcTemplate jdbc;

    @Test
    void flywaySchemaHistoryExists() {
        Integer count = jdbc.queryForObject(
            "select count(*) from flyway_schema_history where success = true", Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }
}
