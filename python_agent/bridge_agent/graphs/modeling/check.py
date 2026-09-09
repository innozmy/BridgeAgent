"""对照账本投影做开建硬条件检查。不调 VL、不启 SAP。"""

from __future__ import annotations

from typing import Any

from bridge_agent.settings import GAP_FOCUS_KINDS
from bridge_agent.graphs.modeling.param_sense import index_params
from bridge_agent.skills.loader import modeling_skill


def _params(ledger: dict) -> dict[str, str]:
    """遍历袋的 key 与 label（含中文），按同义表对齐到目录 key。"""
    return index_params(ledger.get("params") or [])


def _num(value) -> float | None:
    """账本投影里柱高可能是数字或带单位字符串；0 也算有值。"""
    if value is None or value is False:
        return None
    if isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return float(value)
    text = str(value).strip()
    if not text:
        return None
    raw = text.replace("mm", "").replace("m", "").replace("×", "x").replace("*", "x")
    try:
        return float(raw.split("x")[0].replace(",", ""))
    except ValueError:
        return None


def _truthy_span(spans) -> bool:
    if isinstance(spans, str):
        try:
            import json
            spans = json.loads(spans)
        except Exception:
            return bool(spans.strip())
    if not isinstance(spans, list) or not spans:
        return False
    return any(_num(str(x)) and _num(str(x)) > 0 for x in spans)


def _all_supports(ledger: dict) -> list[dict]:
    out: list[dict] = []
    for unit in ledger.get("units") or []:
        if not isinstance(unit, dict):
            continue
        for support in unit.get("supports") or []:
            if isinstance(support, dict):
                out.append(support)
    return out


def _is_abutment(support: dict) -> bool:
    kind = str(support.get("kind") or "").strip().lower()
    return kind in ("abutment", "abut", "台", "桥台") or kind.endswith("台")


def _column_height(col: dict):
    if col.get("heightM") is not None:
        return col.get("heightM")
    return col.get("height_m")


def _support_heights_ok(support: dict) -> bool:
    cols = support.get("columns") or []
    if not cols:
        return False
    for col in cols:
        if not isinstance(col, dict) or _num(_column_height(col)) is None:
            return False
    return True


def _pier_heights_ok(ledger: dict) -> bool:
    """账本已有中间墩柱高则通过。全标成桥台但每行都有柱高，不误报缺失。"""
    supports = _all_supports(ledger)
    if not supports:
        return False
    piers = [item for item in supports if not _is_abutment(item)]
    targets = piers if piers else supports
    return all(_support_heights_ok(item) for item in targets)


def _is_rigid(params: dict[str, str]) -> bool:
    text = (params.get("pierType") or "") + (params.get("layoutType") or "")
    return "刚构" in text or "固结" in text


def _bearing_ok(params: dict[str, str]) -> tuple[bool, str]:
    btype = params.get("bearingType") or ""
    if any(word in btype for word in ("盆", "球钢", "球形")):
        return False, "第一版只建板式支座（GJZ/GJZF），盆式/球钢为硬缺口"
    plate = (not btype) or ("GJZ" in btype.upper()) or ("板式" in btype) or ("橡胶" in btype)
    if not plate:
        return False, "支座类型不是板式"
    layout = params.get("bearingLayout") or params.get("bearingCountPerGirder")
    thick = params.get("bearingRubberThickM")
    if not layout:
        return False, "缺板式支座布置（每片几块/排数）"
    # 板式按 SAP Link 建，不建几何截面；平面尺寸 bearingSize 不是硬条件
    if _num(thick) is None:
        return False, "缺橡胶层总厚"
    return True, ""


def _foundation_ok(params: dict[str, str]) -> tuple[bool, str]:
    ftype = params.get("foundationType") or ""
    if not ftype:
        return False, "缺基础形式"
    if "扩大" in ftype or "明挖" in ftype:
        return True, ""
    if "桩" in ftype or "桩柱" in ftype:
        if _num(params.get("pileDiameterM")) is None:
            return False, "有桩但缺桩径"
        if _num(params.get("pileLengthM")) is None:
            return False, "有桩但缺桩长"
        if not (params.get("pileCountPerPier") or params.get("pileLayout")):
            return False, "有桩但缺桩数或桩位"
        return True, ""
    return True, ""


def _cap_ok(ledger: dict, params: dict[str, str]) -> bool:
    """多柱墩必须有盖梁截面，或账本写明无盖梁。禁止编 1.5×1.5。"""
    multi = False
    for support in _all_supports(ledger):
        if _is_abutment(support):
            continue
        if len(support.get("columns") or []) >= 2:
            multi = True
            break
    if not multi:
        return True
    text = (params.get("capBeam") or "").strip()
    if text in ("无", "没有", "无盖梁"):
        return True
    return _num(params.get("capBeamHeightM")) is not None and _num(params.get("capBeamWidthM")) is not None


def check_ledger(payload: dict[str, Any]) -> dict[str, Any]:
    """返回 missing 列表；空则可以 spec/build。切片 required.md 由代码实现，不注入 VL。"""
    modeling_skill("required.md", "seismic.md")
    ledger = payload.get("ledger") if isinstance(payload.get("ledger"), dict) else {}
    params = _params(ledger)
    missing: list[dict[str, Any]] = []

    units = ledger.get("units") or []
    has_span = any(isinstance(u, dict) and _truthy_span(u.get("spansM")) for u in units)
    if not has_span:
        missing.append(_gap("spansM", "至少一联跨径"))

    if not str(ledger.get("girderType") or "").strip():
        missing.append(_gap("girderType", "缺主梁形式"))
    if not str(ledger.get("material") or "").strip():
        missing.append(_gap("material", "缺主梁材料/砼标号"))

    if _num(params.get("girderCount")) is None:
        missing.append(_gap("girderCount", "缺单幅主梁片数"))
    if _num(params.get("girderSpacingM")) is None:
        missing.append(_gap("girderSpacingM", "缺梁距"))

    h = _num(params.get("girderHeightM")) or _num(params.get("girderHeightAtMidspanM"))
    w = _num(params.get("innerGirderWidthM")) or _num(params.get("edgeGirderWidthM")) or _num(params.get("girderBottomWidthM"))
    if h is None or w is None:
        missing.append(_gap("girderSection", "缺主梁截面（梁高+宽度）"))

    if not _pier_heights_ok(ledger):
        missing.append(_gap("pierHeightM", "墩缺柱数或柱高"))

    if _num(params.get("pierDiameterM")) is None and _num(params.get("pierSectionB")) is None:
        missing.append(_gap("pierSection", "缺墩柱截面尺寸"))

    if not _cap_ok(ledger, params):
        missing.append(_gap("capBeam", "多柱墩缺盖梁截面（宽×高），不要编标准盖梁"))

    if not _is_rigid(params):
        ok, why = _bearing_ok(params)
        if not ok:
            missing.append(_gap("bearing", why))

    ok_f, why_f = _foundation_ok(params)
    if not ok_f:
        missing.append(_gap("foundation", why_f))

    skew = params.get("skewDeg")
    curve = params.get("curveRadiusM")
    note = (params.get("alignmentNote") or "") + str(ledger.get("intro") or "")
    if any(w in note for w in ("斜", "弯", "圆曲线")) and _num(skew) is None and _num(curve) is None:
        missing.append(_gap("skewOrCurve", "图上斜/弯但读不出斜交角或半径"))

    return {"missing": missing, "params": params}


def _gap(key: str, reason: str) -> dict[str, Any]:
    """硬缺口一条：focusKinds 含专页启发式 + 分类不准时的扩扫类。"""
    kinds = list(GAP_FOCUS_KINDS.get(key) or [])
    return {"key": key, "focusKinds": kinds, "reason": reason}


def _tomb_keys(tomb: dict) -> list[str]:
    keys: list[str] = []
    seen: set[str] = set()
    for raw in tomb.get("missingKeys") or []:
        text = str(raw or "").strip()
        if text and text not in seen:
            keys.append(text)
            seen.add(text)
    asked = str(tomb.get("askedKey") or "").strip()
    if asked and asked not in seen:
        keys.append(asked)
    return keys


def _swept_pairs(tombs) -> set[tuple[int, str]]:
    """仅「已扩扫仍缺」的墓碑挡住再识。一条墓碑可带多个 missingKeys。"""
    pairs: set[tuple[int, str]] = set()
    for tomb in tombs or []:
        if not isinstance(tomb, dict):
            continue
        if tomb.get("sweep") is not True:
            continue
        try:
            fid = int(tomb.get("fileId"))
        except (TypeError, ValueError):
            continue
        for asked in _tomb_keys(tomb):
            pairs.add((fid, asked))
    return pairs


def _is_catalog_pdf(item: dict) -> bool:
    kind = str(item.get("kind") or "").lower()
    if kind in ("dwg", "dxf", "cad"):
        return False
    name = str(item.get("originalName") or "").lower()
    if name.endswith(".dwg") or name.endswith(".dxf"):
        return False
    return True


def _catalog_file_id(item: dict) -> int | None:
    try:
        return int(item.get("fileId"))
    except (TypeError, ValueError):
        return None


def _kinds_hit_counts(kinds: list[str], counts: dict) -> bool:
    if not kinds:
        return True
    return any(int(counts.get(k) or 0) > 0 for k in kinds)


def _need_row(file_id: int, items: list[dict[str, Any]]) -> dict[str, Any]:
    keys: list[str] = []
    kinds: list[str] = []
    reasons: list[str] = []
    seen_kind: set[str] = set()
    for item_miss in items:
        asked_key = str(item_miss.get("key") or "")
        if asked_key and asked_key not in keys:
            keys.append(asked_key)
        for kind in item_miss.get("focusKinds") or GAP_FOCUS_KINDS.get(asked_key) or []:
            text = str(kind or "").strip()
            if text and text not in seen_kind:
                kinds.append(text)
                seen_kind.add(text)
        reason = str(item_miss.get("reason") or "").strip()
        if reason and reason not in reasons:
            reasons.append(reason)
    return {
        "fileId": file_id,
        "focusKinds": kinds,
        "missingKeys": keys,
        "askedKey": keys[0] if keys else "",
        "reason": "；".join(reasons) or "硬缺口",
        "sweep": True,
    }


def _pick_file_for_gaps(
    catalog: list,
    remaining: list[dict[str, Any]],
    swept: set[tuple[int, str]],
    require_kind_match: bool,
) -> int | None:
    for item_miss in remaining:
        asked_key = str(item_miss.get("key") or "")
        kinds = list(item_miss.get("focusKinds") or GAP_FOCUS_KINDS.get(asked_key) or [])
        for item in catalog:
            if not isinstance(item, dict) or not _is_catalog_pdf(item):
                continue
            file_id = _catalog_file_id(item)
            if file_id is None:
                continue
            if asked_key and (file_id, asked_key) in swept:
                continue
            counts = item.get("kindCounts") if isinstance(item.get("kindCounts"), dict) else {}
            if require_kind_match and not _kinds_hit_counts(kinds, counts):
                continue
            return file_id
    return None


def pick_need_supplement(payload: dict[str, Any], missing: list[dict[str, Any]]) -> dict[str, Any] | None:
    """硬缺口汇总后只开一份 PDF。页类对不上也要打开这份图做扩扫。"""
    if not missing:
        return None
    catalog = payload.get("drawingCatalog") or []
    stm = payload.get("modelingStm") if isinstance(payload.get("modelingStm"), dict) else {}
    swept = _swept_pairs(stm.get("tombstones") or [])
    remaining = [
        item for item in missing
        if str(item.get("key") or "") and not _key_swept_on_all_pdfs(str(item.get("key")), catalog, swept)
    ]
    if not remaining:
        return None
    file_id = _pick_file_for_gaps(catalog, remaining, swept, True)
    if file_id is None:
        file_id = _pick_file_for_gaps(catalog, remaining, swept, False)
    if file_id is None:
        return None
    on_file = [
        item for item in remaining
        if (file_id, str(item.get("key") or "")) not in swept
    ]
    if not on_file:
        return None
    return _need_row(file_id, on_file)


def _key_swept_on_all_pdfs(key: str, catalog: list, swept: set[tuple[int, str]]) -> bool:
    pdf_ids: list[int] = []
    for item in catalog:
        if not isinstance(item, dict) or not _is_catalog_pdf(item):
            continue
        file_id = _catalog_file_id(item)
        if file_id is not None:
            pdf_ids.append(file_id)
    if not pdf_ids:
        return True
    return all((fid, key) in swept for fid in pdf_ids)
