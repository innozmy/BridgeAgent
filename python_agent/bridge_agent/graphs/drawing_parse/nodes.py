"""识图图节点。真正调 VL / 拆页的地方；边条件见 graph.py。"""

from __future__ import annotations

import json

from openai import APIStatusError, AuthenticationError, RateLimitError

from bridge_agent.graphs.drawing_parse.prompts import build_classify_prompt, build_extract_prompt
from bridge_agent.graphs.drawing_parse.state import DrawingParseState
from bridge_agent.graphs.drawing_parse.need_files import catalog_index, resolve_need_files
from bridge_agent.llm.qwen_vl import chat_json
from bridge_agent.memory.page_map import build_page_map
from bridge_agent.runtime import classify_api_error, fail, is_quota_message
from bridge_agent.settings import (
    API_KEY,
    COARSE_DPI,
    COARSE_PAGE_LIMIT,
    FINE_DPI,
    FINE_ROUND1_STRUCTURE_KINDS,
    LAYOUT_KINDS,
    MAX_FINE_PAGES,
    MAX_FULL_FINE_PAGES,
    MAX_SUPPLEMENT_FINE_PAGES,
    PAGE_KINDS,
    SKIP_SWEEP_KINDS,
    SWEEP_FILL_KINDS,
)
from bridge_agent.tools.drawings import collect_pdfs
from bridge_agent.tools.pdf import page_count, render_pages


def parse_classify(data: dict, batch_indices: list[int], total: int) -> dict[int, dict]:
    """页码 0-based → {kind, codeHint}。只收本批里的页。"""
    allowed = set(batch_indices)
    out: dict[int, dict] = {}
    for raw in data.get("pages") or []:
        if not isinstance(raw, dict):
            continue
        try:
            one_based = int(raw.get("i"))
        except (TypeError, ValueError):
            continue
        idx = one_based - 1
        if idx not in allowed or idx < 0 or idx >= total:
            continue
        kind = str(raw.get("kind") or "other").strip()
        if kind not in PAGE_KINDS:
            kind = "other"
        hint = raw.get("codeHint")
        out[idx] = {
            "kind": kind,
            "codeHint": str(hint).strip() if hint else None,
        }
    for idx in batch_indices:
        if idx not in out:
            out[idx] = {"kind": "other", "codeHint": None}
    return out


def classify_all_pages(path: str, total: int, payload: dict) -> dict[int, dict]:
    """分批粗看全书，合并页分类。各批 VL 调用互相独立。"""
    merged: dict[int, dict] = {}
    for start in range(0, total, COARSE_PAGE_LIMIT):
        batch = list(range(start, min(start + COARSE_PAGE_LIMIT, total)))
        coarse = render_pages(path, COARSE_DPI, batch)
        labels = ",".join(str(i + 1) for i in batch)
        data = chat_json([blob for _, blob in coarse], build_classify_prompt(payload, labels, total))
        merged.update(parse_classify(data, batch, total))
        print("[python-agent] 粗看 %s/%s" % (min(start + len(batch), total), total), flush=True)
    return merged


def _already_fine_indices(payload: dict) -> set[int]:
    """页地图 fineRead 是 1-based；已细看过的页优先让给尚未打开的页。"""
    existing = payload.get("existingPageMap") if isinstance(payload.get("existingPageMap"), dict) else {}
    out: set[int] = set()
    for item in existing.get("fineRead") or []:
        try:
            idx = int(item) - 1
        except (TypeError, ValueError):
            continue
        if idx >= 0:
            out.add(idx)
    return out


def _append_kind_pages(
    picked: list[int],
    page_map: dict[int, dict],
    kinds: set[str] | tuple[str, ...] | list[str],
    already: set[int],
    cap: int,
    skip_already: bool,
) -> None:
    wanted = {str(k) for k in kinds if k}
    for idx, meta in sorted(page_map.items()):
        if len(picked) >= cap:
            return
        if idx in picked:
            continue
        if skip_already and idx in already:
            continue
        if meta.get("kind") in wanted:
            picked.append(idx)


def pick_fine_pages(
    page_map: dict[int, dict],
    focus_kinds: list[str] | None = None,
    already_fine: set[int] | None = None,
) -> list[int]:
    """本轮细看。补充识别：先精确/相关页类，不够再遍历大样/其它等，不因分类漏标就 0 页收工。"""
    if focus_kinds:
        cap = MAX_SUPPLEMENT_FINE_PAGES
        already = already_fine or set()
        wanted = [str(k) for k in focus_kinds if k and k != "rebar"]
        picked: list[int] = []
        _append_kind_pages(picked, page_map, wanted, already, cap, skip_already=True)
        if len(picked) < cap:
            _append_kind_pages(picked, page_map, SWEEP_FILL_KINDS, already, cap, skip_already=True)
        if len(picked) < cap:
            for idx, meta in sorted(page_map.items()):
                if len(picked) >= cap:
                    break
                if idx in picked or idx in already:
                    continue
                if meta.get("kind") in SKIP_SWEEP_KINDS:
                    continue
                picked.append(idx)
        # 相关页都已细看过仍 0 页：退回再读指定类（避免空转）
        if not picked:
            _append_kind_pages(picked, page_map, wanted, already, cap, skip_already=False)
        if not picked:
            _append_kind_pages(picked, page_map, SWEEP_FILL_KINDS, already, cap, skip_already=False)
        return picked[:cap]
    cap = MAX_FULL_FINE_PAGES
    picked: list[int] = []
    notes = [i for i, meta in sorted(page_map.items()) if meta.get("kind") == "project_notes"]
    layout = [i for i, meta in sorted(page_map.items()) if meta.get("kind") in LAYOUT_KINDS]
    quantity = [i for i, meta in sorted(page_map.items()) if meta.get("kind") == "quantity"]
    if notes:
        picked.append(notes[0])
    for idx in layout:
        if len(picked) >= cap:
            break
        if idx not in picked:
            picked.append(idx)
        if sum(1 for p in picked if page_map.get(p, {}).get("kind") in LAYOUT_KINDS) >= 2:
            break
    for kind in FINE_ROUND1_STRUCTURE_KINDS:
        _append_kind_pages(picked, page_map, (kind,), set(), cap, skip_already=False)
        # 墩柱构造常不止一页（各墩或桩柱/盖梁分开），最多再收一页
        if kind == "pier" and len(picked) < cap:
            _append_kind_pages(picked, page_map, ("pier",), set(), min(cap, len(picked) + 1), skip_already=False)
        if len(picked) >= cap:
            break
    if len(picked) < cap and quantity:
        if quantity[0] not in picked:
            picked.append(quantity[0])
    return picked[:cap]


def normalize_extracts(raw) -> list[dict]:
    """只保留带 label 或 key 的摘录，供页地图 extracts。"""
    if not isinstance(raw, list):
        return []
    out: list[dict] = []
    for item in raw:
        if not isinstance(item, dict):
            continue
        key = str(item.get("key") or "").strip()
        label = str(item.get("label") or "").strip()
        if not key and not label:
            continue
        row = {
            "key": key or None,
            "label": label or key,
            "value": item.get("value"),
            "unit": item.get("unit"),
            "page": item.get("page"),
        }
        note = item.get("note")
        if note:
            row["note"] = str(note)[:200]
        out.append(row)
    return out


def normalize_units(raw) -> list[dict]:
    """跨径与墩柱分开；双柱必须拆成两条 column。"""
    if not isinstance(raw, list):
        return []
    units: list[dict] = []
    for item in raw:
        if not isinstance(item, dict):
            continue
        try:
            seq = int(item.get("seq"))
        except (TypeError, ValueError):
            continue
        spans = []
        for span in item.get("spansM") or item.get("spans_m") or []:
            try:
                spans.append(float(span))
            except (TypeError, ValueError):
                continue
        supports = []
        for raw_s in item.get("supports") or []:
            if not isinstance(raw_s, dict):
                continue
            try:
                s_seq = int(raw_s.get("seq"))
            except (TypeError, ValueError):
                s_seq = len(supports)
            kind = str(raw_s.get("kind") or "pier").strip().lower()
            if kind not in {"pier", "abutment"}:
                kind = "abutment" if "台" in str(raw_s.get("kind") or "") else "pier"
            columns = []
            for raw_c in raw_s.get("columns") or []:
                if not isinstance(raw_c, dict):
                    continue
                height = raw_c.get("heightM")
                if height is None:
                    height = raw_c.get("height_m")
                try:
                    height_m = float(height) if height is not None else None
                except (TypeError, ValueError):
                    height_m = None
                if height_m is not None and height_m <= 0:
                    height_m = None
                try:
                    c_seq = int(raw_c.get("seq"))
                except (TypeError, ValueError):
                    c_seq = len(columns) + 1
                side = raw_c.get("side")
                side = str(side).strip() if side else None
                columns.append({"seq": c_seq, "side": side, "heightM": height_m})
            supports.append({
                "seq": s_seq,
                "code": (str(raw_s.get("code")).strip() if raw_s.get("code") else None),
                "kind": kind,
                "columns": columns,
            })
        if supports:
            row = {"seq": seq, "supports": supports}
            if spans:
                row["spansM"] = spans
            units.append(row)
        elif spans:
            units.append({"seq": seq, "spansM": spans})
    return units


def collect_gaps(result: dict, has_layout: bool) -> list[str]:
    gaps: list[str] = []
    if not (result.get("units") or []):
        gaps.append("spansM")
        if not has_layout:
            gaps.append("missing_layout")
    if not (result.get("girderType") or "").strip():
        gaps.append("girderType")
    if not (result.get("layoutType") or "").strip():
        gaps.append("layoutType")
    if not (result.get("material") or "").strip():
        gaps.append("material")
    return gaps


def ok_or_end(state: DrawingParseState) -> str:
    if state.get("error") or (state.get("response") and not state.get("response", {}).get("ok")):
        return "end"
    return "ok"


def after_open(state: DrawingParseState) -> str:
    if state.get("error"):
        return "end"
    payload = state.get("payload") or {}
    if str(payload.get("mode") or "") == "supplement":
        return "mapped"
    total = int(state.get("total") or 0)
    if total <= MAX_FINE_PAGES:
        return "short"
    return "long"


def load_map(state: DrawingParseState) -> dict:
    """补充识别：用 Spring 注入的页地图，禁止当没扫过。"""
    payload = state.get("payload") or {}
    raw = payload.get("existingPageMap") or {}
    pages = raw.get("pages") if isinstance(raw, dict) else None
    page_map: dict[int, dict] = {}
    if isinstance(pages, list):
        for item in pages:
            if not isinstance(item, dict):
                continue
            try:
                idx = int(item.get("i"))
            except (TypeError, ValueError):
                continue
            page_map[idx] = {
                "kind": str(item.get("kind") or "other"),
                "codeHint": item.get("codeHint"),
            }
    if not page_map:
        return {"error": "no_map", "response": fail("补充识别需要已有页地图。请先全册识别。")}
    return {"page_map": page_map}


def gate(state: DrawingParseState) -> dict:
    if not API_KEY:
        return {"error": "no_key", "response": fail("【百炼密钥】未配置 DASHSCOPE_API_KEY。")}
    return {}


def collect(state: DrawingParseState) -> dict:
    payload = state.get("payload") or {}
    pdfs, rejected = collect_pdfs(payload)
    if not pdfs:
        detail = "；".join(rejected) if rejected else "files 为空"
        return {"error": "no_pdf", "rejected": rejected, "response": fail("没有可读的 PDF 文件。" + detail)}
    return {"pdfs": pdfs, "rejected": rejected, "primary": pdfs[0], "pdf_path": pdfs[0]["path"]}


def open_pdf(state: DrawingParseState) -> dict:
    path = state["pdf_path"]
    try:
        total = page_count(path)
    except Exception as exc:
        return {"error": "open_pdf", "response": fail("无法打开 PDF：" + str(exc)[:200])}
    if total <= 0:
        return {"error": "empty_pdf", "response": fail("PDF 没有页面。")}
    return {"total": total}


def render_short(state: DrawingParseState) -> dict:
    total = int(state["total"])
    path = state["pdf_path"]
    rendered = render_pages(path, FINE_DPI)
    picked = list(range(total))
    page_map = {i: {"kind": "general_layout", "codeHint": None} for i in picked}
    return {
        "rendered": rendered,
        "picked": picked,
        "page_map": page_map,
        "labels": "全部 %d 页" % total,
        "has_layout": True,
    }


def classify(state: DrawingParseState) -> dict:
    try:
        page_map = classify_all_pages(state["pdf_path"], int(state["total"]), state.get("payload") or {})
    except json.JSONDecodeError:
        return {"error": "json", "response": fail("模型没有返回合法 JSON。")}
    except (APIStatusError, AuthenticationError, RateLimitError) as exc:
        return {"error": "vl", "response": fail(classify_api_error(exc))}
    except Exception as exc:
        return {"error": "vl", "response": fail(classify_api_error(exc))}
    return {"page_map": page_map}


def pick_render(state: DrawingParseState) -> dict:
    page_map = state.get("page_map") or {}
    payload = state.get("payload") or {}
    focus = payload.get("focusKinds") if isinstance(payload.get("focusKinds"), list) else None
    already = _already_fine_indices(payload) if focus else set()
    picked = pick_fine_pages(page_map, focus, already)
    total = int(state["total"])
    has_layout = any(meta.get("kind") in LAYOUT_KINDS for meta in page_map.values())
    # 超过一批时留给 extract 分批现渲，避免一次塞十几张 JPEG
    rendered = []
    if picked and len(picked) <= MAX_FINE_PAGES:
        rendered = render_pages(state["pdf_path"], FINE_DPI, picked)
    if picked:
        labels = "细看第 %s 页（共 %d 页）" % (
            ",".join(str(i + 1) for i in picked),
            total,
        )
    else:
        labels = "页地图中没有可扩扫的结构页" if focus else "未找到竣工说明或总布置/立面页"
    return {
        "picked": picked,
        "rendered": rendered,
        "labels": labels,
        "has_layout": has_layout,
    }


def _merge_extract_results(base: dict, extra: dict) -> dict:
    """多分批细看时合并摘录。主梁/材料以前批为准（后批常是墩柱页，易把下部标号写成主梁）。联表按 seq 拼跨径与墩柱。"""
    merged = dict(base or {})
    extra = extra if isinstance(extra, dict) else {}
    for field in ("girderType", "layoutType", "material", "code", "region"):
        if extra.get(field) and not merged.get(field):
            merged[field] = extra.get(field)
    if extra.get("units"):
        merged["units"] = _merge_units(merged.get("units"), extra.get("units"))
    merged["extracts"] = _merge_extracts(merged.get("extracts"), extra.get("extracts") or [])
    notes = [str(merged.get("note") or "").strip(), str(extra.get("note") or "").strip()]
    merged["note"] = " ".join(n for n in notes if n) or None
    need = list(merged.get("needFiles") or [])
    for item in extra.get("needFiles") or []:
        if item not in need:
            need.append(item)
    if need:
        merged["needFiles"] = need
    merged["ok"] = True
    return merged


def _unit_seq(unit: dict) -> int | None:
    try:
        return int(unit.get("seq"))
    except (TypeError, ValueError):
        return None


def _spans_of(unit: dict) -> list:
    raw = unit.get("spansM") or unit.get("spans_m") or []
    return raw if isinstance(raw, list) else []


def _columns_have_height(support: dict) -> bool:
    for col in support.get("columns") or []:
        if not isinstance(col, dict):
            continue
        if col.get("heightM") is not None or col.get("height_m") is not None:
            return True
    return False


def _merge_supports(old, new) -> list:
    """按 seq 合并墩台；有柱高或柱数更多的一侧优先。"""
    by: dict[int, dict] = {}
    order: list[int] = []
    for src in (old or [], new or []):
        if not isinstance(src, list):
            continue
        for item in src:
            if not isinstance(item, dict):
                continue
            try:
                seq = int(item.get("seq"))
            except (TypeError, ValueError):
                seq = len(order)
            if seq not in by:
                by[seq] = dict(item)
                order.append(seq)
                continue
            cur = by[seq]
            new_cols = item.get("columns") or []
            old_cols = cur.get("columns") or []
            if _columns_have_height(item) and not _columns_have_height(cur):
                by[seq] = dict(item)
            elif len(new_cols) > len(old_cols) and not _columns_have_height(cur):
                by[seq] = dict(item)
            elif _columns_have_height(item) and _columns_have_height(cur) and len(new_cols) >= len(old_cols):
                by[seq] = dict(item)
    return [by[seq] for seq in order]


def _merge_units(old, new) -> list:
    """跨径以前批为准（后批墩柱页常缺跨或读错）；墩柱有高度或柱数则补上。"""
    by: dict[int, dict] = {}
    order: list[int] = []
    for src in (old or [], new or []):
        if not isinstance(src, list):
            continue
        for unit in src:
            if not isinstance(unit, dict):
                continue
            seq = _unit_seq(unit)
            if seq is None:
                continue
            if seq not in by:
                by[seq] = dict(unit)
                order.append(seq)
                continue
            cur = by[seq]
            incoming = _spans_of(unit)
            if incoming and not _spans_of(cur):
                cur["spansM"] = incoming
            if unit.get("supports"):
                cur["supports"] = _merge_supports(cur.get("supports"), unit.get("supports"))
    return [by[seq] for seq in order]


def extract(state: DrawingParseState) -> dict:
    payload = state.get("payload") or {}
    picked = list(state.get("picked") or [])
    rendered = state.get("rendered") or []
    path = state.get("pdf_path") or ""
    batches: list[tuple[list[int], list | None]] = []
    if rendered and len(picked) <= MAX_FINE_PAGES:
        batches.append((picked, rendered))
    elif picked:
        for start in range(0, len(picked), MAX_FINE_PAGES):
            batches.append((picked[start : start + MAX_FINE_PAGES], None))
    try:
        merged: dict | None = None
        if not batches:
            merged = {"ok": True, "units": [], "note": None}
        for batch, pre_rendered in batches:
            blobs = pre_rendered if pre_rendered is not None else render_pages(path, FINE_DPI, batch)
            labels = "细看第 %s 页（共 %d 页）" % (
                ",".join(str(i + 1) for i in batch),
                int(state.get("total") or 0),
            )
            result = chat_json(
                [blob for _, blob in blobs],
                build_extract_prompt(payload, labels),
            )
            if not isinstance(result, dict):
                return {"error": "shape", "response": fail("模型返回不是对象。")}
            if result.get("ok") is False:
                err = str(result.get("error") or "模型认为无法识别")
                if is_quota_message(err):
                    return {
                        "error": "quota",
                        "response": fail("【百炼额度】不足或已用尽，请到阿里云百炼控制台充值或开通模型额度。"),
                    }
                result["ok"] = True
                result["note"] = (result.get("note") or "") + " " + err
                if not result.get("units"):
                    result["units"] = []
            merged = result if merged is None else _merge_extract_results(merged, result)
            print("[python-agent] 细看 %s" % labels, flush=True)
    except json.JSONDecodeError:
        return {"error": "json", "response": fail("模型没有返回合法 JSON。")}
    except (APIStatusError, AuthenticationError, RateLimitError) as exc:
        return {"error": "vl", "response": fail(classify_api_error(exc))}
    except Exception as exc:
        return {"error": "vl", "response": fail(classify_api_error(exc))}

    # 页图只给本节点调 VL；清掉后 persist/finish 的 checkpoint 不再夹带 JPEG
    return {"extract_result": merged or {"ok": True, "units": []}, "rendered": []}


def persist(state: DrawingParseState) -> dict:
    result = state.get("extract_result") or {"ok": True, "units": []}
    has_layout = bool(state.get("has_layout"))
    gaps = collect_gaps(result, has_layout)
    picked = state.get("picked") or []
    total = int(state.get("total") or 0)
    extra = ""
    payload = state.get("payload") or {}
    primary = state.get("primary") or {}
    current_id = primary.get("fileId") if isinstance(primary, dict) else None
    catalog_by_id, already = catalog_index(payload, current_id)
    unread = sum(1 for fid in catalog_by_id if fid not in already)
    if unread:
        extra = " 图纸清单中还有 %d 份未打开 PDF；不够则填 needFiles.fileId（必须来自 drawingCatalog）。" % unread
    if not picked:
        if str(payload.get("mode") or "") == "supplement":
            extra += " 页地图中没有可扩扫的结构页（已跳过目录/钢筋/封面）。"
        elif total > MAX_FINE_PAGES:
            extra += " 全册粗看未发现竣工说明或总布置/立面，请补充图纸或手填跨径。"
    note = ((result.get("note") or "") + extra).strip() or None
    # extract 节点会清掉 JPEG；细看页号用 picked，不要依赖 rendered
    fine_pages = list(picked or [])
    extracts = normalize_extracts(result.get("extracts"))
    existing = payload.get("existingPageMap") if isinstance(payload.get("existingPageMap"), dict) else {}
    if existing.get("extracts"):
        extracts = _merge_extracts(existing.get("extracts"), extracts)
    for item in existing.get("fineRead") or []:
        try:
            idx = int(item) - 1
        except (TypeError, ValueError):
            continue
        if idx >= 0 and idx not in fine_pages:
            fine_pages.append(idx)
    page_map_body = build_page_map(
        state.get("payload") or {},
        state.get("primary") or {},
        total,
        state.get("page_map") or {},
        fine_pages,
        gaps,
        extracts,
    )
    need_files = resolve_need_files(payload, result, gaps, state.get("primary") or {})
    response = {
        "ok": True,
        "girderType": result.get("girderType"),
        "layoutType": result.get("layoutType"),
        "material": result.get("material"),
        "code": result.get("code"),
        "region": result.get("region"),
        "units": normalize_units(result.get("units") or []),
        "extracts": extracts,
        "note": note,
        "gaps": gaps,
        "hasLayoutPages": has_layout,
        "pageMap": page_map_body,
        "needFiles": need_files,
    }
    return {"gaps": gaps, "response": response}


def _merge_extracts(old, new: list[dict]) -> list[dict]:
    """补充识别保留旧摘录，按 key+label+page 去重，新值覆盖旧值。"""
    merged: dict[tuple, dict] = {}
    for item in list(old or []) + list(new or []):
        if not isinstance(item, dict):
            continue
        key = (
            str(item.get("key") or ""),
            str(item.get("label") or ""),
            str(item.get("page") or ""),
        )
        merged[key] = item
    return list(merged.values())


def finish(state: DrawingParseState) -> dict:
    if state.get("response"):
        return {}
    return {"response": fail(state.get("error") or "图执行未产生响应")}
