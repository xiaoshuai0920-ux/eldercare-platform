package cn.eldercare.common;

import org.springframework.http.HttpStatus;

/**
 * 分页请求参数。所有列表接口共用同一套约束：
 * page 从 1 开始，size 限定 1–100，越界直接按 400 拒绝，避免静默截断。
 */
public record PageQuery(int page, int size) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static PageQuery of(int page, int size) {
        if (size < 1 || size > MAX_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_SIZE", "每页数量须为 1–100");
        }
        if (page < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "页码须从 1 开始");
        }
        return new PageQuery(page, size);
    }

    public int offset() {
        return (page - 1) * size;
    }
}
