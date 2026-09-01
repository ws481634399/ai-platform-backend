package com.ai.mall.common.core.result;

/**
 * 错误码公共接口。
 *
 * <p>错误码分段约定（design.md §2.3，值域初稿）：
 * <ul>
 *   <li>0 —— 成功</li>
 *   <li>A0001~ —— 参数/请求类错误（客户端可修正）</li>
 *   <li>B0001~ —— 业务类错误（业务规则拒绝）</li>
 *   <li>S0001~ —— 系统类错误（服务端内部问题）</li>
 * </ul>
 * 各业务域后续在本分段值域内扩展自己的枚举，禁止跨段复用。
 */
public interface ErrorCode {

    /** 错误码（分段见类注释） */
    String getCode();

    /** 面向调用方的默认错误文案 */
    String getMessage();
}
