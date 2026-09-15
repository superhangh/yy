package com.yy.module.dispatch.service.order;

import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderLogDO;
import com.yy.module.dispatch.enums.order.DispatchOrderOperateTypeEnum;

import java.util.List;

public interface DispatchOrderLogService {

    void createLog(Long orderId, DispatchOrderOperateTypeEnum operateType, String content);

    List<DispatchOrderLogDO> getLogList(Long orderId);

}
