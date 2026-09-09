"""规范覆盖层白名单。spec 只消费这些字段；非法键丢弃。"""

from __future__ import annotations

from bridge_agent.graphs.modeling.check import _num

FLAG_KEYS = ("abutmentGjzf", "skipBearingWhereRigid", "requireCapBeam")
OVERRIDE_KEYS = ("soilM",)


def empty_overlay() -> dict:
    return {"overrides": {}, "flags": {}, "notices": []}


def sanitize_overlay(raw) -> dict:
    """只保留白名单；数字须能解析为正值。"""
    out = empty_overlay()
    if not isinstance(raw, dict):
        return out
    overrides = raw.get("overrides") if isinstance(raw.get("overrides"), dict) else {}
    for key in OVERRIDE_KEYS:
        value = _num(overrides.get(key))
        if value is not None and value > 0:
            out["overrides"][key] = value
    flags = raw.get("flags") if isinstance(raw.get("flags"), dict) else {}
    for key in FLAG_KEYS:
        bit = flags.get(key)
        if isinstance(bit, bool):
            out["flags"][key] = bit
        elif bit in (0, 1, "true", "false", "True", "False"):
            out["flags"][key] = bit in (True, 1, "true", "True")
    notices = raw.get("notices") if isinstance(raw.get("notices"), list) else []
    for item in notices:
        if not isinstance(item, dict):
            continue
        text = str(item.get("text") or "").strip()
        if not text:
            continue
        out["notices"].append({
            "part": str(item.get("part") or "")[:32],
            "citation": str(item.get("citation") or "")[:80],
            "text": text[:400],
            "severity": "warn" if item.get("severity") == "warn" else "info",
        })
    return out


def merge_overlay(base: dict, extra: dict) -> dict:
    merged = sanitize_overlay(base)
    add = sanitize_overlay(extra)
    merged["overrides"].update(add["overrides"])
    merged["flags"].update(add["flags"])
    merged["notices"].extend(add["notices"])
    return merged
