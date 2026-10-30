package cn.eldercare.catalog;

import java.math.BigDecimal;
import java.time.LocalTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 服务项目的新建/更新入参。控制器负责校验，仓储只负责落库。 */
public record ItemRequest(@NotBlank String category, @NotBlank @Size(max = 100) String name,
                          @NotBlank @Size(max = 1000) String description, @NotBlank String pricingModel,
                          @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
                          @Min(1) @Max(1440) int minMinutes, @Min(1) @Max(1440) int maxMinutes,
                          @NotNull LocalTime availableFrom, @NotNull LocalTime availableTo,
                          @Min(0) int advanceMinutes, @NotBlank @Size(max = 500) String ruleText) { }
