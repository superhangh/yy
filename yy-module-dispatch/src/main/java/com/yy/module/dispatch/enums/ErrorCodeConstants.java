package com.yy.module.dispatch.enums;

import com.yy.framework.common.exception.ErrorCode;

/**
 * Dispatch 错误码枚举类
 *
 * dispatch 系统，使用 1-099-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== 商家 1-099-001-000 ==========
    ErrorCode MERCHANT_NOT_EXISTS = new ErrorCode(1_099_001_000, "商家不存在");
    ErrorCode MERCHANT_APPLY_EXISTS = new ErrorCode(1_099_001_001, "该会员已提交过商家入驻申请");
    ErrorCode MERCHANT_NOT_ENABLED = new ErrorCode(1_099_001_002, "商家状态非正常，无法操作");
    ErrorCode MERCHANT_STATUS_ERROR = new ErrorCode(1_099_001_003, "商家审核状态不合法");

}
