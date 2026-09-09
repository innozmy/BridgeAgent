package myz.bridge_agent_demo.vo;

import lombok.Data;
import myz.bridge_agent_demo.entity.ModelTask;
import myz.bridge_agent_demo.entity.ModelTaskEvent;

import java.util.ArrayList;
import java.util.List;

/** 任务 + 时间线，给任务页一次渲染。 */
@Data
public class ModelTaskVO {
    private ModelTask task;
    private List<ModelTaskEvent> events = new ArrayList<>();
}
