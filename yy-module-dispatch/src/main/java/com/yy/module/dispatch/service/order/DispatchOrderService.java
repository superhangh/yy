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

    /**
     * 取消超时未接的订单（status=待接单 且 deadline < now）
     *
     * @return 取消数量
     */
    int cancelTimeoutOrders();

    DispatchOrderDO getOrder(Long orderId);

    /**
     * 用户端订单详情：待接单（大厅可见）或本人接单的订单可见，否则抛 ORDER_NOT_EXISTS
     */
    DispatchOrderDO getUserOrder(Long orderId, Long userId);

    /**
     * 商家端订单详情：仅本商家的订单可见，否则抛 ORDER_NOT_EXISTS
     */
    DispatchOrderDO getMerchantOrder(Long orderId, Long merchantId);

    PageResult<DispatchOrderDO> getHallPage(DispatchOrderPageReqVO reqVO);

    PageResult<DispatchOrderDO> getUserOrderPage(Long userId, DispatchOrderPageReqVO reqVO);

    PageResult<DispatchOrderDO> getMerchantOrderPage(Long merchantId, DispatchOrderPageReqVO reqVO);

    PageResult<DispatchOrderDO> getAdminOrderPage(DispatchOrderPageReqVO reqVO);

}
