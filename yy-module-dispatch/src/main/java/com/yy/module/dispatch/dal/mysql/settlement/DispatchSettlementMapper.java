package com.yy.module.dispatch.dal.mysql.settlement;

import com.yy.framework.common.pojo.PageResult;
import com.yy.framework.mybatis.core.mapper.BaseMapperX;
import com.yy.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.yy.module.dispatch.controller.admin.settlement.vo.DispatchSettlementPageReqVO;
import com.yy.module.dispatch.dal.dataobject.settlement.DispatchSettlementDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DispatchSettlementMapper extends BaseMapperX<DispatchSettlementDO> {

    default DispatchSettlementDO selectByOrderId(Long orderId) {
        return selectOne(DispatchSettlementDO::getOrderId, orderId);
    }

    default PageResult<DispatchSettlementDO> selectPage(DispatchSettlementPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DispatchSettlementDO>()
                .eqIfPresent(DispatchSettlementDO::getMerchantId, reqVO.getMerchantId())
                .eqIfPresent(DispatchSettlementDO::getUserId, reqVO.getUserId())
                .eqIfPresent(DispatchSettlementDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(DispatchSettlementDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(DispatchSettlementDO::getId));
    }
}
