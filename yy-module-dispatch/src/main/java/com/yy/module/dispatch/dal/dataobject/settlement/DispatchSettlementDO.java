package com.yy.module.dispatch.dal.dataobject.settlement;

import com.yy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("dispatch_settlement")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchSettlementDO extends BaseDO {

    @TableId
    private Long id;
    private Long orderId;
    private Long merchantId;
    private Long userId;
    /** 结算金额（分） */
    private Integer amount;
    /** 状态，见 DispatchSettlementStatusEnum */
    private Integer status;
    private LocalDateTime settleTime;
    private LocalDateTime refundTime;
    private String remark;
}
