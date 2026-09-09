package myz.bridge_agent_demo.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 参数袋不得与固定列、联/墩柱子表抢含义。识图 extracts 与概览手填共用这一拒写名单。
 * 手填中文键/名称按专业同义表归一到目录 camelCase（与建模 check 同一套用语，含「2#墩桩径」最长子串）。
 */
public final class LedgerParamRules {

    private record Phrase(String folded, String canon) {
    }

    private static final List<Phrase> PHRASES = buildPhrases();

    private LedgerParamRules() {
    }

    /**
     * 入参：袋项 key 与中文 label（可空）。
     * 返回：目录标准 key；无法识别则退回原 key（已 trim）。
     */
    public static String canonicalBagKey(String key, String label) {
        String hit = senseOf(key, label);
        if (hit != null) {
            return hit;
        }
        return key == null ? "" : key.trim();
    }

    /** 与 Python {@code param_sense.sense_of} 对齐：整词优先，否则最长包含。 */
    public static String senseOf(String key, String label) {
        List<String> fields = new ArrayList<>();
        String k = fold(key);
        String l = fold(label);
        if (!k.isEmpty()) {
            fields.add(k);
        }
        if (!l.isEmpty()) {
            fields.add(l);
        }
        if (fields.isEmpty()) {
            return null;
        }
        for (Phrase phrase : PHRASES) {
            for (String field : fields) {
                if (field.equals(phrase.folded())) {
                    return phrase.canon();
                }
            }
        }
        for (Phrase phrase : PHRASES) {
            if (phrase.folded().length() < 2) {
                continue;
            }
            for (String field : fields) {
                if (field.contains(phrase.folded())) {
                    return phrase.canon();
                }
            }
        }
        return null;
    }

    private static String fold(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        return text.replace(" ", "").replace("_", "").replace("-", "").replace("—", "").replace("＃", "");
    }

    private static List<Phrase> buildPhrases() {
        List<Phrase> rows = new ArrayList<>();
        add(rows, "pileDiameterM", "钻孔桩径", "灌注桩径", "桩直径", "桩径", "pilediameterm", "pilediameter", "piledia");
        add(rows, "pileLengthM", "桩长度", "桩长", "pilelengthm", "pilelength");
        add(rows, "pileCountPerPier", "每墩桩数", "单墩桩数", "桩根数", "桩数", "pilecountperpier");
        add(rows, "pileLayout", "桩位布置", "桩位图", "桩位", "pilelayout");
        add(rows, "foundationType", "基础形式", "基础类型", "基础型式", "foundationtype");
        add(rows, "pierDiameterM", "墩柱直径", "圆柱直径", "墩直径", "墩径", "pierdiameterm", "pierdiameter");
        add(rows, "pierSectionB", "矩形墩横桥向", "墩柱边长", "piersectionb");
        add(rows, "pierType", "桥墩类型", "墩类型", "墩身形式", "piertype");
        add(rows, "girderCount", "单幅片数", "主梁片数", "梁片数", "片数", "girdercount");
        add(rows, "girderSpacingM", "主梁中心距", "梁中心距", "梁距", "girderspacingm", "girderspacing");
        add(rows, "girderHeightM", "主梁梁高", "梁高", "girderheightm", "girderheight");
        add(rows, "girderHeightAtMidspanM", "跨中梁高", "girderheightatmidspanm");
        add(rows, "innerGirderWidthM", "中梁顶宽", "中梁宽", "innergirderwidthm");
        add(rows, "edgeGirderWidthM", "边梁顶宽", "边梁宽", "edgegirderwidthm");
        add(rows, "girderBottomWidthM", "梁底宽", "底板宽", "girderbottomwidthm");
        add(rows, "bearingType", "支座类型", "支座型号", "bearingtype");
        add(rows, "bearingLayout", "支座布置", "每片支座", "bearinglayout");
        add(rows, "bearingCountPerGirder", "每片梁支座数", "每片支座数", "bearingcountperpier");
        add(rows, "bearingRubberThickM", "橡胶层总厚", "橡胶层厚", "胶层厚", "bearingrubberthickm");
        add(rows, "skewDeg", "斜交角", "skewdeg");
        add(rows, "pierMaterial", "墩身砼", "下部标号", "墩砼", "piermaterial");
        add(rows, "capBeam", "盖梁", "capbeam");
        add(rows, "capBeamHeightM", "盖梁高", "盖梁高度", "capbeamheightm");
        add(rows, "capBeamWidthM", "盖梁宽", "盖梁宽度", "capbeamwidthm");
        add(rows, "capBeamLengthM", "盖梁长", "盖梁长度", "capbeamlengthm");
        add(rows, "tieBeam", "系梁", "tiebeam");
        add(rows, "tieBeamHeightM", "系梁高", "tiebeamheightm");
        add(rows, "tieBeamWidthM", "系梁宽", "tiebeamwidthm");
        add(rows, "soilM", "土弹簧m", "m值", "地基m", "soilm");
        add(rows, "bearingG", "支座剪切模量", "橡胶g", "bearingg");
        add(rows, "sdlKNPerM", "二期恒载", "sdlknperm");
        add(rows, "curveRadiusM", "平曲线半径", "曲线半径", "curveradiusm");
        rows.sort(Comparator.comparingInt((Phrase p) -> p.folded().length()).reversed());
        return List.copyOf(rows);
    }

    private static void add(List<Phrase> rows, String canon, String... words) {
        rows.add(new Phrase(fold(canon), canon));
        for (String word : words) {
            String folded = fold(word);
            if (!folded.isEmpty()) {
                rows.add(new Phrase(folded, canon));
            }
        }
    }

    /**
     * 入参含义：建议 key（可为 camelCase）。
     * 返回：是否禁止进 {@code project_param}（应走固定列或联表）。
     */
    public static boolean reservedKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        return switch (key.trim()) {
            case "code", "carriageway", "girderType", "layoutType", "material", "region",
                 "spansM", "name", "intro", "openedOn", "codeStrategy", "status",
                 "nSpans", "units", "pierColumnHeightM" -> true;
            default -> false;
        };
    }

    /**
     * 入参含义：专业中文 label。
     * 返回：是否与固定列同义，禁止再开袋项。
     */
    public static boolean reservedLabel(String label) {
        if (label == null || label.isBlank()) {
            return false;
        }
        return switch (label.trim()) {
            case "工程标号", "幅面", "主梁形式", "结构形式", "材料", "地区", "跨径", "跨径组合",
                 "墩高", "墩柱高", "柱高" -> true;
            default -> false;
        };
    }
}
