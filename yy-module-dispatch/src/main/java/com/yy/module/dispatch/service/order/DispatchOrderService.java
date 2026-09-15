package com.yy.module.dispatch.service.order;

import com.yy.framework.common.pojo.PageResult;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderCreateReqVO;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderPageReqVO;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;

public interface DispatchOrderService {

    Long createOrder(Long merchantId, DispatchOrderCreateReqVO reqVO);

    void acceptOrder(Long orderId, Long userId);

    void startOrder(Long orderId, Long userId);

    void finishOrder(Long orderId, Long userId);

    void cancelOrder(Long orderId, Long merchantId, String reason);

    void cancelOrderByAdmin(Long orderId, String reason);

    DispatchOrderDO getOrder(Long orderId);

    PageResult<DispatchOrderDO> getHallPage(DispatchOrderPageReqVO reqVO);

    PageResult<DispatchOrderDO> getUserOrderPage(Long userId, DispatchOrderPageReqVO reqVO);

    PageResult<DispatchOrderDO> getMerchantOrderPage(Long merchantId, DispatchOrderPageReqVO reqVO);

    PageResult<DispatchOrderDO> getAdminOrderPage(DispatchOrderPageReqVO reqVO);

}
