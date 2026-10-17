-- 批量导入建的班级排序都是 0，班级列表看起来是乱序。
-- 对“整个年级的班级排序都还是 0”的年级，按班名里的数字（1班、2班……10班）重新编号；
-- 已经手工排过序的年级不动。
UPDATE klass k
SET display_order = r.rn
FROM (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY school_year_id
                              ORDER BY NULLIF(substring(name FROM '[0-9]+'), '')::numeric NULLS LAST, name) AS rn
    FROM klass
    WHERE school_year_id IN (SELECT school_year_id FROM klass GROUP BY school_year_id HAVING MAX(display_order) = 0)
) r
WHERE k.id = r.id;
