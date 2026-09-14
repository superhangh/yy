package com.yy.module.pay.api.refund;

import com.yy.framework.common.util.object.BeanUtils;
import com.yy.module.pay.api.refund.dto.PayRefundCreateReqDTO;
import com.yy.module.pay.api.refund.dto.PayRefundRespDTO;
import com.yy.module.pay.dal.dataobject.refund.PayRefundDO;
import com.yy.module.pay.service.refund.PayRefundService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * 退款单 API 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
public class PayRefundApiImpl implements PayRefundApi {

    @Resource
    private PayRefundService payRefundService;

    @Override
    public Long createRefund(PayRefundCreateReqDTO reqDTO) {
        return payRefundService.createRefund(reqDTO);
    }

    @Override
    public PayRefundRespDTO getRefund(Long id) {
        PayRefundDO refund = payRefundService.getRefund(id);
        return BeanUtils.toBean(refund, PayRefundRespDTO.class);
    }

}
