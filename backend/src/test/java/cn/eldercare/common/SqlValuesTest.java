package cn.eldercare.common;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlValuesTest {
    @Test
    void acceptsBothJdbcDateTimeRepresentations() {
        LocalDateTime expected = LocalDateTime.of(2026, 10, 19, 12, 30, 15);
        assertEquals(expected, SqlValues.localDateTime(expected));
        assertEquals(expected, SqlValues.localDateTime(Timestamp.valueOf(expected)));
    }


    @Test
    void acceptsBothJdbcBooleanRepresentations() {
        assertEquals(true, SqlValues.booleanValue(true));
        assertEquals(true, SqlValues.booleanValue(1));
        assertEquals(false, SqlValues.booleanValue(0));
    }
}
