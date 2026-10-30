package cn.eldercare.family;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 授权列名枚举：与数据库字段一一对应，且反查不会返回未知值。 */
class PermissionColumnTest {

    @Test
    void everyColumnIsUniqueAndPrefixed() {
        long distinct = Arrays.stream(PermissionColumn.values()).map(PermissionColumn::column).distinct().count();
        assertEquals(PermissionColumn.values().length, distinct);
        assertTrue(Arrays.stream(PermissionColumn.values()).allMatch(column -> column.column().startsWith("can_")));
    }

    @Test
    void knownColumnsRoundTrip() {
        assertEquals(PermissionColumn.BOOK, PermissionColumn.of("can_book"));
        assertEquals(PermissionColumn.MANAGE_REMINDER, PermissionColumn.of(" can_manage_reminder "));
        assertEquals("can_confirm_fee", PermissionColumn.CONFIRM_FEE.column());
    }

    @Test
    void unknownColumnReturnsNullInsteadOfGuessing() {
        assertNull(PermissionColumn.of("can_everything"));
        assertNull(PermissionColumn.of(null));
    }

    @Test
    void handoverCandidateQueryUsesWhitelistedPermissionColumn() {
        String sql = FamilyCoordinationRepository.handoverCandidateSql(PermissionColumn.CONFIRM);
        assertTrue(sql.contains("g.can_confirm=1"));
        assertTrue(!sql.contains("g.CONFIRM"));
    }
}
