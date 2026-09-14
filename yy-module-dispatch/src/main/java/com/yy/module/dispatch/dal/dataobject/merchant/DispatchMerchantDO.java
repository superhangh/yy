package com.yy.module.dispatch.dal.dataobject.merchant;

import com.yy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 派单商家 DO
 */
@TableName("dispatch_merchant")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchMerchantDO extends BaseDO {

    @TableId
    private Long id;
    /** 会员用户编号 */
    private Long memberUserId;
    /** 商家名称 */
    private String name;
    /** 商家 Logo */
    private String logo;
    /** 联系人 */
    private String contactName;
    /** 联系电话 */
    private String contactMobile;
    /** 状态，见 DispatchMerchantStatusEnum */
    private Integer status;
    /** 审核时间 */
    private LocalDateTime auditTime;
    /** 审核备注 */
    private String auditRemark;

}
