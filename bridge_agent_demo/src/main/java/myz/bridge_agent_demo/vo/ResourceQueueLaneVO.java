package myz.bridge_agent_demo.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 一条资源车道的容量与当前占用。 */
@Data
public class ResourceQueueLaneVO {

    private String id;
    private String name;
    private int core;
    private int running;
    private int queued;
    private List<ResourceQueueItemVO> items = new ArrayList<>();
}
