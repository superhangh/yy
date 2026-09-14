package com.yy.module.dispatch.controller.admin.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 商家 Response VO")
@Data
public class DispatchMerchantRespVO {

    @Schema(description = "编号")
    private Long id;
    @Schema(description = "会员用户编号")
    private Long memberUserId;
    @Schema(description = "商家名称")
    private String name;
    @Schema(description = "商家 Logo")
    private String logo;
    @Schema(description = "联系人")
    private String contactName;
    @Schema(description = "联系电话")
    private String contactMobile;
    @Schema(description = "状态")
    private Integer status;
    @Schema(description = "审核时间")
    private LocalDateTime auditTime;
    @Schema(description = "审核备注")
    private String auditRemark;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
