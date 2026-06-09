package com.orangejd.common.constant;

/**
 * 业务异常消息常量 —— 集中管理，便于统一修改和国际化
 */
public class MessageConstant {

    // ---- 通用 ----
    public static final String NOT_FOUND = "数据不存在";
    public static final String NO_PERMISSION = "无权限";
    public static final String NOT_LOGIN = "未登录";

    // ---- 账号 ----
    public static final String USERNAME_EXISTS = "用户名已存在";
    public static final String ACCOUNT_NOT_FOUND = "账号不存在";
    public static final String OLD_PASSWORD_ERROR = "旧密码错误";

    // ---- 分类 ----
    public static final String CATEGORY_NAME_EMPTY = "分类名称不能为空";
    public static final String CATEGORY_TYPE_INVALID = "分类类型必须为 WORK 或 REQUIREMENT";
    public static final String CATEGORY_NAME_DUPLICATE = "该类型下已存在同名分类";
    public static final String CATEGORY_NOT_FOUND = "分类不存在";

    // ---- 作品 ----
    public static final String WORK_TITLE_EMPTY = "作品标题不能为空";
    public static final String WORK_NOT_FOUND = "作品不存在";
    public static final String WORK_EDIT_DENIED = "无权限编辑此作品";
    public static final String WORK_DELETE_DENIED = "无权限删除此作品";
    public static final String WORK_SUBMIT_DENIED = "仅草稿或被驳回的作品可提交审核";
    public static final String WORK_AUDIT_DENIED = "作品不是待审核状态";

    // ---- 需求 ----
    public static final String REQ_TITLE_EMPTY = "需求标题不能为空";
    public static final String REQ_NOT_FOUND = "需求不存在";
    public static final String REQ_EDIT_DENIED = "无权限编辑此需求";
    public static final String REQ_DELETE_DENIED = "无权限删除此需求";
    public static final String REQ_SUBMIT_DENIED = "仅草稿或被驳回的需求可提交审核";
    public static final String REQ_AUDIT_DENIED = "需求不是待审核状态";

    // ---- 评论 ----
    public static final String COMMENT_EMPTY = "评论不能为空";
    public static final String COMMENT_TOO_LONG = "评论过长";

}
