package com.yy.module.dispatch.convert.merchant;

import com.yy.framework.common.pojo.PageResult;
import com.yy.module.dispatch.controller.admin.merchant.vo.DispatchMerchantRespVO;
import com.yy.module.dispatch.dal.dataobject.merchant.DispatchMerchantDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface DispatchMerchantConvert {

    DispatchMerchantConvert INSTANCE = Mappers.getMapper(DispatchMerchantConvert.class);

    DispatchMerchantRespVO convert(DispatchMerchantDO bean);

    List<DispatchMerchantRespVO> convertList(List<DispatchMerchantDO> list);

    PageResult<DispatchMerchantRespVO> convertPage(PageResult<DispatchMerchantDO> page);
}
