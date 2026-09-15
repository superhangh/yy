package com.yy.module.dispatch.service.settlement;

import com.yy.framework.common.pojo.PageResult;
import com.yy.module.dispatch.controller.admin.settlement.vo.DispatchSettlementPageReqVO;
import com.yy.module.dispatch.dal.dataobject.settlement.DispatchSettlementDO;

public interface DispatchSettlementService {

    void settleOrder(Long orderId);

    void refundOrder(Long orderId);

    DispatchSettlementDO getSettlement(Long orderId);

    PageResult<DispatchSettlementDO> getSettlementPage(DispatchSettlementPageReqVO reqVO);
}
