package com.yy.module.dispatch.dal.mysql.merchant;

import com.yy.framework.common.pojo.PageResult;
import com.yy.framework.mybatis.core.mapper.BaseMapperX;
import com.yy.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.yy.module.dispatch.controller.admin.merchant.vo.DispatchMerchantPageReqVO;
import com.yy.module.dispatch.dal.dataobject.merchant.DispatchMerchantDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DispatchMerchantMapper extends BaseMapperX<DispatchMerchantDO> {

    default DispatchMerchantDO selectByMemberUserId(Long memberUserId) {
        return selectOne(DispatchMerchantDO::getMemberUserId, memberUserId);
    }

    default PageResult<DispatchMerchantDO> selectPage(DispatchMerchantPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DispatchMerchantDO>()
                .likeIfPresent(DispatchMerchantDO::getName, reqVO.getName())
                .eqIfPresent(DispatchMerchantDO::getStatus, reqVO.getStatus())
                .eqIfPresent(DispatchMerchantDO::getMemberUserId, reqVO.getMemberUserId())
                .orderByDesc(DispatchMerchantDO::getId));
    }
}
