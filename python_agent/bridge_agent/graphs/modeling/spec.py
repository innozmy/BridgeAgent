"""由账本投影生成平面梁格规格。按 skills/modeling 切片写死，不调 VL、不启 SAP。

已锁定：每片梁单独杆、主梁同一 Z（不做纵坡）、kN·m、Linear Link、每只支座单独建。
K130 只学盖梁/系梁/Body/土弹簧/插入点/分标号；不抄脊骨、纵坡、N·mm、Wen、支座并联。
"""

from __future__ import annotations

import re
from typing import Any

from bridge_agent.graphs.modeling.check import _is_abutment, _is_rigid, _num, _params
from bridge_agent.skills.loader import modeling_skill


def _spans(unit: dict) -> list[float]:
    raw = unit.get("spansM")
    if isinstance(raw, str):
        import json
        try:
            raw = json.loads(raw)
        except Exception:
            return []
    out = []
    if isinstance(raw, list):
        for item in raw:
            n = _num(str(item))
            if n and n > 0:
                out.append(n)
    return out


def _split(length: float, cap: float) -> list[float]:
    if length <= 0:
        return []
    n = max(1, int((length - 1e-9) / cap) + 1)
    step = length / n
    return [step] * n


def _conc_e(material: str) -> float:
    """砼弹性模量 kN/m²。"""
    digits = "".join(ch for ch in (material or "") if ch.isdigit())
    grade = int(digits) if digits else 50
    table = {30: 3.00e7, 40: 3.25e7, 45: 3.35e7, 50: 3.45e7, 55: 3.55e7, 60: 3.60e7}
    return table.get(grade, 3.45e7)


def _support_tag(support: dict, seq: int) -> str:
    """墩台号：0# → P0 / ABUT0，与账本 code 对齐。"""
    code = str(support.get("code") or "").replace("#", "").replace("号", "").strip()
    if _is_abutment(support):
        return "ABUT" + (code or str(seq))
    if code.isdigit():
        return "P" + code
    return "P" + (code or str(seq))


def _cap_size(params: dict[str, str]) -> tuple[float, float] | None:
    h = _num(params.get("capBeamHeightM"))
    w = _num(params.get("capBeamWidthM"))
    if h and w and h > 0 and w > 0:
        return float(h), float(w)
    return None


def _tie_size(params: dict[str, str]) -> tuple[float, float] | None:
    text = (params.get("tieBeam") or "").strip()
    if text in ("无", "没有", "无系梁"):
        return None
    h = _num(params.get("tieBeamHeightM"))
    w = _num(params.get("tieBeamWidthM"))
    if h and w and h > 0 and w > 0:
        return float(h), float(w)
    return None


def _is_gjzf(text: str, abutment: bool) -> bool:
    """型号同时含 GJZ 与 GJZF 时：台用四氟、墩用普通板式（K130 案例，非所有桥）。"""
    raw = (text or "").upper().replace(" ", "")
    has_f = "GJZF" in raw or "四氟" in (text or "") or "滑板" in (text or "")
    has_j = "GJZ" in raw.replace("GJZF", " ")
    if has_f and has_j:
        return abutment
    return has_f


def _bearing_grid(params: dict[str, str]) -> tuple[int, int, int]:
    """每片：排数、每排只数、总只数。只拆开建，禁止并联合成 1 个 Link。"""
    layout = params.get("bearingLayout") or ""
    n = _num(params.get("bearingCountPerGirder"))
    rows = 1
    if any(word in layout for word in ("三排", "3排")):
        rows = 3
    elif any(word in layout for word in ("两排", "二排", "2排")):
        rows = 2
    total = int(n) if n and n > 0 else None
    if total is None:
        found = re.search(r"每片\s*(\d+)", layout)
        if found:
            total = int(found.group(1))
    if total is None:
        total = 1
    rows = min(rows, total)
    per_row = max(1, (total + rows - 1) // rows)
    return rows, per_row, total


def _soil_m(params: dict[str, str]) -> float | None:
    m = _num(params.get("soilM"))
    return float(m) if m and m > 0 else None


def _pile_b1(diam: float) -> float:
    """JTG 3363 附录 L 单桩计算宽度；第一版 k=1。"""
    kf = 0.9
    k = 1.0
    if diam >= 1.0:
        return min(k * kf * (diam + 1.0), 2.0 * diam)
    return min(k * kf * (1.5 * diam + 0.5), 2.0 * diam)


def build_spec(payload: dict[str, Any], overlay: dict | None = None) -> dict[str, Any]:
    # 确认切片在盘上可读；真正执行的是下面的代码，不是把 markdown 塞给大模型
    modeling_skill(
        "geometry.md", "sections.md", "bearings.md", "constraints.md",
        "soil.md", "loads.md", "naming.md",
    )
    ledger = payload.get("ledger") if isinstance(payload.get("ledger"), dict) else {}
    params = _params(ledger)
    overlay = overlay if isinstance(overlay, dict) else {}
    flags = overlay.get("flags") if isinstance(overlay.get("flags"), dict) else {}
    overrides = overlay.get("overrides") if isinstance(overlay.get("overrides"), dict) else {}
    notes: list[str] = []
    gaps: list[str] = []
    girder_n = int(_num(params.get("girderCount")) or 1)
    spacing = float(_num(params.get("girderSpacingM")) or 2.2)
    gh = float(_num(params.get("girderHeightM")) or _num(params.get("girderHeightAtMidspanM")) or 1.8)
    gw = float(
        _num(params.get("innerGirderWidthM"))
        or _num(params.get("edgeGirderWidthM"))
        or _num(params.get("girderBottomWidthM"))
        or 1.7
    )
    slab = _num(params.get("deckSlabThickM")) or _num(params.get("concreteOverlayThickM"))
    if slab is None:
        slab = 0.18
        notes.append("虚横梁板厚缺省 0.18 m estimated")
    joint = _num(params.get("expansionJointM"))
    if joint is None:
        joint = 0.08
        notes.append("联间缝 0.08 m estimated")

    material = str(ledger.get("material") or "C50")
    pier_mat = str(params.get("pierMaterial") or "").strip() or material
    if pier_mat == material:
        notes.append("pierMaterialFromGirder")
    e = _conc_e(material)
    e_pier = _conc_e(pier_mat)

    thick = float(_num(params.get("bearingRubberThickM")) or 0.063)
    size = (params.get("bearingSize") or "").strip()
    g_mpa = _num(params.get("bearingG"))
    if g_mpa is None:
        g_mpa = 1.0
        notes.append("支座 G=1.0 MPa estimated")
    if not size:
        notes.append("无支座平面尺寸：水平刚度用约定剪切面积 estimated")
    notes.append("板式支座按 Link 建，Kz=1e8 kN/m，转动放开；Linear，不用 Wen")
    notes.append("主梁每片单独杆系，节点同一 Z；忽略纵坡；单位 kN·m")
    br_rows, br_per, br_total = _bearing_grid(params)
    if br_total <= 1:
        notes.append("账本未写清每片几块时每片 1 只 Link；禁止把多只并成一个")
    else:
        notes.append("每片 %s 只支座各建 Link（%s 排），不合并刚度" % (br_total, br_rows))

    cap = _cap_size(params)
    tie = _tie_size(params)
    soil_m = _soil_m(params)
    overlay_m = _num(overrides.get("soilM"))
    # 账本已有 m 值禁止被规范估算覆盖；只补软缺口
    if soil_m is None and overlay_m and overlay_m > 0:
        soil_m = float(overlay_m)
        notes.append("soilM 来自规范估算，需核对")
    elif soil_m is None:
        gaps.append("soilM")
        notes.append("全桥无土弹簧 m，桩底仍固结")

    sdl = _num(params.get("sdlKNPerM"))
    sdl_per_girder = 0.0
    if sdl and sdl > 0 and girder_n > 0:
        # kN/m ÷ g → t/m，再按片数均分
        sdl_per_girder = float(sdl) / 9.806 / girder_n
        notes.append("sdlSplitByGirderCount")
    else:
        gaps.append("sdlKNPerM")
        notes.append("二期缺失，不加线质量")

    joints: list[dict] = []
    frames: list[dict] = []
    links: list[dict] = []
    constraints: list[dict] = []
    jid = 1
    names_used: set[str] = set()

    def add_joint(x: float, y: float, z: float, name: str, extra: dict | None = None) -> int:
        nonlocal jid
        raw = name[:24]
        tag = raw
        n = 2
        while tag in names_used:
            tag = (raw[:20] + "-" + str(n))[:24]
            n += 1
        names_used.add(tag)
        nid = jid
        row = {"id": nid, "name": tag, "x": round(x, 4), "y": round(y, 4), "z": round(z, 4)}
        if extra:
            row.update(extra)
        joints.append(row)
        jid += 1
        return nid

    def add_frame(a: int, b: int, prop: str, kind: str, name: str, **kw: Any) -> None:
        row = {"i": a, "j": b, "name": name[:24], "prop": prop, "kind": kind}
        row.update(kw)
        frames.append(row)

    ys = [((i - (girder_n - 1) / 2.0) * spacing) for i in range(girder_n)]
    girder_lines: list[list[int]] = [[] for _ in ys]
    units = [u for u in (ledger.get("units") or []) if isinstance(u, dict)]

    # 每联支承 X（含台）；主梁按 ≤3 m 划分
    support_plan: list[dict] = []
    x_cursor = 0.0
    for ui, unit in enumerate(units):
        spans = _spans(unit)
        if ui > 0:
            x_cursor += float(joint)
        xs = [x_cursor]
        local = 0.0
        for si, span in enumerate(spans):
            for seg in _split(span, 3.0):
                local += seg
                xs.append(x_cursor + local)
        supports = [s for s in (unit.get("supports") or []) if isinstance(s, dict)]
        acc = 0.0
        sx_list = [x_cursor]
        for span in spans:
            acc += span
            sx_list.append(x_cursor + acc)
        for si, support in enumerate(supports):
            sx = sx_list[si] if si < len(sx_list) else sx_list[-1]
            support_plan.append({
                "x": sx,
                "unit": ui + 1,
                "support": support,
                "seq": si,
                "tag": _support_tag(support, si),
                "nspans": len(spans),
            })
        for gi, y in enumerate(ys):
            ids = []
            for pi, x in enumerate(xs):
                # 跨号：按累计 X 落在哪一跨
                span_i = 1
                run = 0.0
                for k, span in enumerate(spans, start=1):
                    run += span
                    if x <= x_cursor + run + 1e-6:
                        span_i = k
                        break
                ids.append(add_joint(
                    x, y, 0.0,
                    "J-U%s-K%s-G%s-%03d" % (ui + 1, span_i, gi + 1, pi + 1),
                ))
            for fi, (a, b) in enumerate(zip(ids, ids[1:])):
                add_frame(
                    a, b, "GIRDER", "girder",
                    "F-GIR-U%s-G%s-%03d" % (ui + 1, gi + 1, fi + 1),
                    insert=8, massPerLen=sdl_per_girder,
                )
            girder_lines[gi].extend(ids if not girder_lines[gi] else ids[1:])
        x_cursor += sum(spans)

    if girder_n >= 2 and girder_lines and girder_lines[0]:
        npts = min(len(line) for line in girder_lines)
        for pi in range(npts):
            for gi in range(girder_n - 1):
                add_frame(
                    girder_lines[gi][pi], girder_lines[gi + 1][pi], "DUMMY", "dummy",
                    "F-VTB-%03d-%s" % (pi + 1, gi + 1),
                    weightless=True,
                )

    # 梁底高程；盖梁顶在支座底
    z_brg_top = -gh
    z_brg_bot = -gh - thick
    cap_h = cap[0] if cap else 0.0
    z_col_top = z_brg_bot - cap_h
    pier_d = float(_num(params.get("pierDiameterM")) or _num(params.get("pierSectionB")) or 1.2)
    pile_d = float(_num(params.get("pileDiameterM")) or 1.2)
    pile_l = _num(params.get("pileLengthM"))
    b1 = _pile_b1(pile_d)

    def place_bearings(
        sx: float,
        tag: str,
        link_prop: str,
        cap_ids: list[int],
        col_top_ids: list[int],
        restrain_bot: bool,
    ) -> None:
        """每片每只一个 Link。多排沿顺桥错开，多只沿横桥错开，不把刚度加总到一只。"""
        dx = 0.40 if br_rows > 1 else 0.0
        dy = 0.25 if br_per > 1 else 0.0
        for gi, y0 in enumerate(ys):
            for ri in range(br_rows):
                x = sx + (ri - (br_rows - 1) / 2.0) * dx
                for ni in range(br_per):
                    y = y0 + (ni - (br_per - 1) / 2.0) * dy
                    suffix = "G%s-R%s-N%s" % (gi + 1, ri + 1, ni + 1)
                    extra = {"restraint": [True, True, True, True, True, True]} if restrain_bot else None
                    bot = add_joint(x, y, z_brg_bot, "J-%s-%s-D" % (tag, suffix), extra)
                    if cap_ids:
                        near = min(cap_ids, key=lambda jid_: abs(joints[jid_ - 1]["y"] - y))
                        constraints.append({
                            "name": "C-BRGCAP-%s-%s" % (tag, suffix),
                            "joints": [near, bot],
                        })
                    elif col_top_ids:
                        near = min(col_top_ids, key=lambda jid_: abs(joints[jid_ - 1]["y"] - y))
                        constraints.append({
                            "name": "C-BRGCOL-%s-%s" % (tag, suffix),
                            "joints": [near, bot],
                        })
                    top = add_joint(x, y, z_brg_top, "J-%s-%s-T" % (tag, suffix))
                    links.append({
                        "i": bot, "j": top,
                        "name": "L-BRG-%s-%s" % (tag, suffix),
                        "prop": link_prop,
                    })
                    gir_id = _nearest_girder_joint(girder_lines, gi, x, joints)
                    constraints.append({
                        "name": "C-GIRBRG-%s-%s" % (tag, suffix),
                        "joints": [gir_id, top],
                    })

    for sp in support_plan:
        sx = float(sp["x"])
        support = sp["support"]
        tag = sp["tag"]
        abutment = _is_abutment(support)
        cols = [c for c in (support.get("columns") or []) if isinstance(c, dict)]
        gjzf = _is_gjzf(params.get("bearingType") or "", abutment)
        if abutment and flags.get("abutmentGjzf") is True:
            gjzf = True
            notes.append("桥台 GJZF 来自规范判别，需核对")
        elif abutment and flags.get("abutmentGjzf") is False:
            gjzf = False
        skip_links = (not abutment) and (
            flags.get("skipBearingWhereRigid") is True or _is_rigid(params)
        )
        link_prop = "GJZF" if gjzf else "GJZ"

        col_top_ids: list[int] = []
        col_ys: list[float] = []
        if abutment or not cols:
            # 不建台身：固定点 ← 每只 Link ← 梁底
            place_bearings(sx, tag, link_prop, [], [], True)
            continue

        n_col = max(len(cols), 1)
        for ci, col in enumerate(cols):
            y = ys[ci] if ci < len(ys) else (ys[-1] if ys else 0.0)
            if n_col == 1 and girder_n > 1:
                y = 0.0
            elif n_col > 1:
                y = ((ci - (n_col - 1) / 2.0) * spacing) if n_col == girder_n else (
                    ys[0] + ci * (ys[-1] - ys[0]) / max(n_col - 1, 1)
                )
            ch = float(_num(str(col.get("heightM") or "")) or 8.0)
            top = add_joint(sx, y, z_col_top, "J-%s-C%s-Z00" % (tag, ci + 1))
            col_top_ids.append(top)
            col_ys.append(y)
            z = z_col_top
            prev = top
            pile_nodes: list[tuple[int, float]] = []
            for segi, seg in enumerate(_split(ch, 2.0), start=1):
                z -= seg
                nxt = add_joint(sx, y, z, "J-%s-C%s-Z%02d" % (tag, ci + 1, segi))
                add_frame(prev, nxt, "PIER", "pier", "F-COL-%s-C%s-%02d" % (tag, ci + 1, segi))
                prev = nxt
            if pile_l:
                pz = z
                pprev = prev
                segs = _split(float(pile_l), 2.0)
                for segi, seg in enumerate(segs, start=1):
                    z_mid = pz - seg / 2.0
                    pz -= seg
                    nxt = add_joint(sx, y, pz, "J-%s-PL%s-%03d" % (tag, ci + 1, segi))
                    add_frame(pprev, nxt, "PILE", "pile", "F-PIL-%s-C%s-%02d" % (tag, ci + 1, segi))
                    pile_nodes.append((nxt, abs(z_col_top - ch - pz)))  # 深度从桩顶起
                    pprev = nxt
                joints[-1]["restraint"] = [True, True, True, True, True, True]
                if soil_m and pile_nodes:
                    for nid, depth in pile_nodes:
                        dh = float(pile_l) / len(pile_nodes)
                        kh = soil_m * max(depth, dh / 2.0) * b1 * dh
                        joints[nid - 1]["spring"] = [kh, kh, 0.0, 0.0, 0.0, 0.0]

        # 盖梁：横桥向连柱顶，并伸到边梁 Y
        cap_ids: list[int] = []
        if cap and col_top_ids:
            z_cap = z_col_top + cap_h / 2.0
            y_cap = sorted(set(col_ys + ys))
            for yi, y in enumerate(y_cap):
                cap_ids.append(add_joint(sx, y, z_cap, "J-%s-CAP%s" % (tag, yi + 1)))
            for fi, (a, b) in enumerate(zip(cap_ids, cap_ids[1:])):
                add_frame(a, b, "CAP", "cap", "F-CAP-%s-%s" % (tag, fi + 1))
            for ci, tid in enumerate(col_top_ids):
                near = min(cap_ids, key=lambda jid_: abs(joints[jid_ - 1]["y"] - col_ys[ci]))
                constraints.append({
                    "name": "C-CAP-%s-C%s" % (tag, ci + 1),
                    "joints": [tid, near],
                })

        if tie and len(col_top_ids) >= 2:
            z_tie = z_col_top - float(_num(str((cols[0] or {}).get("heightM") or "8")) or 8.0) / 2.0
            tie_ids = []
            for ci, y in enumerate(col_ys):
                tie_ids.append(add_joint(sx, y, z_tie, "J-%s-TIE%s" % (tag, ci + 1)))
            for fi, (a, b) in enumerate(zip(tie_ids, tie_ids[1:])):
                add_frame(a, b, "TIE", "tie", "F-TIE-%s-%s" % (tag, fi + 1))

        if skip_links:
            notes.append("%s 刚构/规范判别：不建支座 Link，盖梁或柱顶与主梁 Body" % tag)
            anchors = cap_ids or col_top_ids
            if anchors:
                for gi, y0 in enumerate(ys):
                    gir_id = _nearest_girder_joint(girder_lines, gi, sx, joints)
                    near = min(anchors, key=lambda jid_: abs(joints[jid_ - 1]["y"] - y0))
                    constraints.append({
                        "name": "C-RIG-%s-G%s" % (tag, gi + 1),
                        "joints": [near, gir_id],
                    })
        else:
            place_bearings(sx, tag, link_prop, cap_ids, col_top_ids, False)

    if not cap:
        notes.append("账本无盖梁尺寸：支座底连柱顶，未编标准盖梁")
    if not tie:
        notes.append("账本无系梁尺寸：未建系梁")
    for item in overlay.get("notices") or []:
        if isinstance(item, dict) and item.get("text"):
            cite = str(item.get("citation") or "").strip()
            notes.append((cite + " " if cite else "") + str(item.get("text")))

    return {
        "material": material,
        "pierMaterial": pier_mat,
        "E": e,
        "EPier": e_pier,
        "girder": {"h": gh, "w": gw, "count": girder_n, "spacing": spacing},
        "dummyH": float(slab),
        "dummyB": 3.0,
        "pier": {"d": pier_d},
        "pileD": pile_d,
        "cap": {"h": cap[0], "w": cap[1]} if cap else None,
        "tie": {"h": tie[0], "w": tie[1]} if tie else None,
        "bearing": {"thick": thick, "size": size, "G": float(g_mpa)},
        "joints": joints,
        "frames": frames,
        "links": links,
        "constraints": constraints,
        "notes": notes,
        "gaps": gaps,
    }


def _nearest_girder_joint(girder_lines: list[list[int]], gi: int, x: float, joints: list[dict]) -> int:
    line = girder_lines[gi] if gi < len(girder_lines) else (girder_lines[-1] if girder_lines else [])
    if not line:
        return 1
    by_id = {j["id"]: j for j in joints}
    return min(line, key=lambda jid: abs(float(by_id[jid]["x"]) - x))
