package cn.eldercare.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 服务区域的新建入参。 */
public record AreaRequest(@NotBlank String level, Long parentId, @NotBlank @Size(max = 80) String name) { }
