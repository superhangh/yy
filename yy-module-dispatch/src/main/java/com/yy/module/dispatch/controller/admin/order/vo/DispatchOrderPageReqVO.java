package com.yy.module.dispatch.controller.admin.order.vo;

import com.yy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 派单订单分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DispatchOrderPageReqVO extends PageParam {

    @Schema(description = "商家编号", example = "2001")
    private Long merchantId;

    @Schema(description = "用户编号", example = "1001")
    private Long userId;

    @Schema(description = "订单状态", example = "0")
    private Integer status;

    @Schema(description = "服务标题", example = "跑腿")
    private String title;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime[] createTime;
}
