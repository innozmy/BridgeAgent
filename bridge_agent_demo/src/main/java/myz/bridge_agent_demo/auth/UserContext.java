package myz.bridge_agent_demo.auth;

/**
 * 请求级当前用户（ThreadLocal）。
 * 权限不放 JWT：拦完票后读角色写入这里。{@code @Async} 不要读。
 */
public final class UserContext {

    /**
     * 本请求已验过的人。{@code superUser} 视为知识库写与项目管理都开。
     */
    public record User(
            long id,
            String username,
            String nickname,
            String roleCode,
            boolean superUser,
            boolean knowledge,
            boolean projectAdmin,
            String avatarPath) {

        public boolean canWriteKnowledge() {
            return superUser || knowledge;
        }

        public boolean canManageAllProjects() {
            return superUser || projectAdmin;
        }
    }

    private static final ThreadLocal<User> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(User user) {
        HOLDER.set(user);
    }

    public static User get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
