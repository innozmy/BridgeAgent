package myz.bridge_agent_demo.service;

import java.util.Set;

/**
 * 任务卡工种枚举。不是自由文本。
 */
public final class TaskKinds {

    public static final String DRAWING_FULL = "drawing_full";
    public static final String DRAWING_SUPPLEMENT = "drawing_supplement";
    public static final String MODELING = "modeling";
    public static final String ANALYSIS = "analysis";

    public static final Set<String> ALL = Set.of(DRAWING_FULL, DRAWING_SUPPLEMENT, MODELING, ANALYSIS);
    public static final Set<String> DRAWING = Set.of(DRAWING_FULL, DRAWING_SUPPLEMENT);

    public static final Set<String> PAGE_KINDS = Set.of(
            "catalog", "notes", "project_notes", "general_layout", "elevation", "site_plan",
            "cross_section", "quantity", "rebar", "pier", "foundation", "bearing", "geology",
            "archive", "detail", "other");

    private TaskKinds() {
    }

    public static boolean isDrawing(String kind) {
        return DRAWING_FULL.equals(kind)
                || DRAWING_SUPPLEMENT.equals(kind)
                || "图纸识别".equals(kind);
    }

    public static String titleOf(String kind) {
        return switch (kind) {
            case DRAWING_FULL -> "全册识图";
            case DRAWING_SUPPLEMENT -> "补充识别";
            case MODELING -> "建模";
            case ANALYSIS -> "分析";
            default -> kind;
        };
    }
}
