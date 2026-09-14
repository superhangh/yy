package com.yy.module.dispatch.controller.admin.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 商家审核 Request VO")
@Data
public class DispatchMerchantAuditReqVO {

    @Schema(description = "商家编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2001")
    @NotNull(message = "商家编号不能为空")
    private Long id;

    @Schema(description = "审核状态：1 通过 2 禁用", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "审核状态不能为空")
    private Integer status;

    @Schema(description = "审核备注", example = "资料齐全")
    private String remark;
}
