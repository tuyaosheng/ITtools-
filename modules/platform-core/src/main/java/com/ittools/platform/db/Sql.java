package com.ittools.platform.db;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * 参数化 SQL 的参数构造助手：Sql.p("id", 1, "name", "x")。
 * 子项目 1-7 的报表/统计类查询使用 NamedParameterJdbcTemplate + 绑定参数（绝不拼接 SQL）。
 */
public final class Sql {
    private Sql() {}

    public static MapSqlParameterSource p(Object... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("参数必须成对出现");
        }
        MapSqlParameterSource s = new MapSqlParameterSource();
        for (int i = 0; i < kv.length; i += 2) {
            s.addValue(String.valueOf(kv[i]), kv[i + 1]);
        }
        return s;
    }
}
