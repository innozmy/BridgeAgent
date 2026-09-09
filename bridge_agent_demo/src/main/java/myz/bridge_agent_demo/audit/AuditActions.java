package myz.bridge_agent_demo.audit;

/**
 * 权限审计动作枚举值，写入 {@code sys_audit.action}。
 * 不含识图/建模/建删项目/知识文件。
 */
public final class AuditActions {

    public static final String LOGIN_OK = "login_ok";
    public static final String LOGIN_FAIL = "login_fail";
    public static final String PASSWORD_CHANGE = "password_change";
    public static final String PASSWORD_RESET = "password_reset";
    public static final String USER_NICKNAME = "user_nickname";
    public static final String USER_AVATAR = "user_avatar";
    public static final String USER_CREATE = "user_create";
    public static final String USER_STATUS = "user_status";
    public static final String USER_ROLE = "user_role";
    public static final String ROLE_CREATE = "role_create";
    public static final String ROLE_UPDATE = "role_update";
    public static final String ROLE_DELETE = "role_delete";
    public static final String MEMBER_ASSIGN = "member_assign";
    public static final String MEMBER_REMOVE = "member_remove";

    private AuditActions() {
    }
}
