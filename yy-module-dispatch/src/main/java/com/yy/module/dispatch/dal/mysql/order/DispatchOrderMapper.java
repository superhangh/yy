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

    /**
     * 查询指定状态且 deadline 早于给定时间的订单（用于超时未接扫描）
     */
    default java.util.List<DispatchOrderDO> selectListByStatusAndDeadlineLt(Integer status, LocalDateTime deadline) {
        return selectList(new LambdaQueryWrapperX<DispatchOrderDO>()
                .eq(DispatchOrderDO::getStatus, status)
                .isNotNull(DispatchOrderDO::getDeadline)
                .lt(DispatchOrderDO::getDeadline, deadline));
    }

    /**
     * 开始服务：ACCEPTED -> SERVING，条件更新（WHERE status=ACCEPTED）
     *
     * @return 影响行数；0 表示状态已变（并发）
     */
    default int updateStart(Long id) {
        return update(null, new LambdaUpdateWrapper<DispatchOrderDO>()
                .set(DispatchOrderDO::getStatus, DispatchOrderStatusEnum.SERVING.getStatus())
                .set(DispatchOrderDO::getStartTime, LocalDateTime.now())
                .eq(DispatchOrderDO::getId, id)
                .eq(DispatchOrderDO::getStatus, DispatchOrderStatusEnum.ACCEPTED.getStatus()));
    }

    /**
     * 完成服务：SERVING -> COMPLETED，条件更新（WHERE status=SERVING）
     *
     * @return 影响行数；0 表示状态已变（并发）
     */
    default int updateFinish(Long id) {
        return update(null, new LambdaUpdateWrapper<DispatchOrderDO>()
                .set(DispatchOrderDO::getStatus, DispatchOrderStatusEnum.COMPLETED.getStatus())
                .set(DispatchOrderDO::getFinishTime, LocalDateTime.now())
                .eq(DispatchOrderDO::getId, id)
                .eq(DispatchOrderDO::getStatus, DispatchOrderStatusEnum.SERVING.getStatus()));
    }

    /**
     * 取消订单：仅当状态在 allowedStatuses 内才取消，条件更新（避免覆盖并发抢单/完成）
     *
     * @return 影响行数；0 表示状态不允许取消（或已被并发变更）
     */
    default int updateCancel(Long id, String reason, Integer... allowedStatuses) {
        return update(null, new LambdaUpdateWrapper<DispatchOrderDO>()
                .set(DispatchOrderDO::getStatus, DispatchOrderStatusEnum.CANCELED.getStatus())
                .set(DispatchOrderDO::getCancelReason, reason)
                .eq(DispatchOrderDO::getId, id)
                .in(DispatchOrderDO::getStatus, (Object[]) allowedStatuses));
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
