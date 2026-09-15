package com.yy.module.dispatch.dal.dataobject.wallet;

import com.yy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("dispatch_merchant_wallet")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchMerchantWalletDO extends BaseDO {

    @TableId
    private Long id;
    private Long merchantId;
    /** 余额（分） */
    private Integer balance;
    /** 累计充值（分） */
    private Integer totalRecharge;
    /** 累计消费（分） */
    private Integer totalConsume;
}
