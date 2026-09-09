package myz.bridge_agent_demo.auth;

import myz.bridge_agent_demo.entity.ModelTask;
import myz.bridge_agent_demo.entity.ModelTaskEvent;

/**
 * 作业链：有 HTTP 用户就记下；{@code @Async} 里没有 ThreadLocal 则时间线记「系统」。
 */
public final class JobActors {

    private JobActors() {
    }

    public static void fillCreator(ModelTask task) {
        UserContext.User user = UserContext.get();
        if (user == null || task.getCreatedByUserId() != null) {
            return;
        }
        task.setCreatedByUserId(user.id());
        task.setCreatedByUsername(user.username());
    }

    public static void fillAgreed(ModelTask task) {
        UserContext.User user = UserContext.get();
        if (user == null) {
            return;
        }
        task.setAgreedByUserId(user.id());
        task.setAgreedByUsername(user.username());
    }

    public static void stampEvent(ModelTaskEvent event) {
        UserContext.User user = UserContext.get();
        if (user == null) {
            event.setActorUsername("系统");
            return;
        }
        event.setActorUserId(user.id());
        event.setActorUsername(user.username());
    }
}
