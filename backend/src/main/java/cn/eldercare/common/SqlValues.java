package cn.eldercare.common;

import java.sql.Timestamp;
import java.time.LocalDateTime;

public final class SqlValues {
    private SqlValues() { }

    public static LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        throw new IllegalArgumentException("数据库时间值类型无效");
    }

    public static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        throw new IllegalArgumentException("数据库布尔值类型无效");
    }
}
