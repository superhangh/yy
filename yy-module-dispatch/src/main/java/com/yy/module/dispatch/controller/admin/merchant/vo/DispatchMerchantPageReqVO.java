package com.yy.module.dispatch.controller.admin.merchant.vo;

import com.yy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 商家分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DispatchMerchantPageReqVO extends PageParam {

    @Schema(description = "商家名称", example = "张记")
    private String name;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "会员用户编号", example = "1024")
    private Long memberUserId;
}
