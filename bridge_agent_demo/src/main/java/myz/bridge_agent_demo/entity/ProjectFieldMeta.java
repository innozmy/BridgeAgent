package myz.bridge_agent_demo.entity;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 项目级字段的来源与上次识图值。对应 {@code project.field_meta}。
 * 联跨径的当前值仍在 {@code project_unit}；这里只记 spansM 的来源/对照值，以及本轮缺口。
 */
@Data
public class ProjectFieldMeta {

    private FieldProvenance code;
    private FieldProvenance girderType;
    private FieldProvenance layoutType;
    private FieldProvenance material;
    private FieldProvenance region;
    /** 跨径对照：drawingValue 如 {@code 40+60+40}，多联用 {@code | } 分隔 */
    private FieldProvenance spansM;
    /** 最近一次识图缺口，如 spansM / girderType / missing_layout */
    private List<String> gaps = new ArrayList<>();
    /** 粗看是否见到总布置或立面；null 表示还没跑过识图 */
    private Boolean hasLayoutPages;
}
