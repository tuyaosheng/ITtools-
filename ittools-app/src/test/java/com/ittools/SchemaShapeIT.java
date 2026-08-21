package com.ittools;

import com.ittools.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class SchemaShapeIT extends AbstractPostgresIT {
    @Autowired JdbcTemplate jdbc;

    @Test
    void coreTablesExist() {
        for (String t : new String[]{"school_year","klass","users","permission","role_permission","module_permission"}) {
            Integer n = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = ?", Integer.class, t);
            assertThat(n).as(t).isEqualTo(1);
        }
    }

    @Test
    void seedAdminExists() {
        Integer n = jdbc.queryForObject(
            "select count(*) from users where login_name = 'admin' and role = 'ADMIN'", Integer.class);
        assertThat(n).isEqualTo(1);
    }

    @Test
    void roleCheckConstraintRejectsBadRole() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            jdbc.update("insert into users(role,name,password_hash) values('WIZARD','x','y')"))
            .isInstanceOf(Exception.class);
    }
}
