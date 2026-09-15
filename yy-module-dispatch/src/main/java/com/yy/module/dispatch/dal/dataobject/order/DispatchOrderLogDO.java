package com.yy.module.dispatch.dal.dataobject.order;

import com.yy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 派单订单操作日志 DO
 */
@TableName("dispatch_order_log")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchOrderLogDO extends BaseDO {

    @TableId
    private Long id;
    /** 订单编号 */
    private Long orderId;
    /** 操作类型，见 DispatchOrderOperateTypeEnum */
    private Integer operateType;
    /** 操作内容 */
    private String content;

}
