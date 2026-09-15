package com.yy.module.dispatch.controller.admin.settlement.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 结算记录 Response VO")
@Data
public class DispatchSettlementRespVO {
    private Long id;
    private Long orderId;
    private Long merchantId;
    private Long userId;
    private Integer amount;
    private Integer status;
    private LocalDateTime settleTime;
    private LocalDateTime refundTime;
    private LocalDateTime createTime;
}
