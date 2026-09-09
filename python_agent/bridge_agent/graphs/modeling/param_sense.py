"""参数袋同义认读：遍历 key + label（含中文键），对齐到目录 camelCase。

建模 check 不调大模型。「意思相近」= 专业同义表 + 最长子串
（如「2#墩桩径」→ pileDiameterM），不是口语随便近义，以免墩径认成桩径。
"""

from __future__ import annotations

# 每个目录 key 的用语，长词在前。折叠后做精确或包含匹配。
_SENSE: dict[str, tuple[str, ...]] = {
    "pileDiameterM": ("钻孔桩径", "灌注桩径", "桩直径", "桩径", "pilediameterm", "pilediameter", "piledia"),
    "pileLengthM": ("桩长度", "桩长", "pilelengthm", "pilelength"),
    "pileCountPerPier": ("每墩桩数", "单墩桩数", "桩根数", "桩数", "pilecountperpier"),
    "pileLayout": ("桩位布置", "桩位图", "桩位", "pilelayout"),
    "foundationType": ("基础形式", "基础类型", "基础型式", "foundationtype"),
    "pierDiameterM": ("墩柱直径", "圆柱直径", "墩直径", "墩径", "pierdiameterm", "pierdiameter"),
    "pierSectionB": ("矩形墩横桥向", "墩柱边长", "piersectionb"),
    "pierType": ("桥墩类型", "墩类型", "墩身形式", "piertype"),
    "girderCount": ("单幅片数", "主梁片数", "梁片数", "片数", "girdercount"),
    "girderSpacingM": ("主梁中心距", "梁中心距", "梁距", "girderspacingm", "girderspacing"),
    "girderHeightM": ("主梁梁高", "梁高", "girderheightm", "girderheight"),
    "girderHeightAtMidspanM": ("跨中梁高", "girderheightatmidspanm"),
    "innerGirderWidthM": ("中梁顶宽", "中梁宽", "innergirderwidthm"),
    "edgeGirderWidthM": ("边梁顶宽", "边梁宽", "edgegirderwidthm"),
    "girderBottomWidthM": ("梁底宽", "底板宽", "girderbottomwidthm"),
    "bearingType": ("支座类型", "支座型号", "bearingtype"),
    "bearingLayout": ("支座布置", "每片支座", "bearinglayout"),
    "bearingCountPerGirder": ("每片梁支座数", "每片支座数", "bearingcountpergirder"),
    "bearingRubberThickM": ("橡胶层总厚", "橡胶层厚", "胶层厚", "bearingrubberthickm"),
    "bearingSize": ("支座平面尺寸", "bearingsize"),
    "skewDeg": ("斜交角", "skewdeg"),
    "curveRadiusM": ("平曲线半径", "曲线半径", "curveradiusm"),
    "alignmentNote": ("平面线形", "alignmentnote"),
    "deckSlabThickM": ("桥面板厚", "deckslabthickm"),
    "expansionJointM": ("伸缩缝宽", "expansionjointm"),
    "sdlKNPerM": ("二期恒载", "sdlknperm"),
    "pierMaterial": ("墩身砼", "下部标号", "墩砼", "piermaterial"),
    "capBeam": ("盖梁", "capbeam"),
    "capBeamHeightM": ("盖梁高", "盖梁高度", "capbeamheightm"),
    "capBeamWidthM": ("盖梁宽", "盖梁宽度", "capbeamwidthm"),
    "capBeamLengthM": ("盖梁长", "盖梁长度", "capbeamlengthm"),
    "tieBeam": ("系梁", "tiebeam"),
    "tieBeamHeightM": ("系梁高", "tiebeamheightm"),
    "tieBeamWidthM": ("系梁宽", "tiebeamwidthm"),
    "soilM": ("土弹簧m", "m值", "地基m", "soilm"),
    "bearingG": ("支座剪切模量", "橡胶g", "bearingg"),
}


def _fold(text: str) -> str:
    raw = (text or "").strip().lower()
    for old in (" ", "_", "-", "—", "＃"):
        raw = raw.replace(old, "")
    return raw


def _phrases() -> list[tuple[str, str]]:
    rows: list[tuple[str, str]] = []
    for canon, words in _SENSE.items():
        rows.append((_fold(canon), canon))
        for word in words:
            folded = _fold(word)
            if folded:
                rows.append((folded, canon))
    rows.sort(key=lambda item: len(item[0]), reverse=True)
    return rows


_PHRASES = _phrases()


def sense_of(key: str, label: str) -> str:
    """一行袋项对应的目录 key；认不出则空串。优先整词相等，否则取最长包含。"""
    fields = [_fold(key), _fold(label)]
    fields = [item for item in fields if item]
    if not fields:
        return ""
    for phrase, canon in _PHRASES:
        if any(item == phrase for item in fields):
            return canon
    for phrase, canon in _PHRASES:
        if len(phrase) < 2:
            continue
        if any(phrase in item for item in fields):
            return canon
    return ""


def index_params(rows) -> dict[str, str]:
    """遍历袋：目录 key、原始 key、原始 label 都能取到同一值。"""
    out: dict[str, str] = {}
    if not isinstance(rows, list):
        return out
    for item in rows:
        if not isinstance(item, dict):
            continue
        key = str(item.get("key") or "").strip()
        label = str(item.get("label") or "").strip()
        value = item.get("value")
        text = "" if value is None else str(value).strip()
        if not text:
            continue
        if key:
            out[key] = text
        if label:
            out[label] = text
        canon = sense_of(key, label)
        if canon:
            out[canon] = text
    return out
