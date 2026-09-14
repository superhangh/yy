package com.yy.module.dispatch.service.merchant;

import com.yy.framework.common.exception.ServiceException;
import com.yy.framework.test.core.ut.BaseDbUnitTest;
import com.yy.module.dispatch.controller.admin.merchant.vo.DispatchMerchantSaveReqVO;
import com.yy.module.dispatch.dal.dataobject.merchant.DispatchMerchantDO;
import com.yy.module.dispatch.dal.mysql.merchant.DispatchMerchantMapper;
import com.yy.module.dispatch.enums.merchant.DispatchMerchantStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static com.yy.module.dispatch.enums.ErrorCodeConstants.MERCHANT_APPLY_EXISTS;
import static org.junit.jupiter.api.Assertions.*;

@Import(DispatchMerchantServiceImpl.class)
public class DispatchMerchantServiceTest extends BaseDbUnitTest {

    @Resource
    private DispatchMerchantService merchantService;
    @Resource
    private DispatchMerchantMapper merchantMapper;

    private DispatchMerchantSaveReqVO buildReqVO() {
        DispatchMerchantSaveReqVO vo = new DispatchMerchantSaveReqVO();
        vo.setName("张记跑腿");
        vo.setContactName("张三");
        vo.setContactMobile("13800138000");
        return vo;
    }

    @Test
    public void testApplyMerchant_success() {
        Long id = merchantService.applyMerchant(1001L, buildReqVO());
        assertNotNull(id);
        DispatchMerchantDO merchant = merchantMapper.selectById(id);
        assertEquals(1001L, merchant.getMemberUserId());
        assertEquals(DispatchMerchantStatusEnum.PENDING.getStatus(), merchant.getStatus());
    }

    @Test
    public void testApplyMerchant_duplicate() {
        merchantService.applyMerchant(1001L, buildReqVO());
        ServiceException ex = assertThrows(ServiceException.class,
                () -> merchantService.applyMerchant(1001L, buildReqVO()));
        assertEquals(MERCHANT_APPLY_EXISTS.getCode(), ex.getCode());
    }

    @Test
    public void testAuditMerchant_enable() {
        Long id = merchantService.applyMerchant(1001L, buildReqVO());
        merchantService.auditMerchant(id, DispatchMerchantStatusEnum.ENABLED.getStatus(), "ok");
        assertEquals(DispatchMerchantStatusEnum.ENABLED.getStatus(),
                merchantMapper.selectById(id).getStatus());
    }
}
