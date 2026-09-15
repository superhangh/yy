package com.yy.module.dispatch.service.order;

import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderLogDO;
import com.yy.module.dispatch.dal.mysql.order.DispatchOrderLogMapper;
import com.yy.module.dispatch.enums.order.DispatchOrderOperateTypeEnum;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.List;

@Service
@Validated
public class DispatchOrderLogServiceImpl implements DispatchOrderLogService {

    @Resource
    private DispatchOrderLogMapper orderLogMapper;

    @Override
    public void createLog(Long orderId, DispatchOrderOperateTypeEnum operateType, String content) {
        DispatchOrderLogDO log = DispatchOrderLogDO.builder()
                .orderId(orderId)
                .operateType(operateType.getType())
                .content(content)
                .build();
        orderLogMapper.insert(log);
    }

    @Override
    public List<DispatchOrderLogDO> getLogList(Long orderId) {
        return orderLogMapper.selectListByOrderId(orderId);
    }
}
