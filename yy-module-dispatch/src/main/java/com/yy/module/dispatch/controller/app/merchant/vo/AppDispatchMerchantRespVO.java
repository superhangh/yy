package com.yy.module.dispatch.controller.app.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "小程序 - 商家信息 Response VO")
@Data
public class AppDispatchMerchantRespVO {

    @Schema(description = "商家编号")
    private Long id;
    @Schema(description = "商家名称")
    private String name;
    @Schema(description = "商家 Logo")
    private String logo;
    @Schema(description = "联系人")
    private String contactName;
    @Schema(description = "联系电话")
    private String contactMobile;
    @Schema(description = "状态：0待审核 1正常 2禁用")
    private Integer status;
}
