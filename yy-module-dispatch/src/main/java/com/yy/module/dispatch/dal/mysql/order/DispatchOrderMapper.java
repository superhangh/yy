package com.yy.module.dispatch.dal.mysql.order;

import com.yy.framework.common.pojo.PageResult;
import com.yy.framework.mybatis.core.mapper.BaseMapperX;
import com.yy.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderPageReqVO;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import com.yy.module.dispatch.enums.order.DispatchOrderStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface DispatchOrderMapper extends BaseMapperX<DispatchOrderDO> {

    /**
     * 抢单：条件更新，原子性由 WHERE 保证
     *
     * @return 影响行数；1 表示抢单成功，0 表示已被抢走/状态不对
     */
    default int updateAccept(Long id, Long userId) {
        return update(null, new LambdaUpdateWrapper<DispatchOrderDO>()
                .set(DispatchOrderDO::getUserId, userId)
                .set(DispatchOrderDO::getStatus, DispatchOrderStatusEnum.ACCEPTED.getStatus())
                .set(DispatchOrderDO::getAcceptTime, LocalDateTime.now())
                .eq(DispatchOrderDO::getId, id)
                .eq(DispatchOrderDO::getStatus, DispatchOrderStatusEnum.PENDING.getStatus())
                .isNull(DispatchOrderDO::getUserId));
    }

    default PageResult<DispatchOrderDO> selectPage(DispatchOrderPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DispatchOrderDO>()
                .eqIfPresent(DispatchOrderDO::getMerchantId, reqVO.getMerchantId())
                .eqIfPresent(DispatchOrderDO::getUserId, reqVO.getUserId())
                .eqIfPresent(DispatchOrderDO::getStatus, reqVO.getStatus())
                .likeIfPresent(DispatchOrderDO::getTitle, reqVO.getTitle())
                .betweenIfPresent(DispatchOrderDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(DispatchOrderDO::getId));
    }
}
