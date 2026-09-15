package com.yy.module.dispatch.dal.dataobject.order;

import com.yy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 派单订单 DO
 */
@TableName("dispatch_order")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchOrderDO extends BaseDO {

    @TableId
    private Long id;
    /** 订单号 */
    private String no;
    /** 发单商家编号 */
    private Long merchantId;
    /** 接单用户编号（抢单前为 null） */
    private Long userId;
    /** 订单状态，见 DispatchOrderStatusEnum */
    private Integer status;
    /** 结算状态，见 DispatchOrderPayStatusEnum */
    private Integer payStatus;
    /** 服务标题 */
    private String title;
    /** 服务描述 */
    private String description;
    /** 服务图片（逗号分隔） */
    private String images;
    /** 服务地址 */
    private String address;
    /** 联系人 */
    private String contactName;
    /** 联系电话 */
    private String contactMobile;
    /** 酬劳金额（分） */
    private Integer amount;
    /** 接单截止时间 */
    private LocalDateTime deadline;
    /** 接单时间 */
    private LocalDateTime acceptTime;
    /** 开始服务时间 */
    private LocalDateTime startTime;
    /** 完成时间 */
    private LocalDateTime finishTime;
    /** 取消原因 */
    private String cancelReason;

}
