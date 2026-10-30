package cn.eldercare.family;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 家庭授权列名的唯一来源。
 *
 * <p>权限校验需要把列名拼进 SQL（`g.&lt;column&gt;=1`），历史上是字符串参数 + 运行时白名单。
 * 改成枚举后，白名单由编译器保证：任何不在枚举里的值都无法传入，新增权限必须同时改这里，
 * 避免将来从请求参数或配置读到列名造成注入面。</p>
 */
public enum PermissionColumn {
    VIEW_SERVICE("can_view_service"),
    BOOK("can_book"),
    MANAGE_REMINDER("can_manage_reminder"),
    CANCEL("can_cancel"),
    CONFIRM("can_confirm"),
    REVIEW("can_review"),
    CONFIRM_FEE("can_confirm_fee"),
    MANAGE_FAMILY("can_manage_family");

    private static final Map<String, PermissionColumn> BY_COLUMN = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(PermissionColumn::column, Function.identity()));

    private final String column;

    PermissionColumn(String column) {
        this.column = column;
    }

    /** 数据库字段名，仅用于拼接受控 SQL 片段 */
    public String column() {
        return column;
    }

    /** 由列名反查枚举；未知列名返回空，供迁移期兼容使用 */
    public static PermissionColumn of(String column) {
        return column == null ? null : BY_COLUMN.get(column.trim());
    }
}
