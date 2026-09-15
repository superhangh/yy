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

    // ========== 订单 1-099-002-000 ==========
    ErrorCode ORDER_NOT_EXISTS = new ErrorCode(1_099_002_000, "派单订单不存在");
    ErrorCode ORDER_STATUS_ERROR = new ErrorCode(1_099_002_001, "订单当前状态不允许该操作");
    ErrorCode ORDER_ALREADY_ACCEPTED = new ErrorCode(1_099_002_002, "订单已被接单");
    ErrorCode ORDER_NOT_MERCHANT_OWNER = new ErrorCode(1_099_002_003, "该订单不属于当前商家");
    ErrorCode ORDER_NOT_USER_OWNER = new ErrorCode(1_099_002_004, "该订单不属于当前用户");

    // ========== 钱包 1-099-003-000 ==========
    ErrorCode WALLET_NOT_EXISTS = new ErrorCode(1_099_003_000, "商家钱包不存在");
    ErrorCode WALLET_INSUFFICIENT_BALANCE = new ErrorCode(1_099_003_001, "商家钱包余额不足");
    ErrorCode SETTLEMENT_NOT_EXISTS = new ErrorCode(1_099_003_002, "结算记录不存在");
    ErrorCode SETTLEMENT_STATUS_ERROR = new ErrorCode(1_099_003_003, "结算状态不允许该操作");

}
