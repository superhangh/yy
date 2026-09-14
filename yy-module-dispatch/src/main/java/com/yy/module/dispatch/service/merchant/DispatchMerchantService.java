package com.yy.module.dispatch.service.merchant;

import com.yy.framework.common.pojo.PageResult;
import com.yy.module.dispatch.controller.admin.merchant.vo.DispatchMerchantPageReqVO;
import com.yy.module.dispatch.controller.admin.merchant.vo.DispatchMerchantSaveReqVO;
import com.yy.module.dispatch.dal.dataobject.merchant.DispatchMerchantDO;

public interface DispatchMerchantService {

    /** 会员提交入驻申请（status=待审核） */
    Long applyMerchant(Long memberUserId, DispatchMerchantSaveReqVO reqVO);

    /** 管理端审核：status 1 通过 / 2 禁用 */
    void auditMerchant(Long id, Integer status, String remark);

    /** 按会员编号查询商家 */
    DispatchMerchantDO getMerchantByMemberUserId(Long memberUserId);

    DispatchMerchantDO getMerchant(Long id);

    PageResult<DispatchMerchantDO> getMerchantPage(DispatchMerchantPageReqVO reqVO);

}
