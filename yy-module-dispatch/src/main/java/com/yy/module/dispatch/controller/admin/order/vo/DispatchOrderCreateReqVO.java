package com.yy.module.dispatch.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Schema(description = "商家 - 发单 Request VO")
@Data
public class DispatchOrderCreateReqVO {

    @Schema(description = "服务标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "帮买一杯咖啡")
    @NotEmpty(message = "服务标题不能为空")
    private String title;

    @Schema(description = "服务描述")
    private String description;

    @Schema(description = "服务图片（逗号分隔）")
    private String images;

    @Schema(description = "服务地址", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "服务地址不能为空")
    private String address;

    @Schema(description = "联系人")
    private String contactName;

    @Schema(description = "联系电话")
    private String contactMobile;

    @Schema(description = "酬劳金额（分）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000")
    @NotNull(message = "酬劳金额不能为空")
    private Integer amount;

    @Schema(description = "接单截止时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deadline;
}
