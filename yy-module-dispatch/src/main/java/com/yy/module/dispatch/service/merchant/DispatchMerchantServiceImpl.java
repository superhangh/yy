package com.yy.module.dispatch.service.merchant;

import com.yy.framework.common.pojo.PageResult;
import com.yy.module.dispatch.controller.admin.merchant.vo.DispatchMerchantPageReqVO;
import com.yy.module.dispatch.controller.admin.merchant.vo.DispatchMerchantSaveReqVO;
import com.yy.module.dispatch.dal.dataobject.merchant.DispatchMerchantDO;
import com.yy.module.dispatch.dal.mysql.merchant.DispatchMerchantMapper;
import com.yy.module.dispatch.enums.merchant.DispatchMerchantStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;

import static com.yy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.yy.module.dispatch.enums.ErrorCodeConstants.*;

@Service
@Validated
public class DispatchMerchantServiceImpl implements DispatchMerchantService {

    @Resource
    private DispatchMerchantMapper merchantMapper;

    @Override
    public Long applyMerchant(Long memberUserId, DispatchMerchantSaveReqVO reqVO) {
        // 一个会员仅可入驻一次
        if (merchantMapper.selectByMemberUserId(memberUserId) != null) {
            throw exception(MERCHANT_APPLY_EXISTS);
        }
        DispatchMerchantDO merchant = DispatchMerchantDO.builder()
                .memberUserId(memberUserId)
                .name(reqVO.getName())
                .logo(reqVO.getLogo())
                .contactName(reqVO.getContactName())
                .contactMobile(reqVO.getContactMobile())
                .status(DispatchMerchantStatusEnum.PENDING.getStatus())
                .build();
        merchantMapper.insert(merchant);
        return merchant.getId();
    }

    @Override
    public void auditMerchant(Long id, Integer status, String remark) {
        validateMerchantExists(id);
        if (!DispatchMerchantStatusEnum.ENABLED.getStatus().equals(status)
                && !DispatchMerchantStatusEnum.DISABLED.getStatus().equals(status)) {
            throw exception(MERCHANT_STATUS_ERROR);
        }
        DispatchMerchantDO update = DispatchMerchantDO.builder()
                .id(id).status(status).auditTime(LocalDateTime.now()).auditRemark(remark)
                .build();
        merchantMapper.updateById(update);
    }

    @Override
    public DispatchMerchantDO getMerchantByMemberUserId(Long memberUserId) {
        return merchantMapper.selectByMemberUserId(memberUserId);
    }

    @Override
    public DispatchMerchantDO getMerchant(Long id) {
        return merchantMapper.selectById(id);
    }

    @Override
    public PageResult<DispatchMerchantDO> getMerchantPage(DispatchMerchantPageReqVO reqVO) {
        return merchantMapper.selectPage(reqVO);
    }

    private DispatchMerchantDO validateMerchantExists(Long id) {
        DispatchMerchantDO merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw exception(MERCHANT_NOT_EXISTS);
        }
        return merchant;
    }
}
