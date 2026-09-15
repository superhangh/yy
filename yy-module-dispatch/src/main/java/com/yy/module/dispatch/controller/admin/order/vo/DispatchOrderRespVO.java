package com.yy.module.dispatch.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 派单订单 Response VO")
@Data
public class DispatchOrderRespVO {

    @Schema(description = "订单编号")
    private Long id;
    @Schema(description = "订单号")
    private String no;
    @Schema(description = "发单商家编号")
    private Long merchantId;
    @Schema(description = "接单用户编号")
    private Long userId;
    @Schema(description = "订单状态")
    private Integer status;
    @Schema(description = "结算状态")
    private Integer payStatus;
    @Schema(description = "服务标题")
    private String title;
    @Schema(description = "服务地址")
    private String address;
    @Schema(description = "酬劳金额（分）")
    private Integer amount;
    @Schema(description = "接单截止时间")
    private LocalDateTime deadline;
    @Schema(description = "接单时间")
    private LocalDateTime acceptTime;
    @Schema(description = "开始服务时间")
    private LocalDateTime startTime;
    @Schema(description = "完成时间")
    private LocalDateTime finishTime;
    @Schema(description = "取消原因")
    private String cancelReason;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
