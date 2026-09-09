package myz.bridge_agent_demo.dto;

import lombok.Data;

/**
 * 项目列表筛选。都是可选查询参数，例如 {@code GET /api/projects?name=沪闵}。
 */
@Data
public class ProjectQuery {

    private String name;
    private String code;
    private String region;
    private String carriageway;
    private String status;
}
