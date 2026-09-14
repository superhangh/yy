package com.yy.module.dispatch.controller.app.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Schema(description = "小程序 - 商家入驻申请 Request VO")
@Data
public class AppDispatchMerchantApplyReqVO {

    @Schema(description = "商家名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "张记跑腿")
    @NotEmpty(message = "商家名称不能为空")
    private String name;

    @Schema(description = "商家 Logo")
    private String logo;

    @Schema(description = "联系人", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "联系人不能为空")
    private String contactName;

    @Schema(description = "联系电话", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "联系电话不能为空")
    private String contactMobile;
}
