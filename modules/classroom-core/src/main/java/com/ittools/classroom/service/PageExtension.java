package com.ittools.classroom.service;

import java.util.Map;

/**
 * 课程页面的扩展设置（由各子项目提供，如作品提交的评分规则、测验的考试方式）。
 * 复制课程、导出/导入课程包时由课程模块统一调用，使页面设置随课程一起迁移。
 */
public interface PageExtension {
    /** 课程包中的键名 */
    String key();

    /** 把 fromPage 的设置复制到 toPage */
    void copy(long fromPage, long toPage);

    /** 导出为可 JSON 序列化的数据；没有设置返回 null */
    Map<String, Object> export(long pageId);

    /** 导入到新页面（数据来自课程包，需自行校验） */
    void importInto(long pageId, long teacherId, Map<String, Object> data);
}
