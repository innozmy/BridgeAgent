package myz.bridge_agent_demo.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** GET /api/resource-queue 的整页快照。 */
@Data
public class ResourceQueueVO {

    private List<ResourceQueueLaneVO> lanes = new ArrayList<>();
}
