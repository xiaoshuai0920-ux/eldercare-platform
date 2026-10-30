package cn.eldercare.common;

import java.util.List;

/**
 * 列表统一响应结构：{items, page, size, total}。
 * total 是该筛选条件下的总条数，供前端渲染页码与"共 N 条"。
 */
public record PageResult<T>(List<T> items, int page, int size, long total) {
    public static <T> PageResult<T> of(List<T> items, PageQuery query, long total) {
        return new PageResult<>(items, query.page(), query.size(), total);
    }
}
