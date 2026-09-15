package com.yy.module.dispatch.dal.mysql.order;

import com.yy.framework.mybatis.core.mapper.BaseMapperX;
import com.yy.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DispatchOrderLogMapper extends BaseMapperX<DispatchOrderLogDO> {

    default List<DispatchOrderLogDO> selectListByOrderId(Long orderId) {
        return selectList(new LambdaQueryWrapperX<DispatchOrderLogDO>()
                .eq(DispatchOrderLogDO::getOrderId, orderId)
                .orderByAsc(DispatchOrderLogDO::getId));
    }
}
