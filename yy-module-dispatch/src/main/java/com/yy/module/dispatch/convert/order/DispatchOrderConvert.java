package com.yy.module.dispatch.convert.order;

import com.yy.framework.common.pojo.PageResult;
import com.yy.module.dispatch.controller.app.order.vo.AppDispatchOrderRespVO;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface DispatchOrderConvert {

    DispatchOrderConvert INSTANCE = Mappers.getMapper(DispatchOrderConvert.class);

    AppDispatchOrderRespVO convert(DispatchOrderDO bean);

    List<AppDispatchOrderRespVO> convertList(List<DispatchOrderDO> list);

    PageResult<AppDispatchOrderRespVO> convertPage(PageResult<DispatchOrderDO> page);
}
