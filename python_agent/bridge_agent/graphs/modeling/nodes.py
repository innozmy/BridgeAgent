"""建模图节点：gate → check → consult → spec → build → finish。COM 不进模型可选清单。"""

from __future__ import annotations

from bridge_agent.graphs.modeling.check import check_ledger, pick_need_supplement
from bridge_agent.graphs.modeling.consult import run_consult
from bridge_agent.graphs.modeling.overlay import empty_overlay
from bridge_agent.graphs.modeling.sap_com import build_sdb
from bridge_agent.graphs.modeling.spec import build_spec
from bridge_agent.graphs.modeling.state import ModelingState
from bridge_agent.runtime import fail
from bridge_agent.settings import API_KEY


def ok_or_end(state: ModelingState) -> str:
    if state.get("error") or (state.get("response") and not state.get("response", {}).get("ok")):
        return "end"
    return "ok"


def after_check(state: ModelingState) -> str:
    if state.get("error") or (state.get("response") and not state.get("response", {}).get("ok")):
        return "end"
    if state.get("response"):
        return "end"
    return "ok"


def gate(state: ModelingState) -> dict:
    """密钥与 sap 输出路径；不通过则直接失败，不开 SAP。"""
    if not API_KEY:
        return {"error": "no_key", "response": fail("【百炼密钥】未配置 DASHSCOPE_API_KEY。")}
    payload = state.get("payload") or {}
    if not isinstance(payload.get("ledger"), dict):
        return {"error": "no_ledger", "response": fail("建模注入包缺少账本。")}
    sap = payload.get("sap") if isinstance(payload.get("sap"), dict) else {}
    if not str(sap.get("sapOutputPath") or "").strip():
        return {"error": "no_sap_path", "response": fail("【SAP2000】Spring 未给 sapOutputPath。")}
    return {}


def check_node(state: ModelingState) -> dict:
    """硬缺口则返回 needSupplement，本 HTTP 结束；通过才进 consult。"""
    payload = state.get("payload") or {}
    try:
        checked = check_ledger(payload)
    except Exception as exc:
        return {"error": "check", "response": fail("建模 check 异常：" + str(exc)[:400])}
    missing = checked.get("missing") or []
    if not missing:
        return {"missing": []}
    need = pick_need_supplement(payload, missing)
    body = {
        "ok": True,
        "ready": False,
        "missingHard": missing,
        "needSupplement": need,
    }
    if need is None:
        reasons = "；".join(str(item.get("reason") or item.get("key")) for item in missing)
        body["ok"] = False
        body["error"] = (
            "仍缺：" + reasons
            + "。已对项目内 PDF 按相关页类扩扫仍读不出。请上传对应专页 PDF，或在概览参数袋手填。"
        )
        body["code"] = "NO_PAGE"
    return {"missing": missing, "response": body}


def consult_node(state: ModelingState) -> dict:
    """规范覆盖层；失败不挡开建，走空 overlay + notice。"""
    payload = state.get("payload") or {}
    try:
        overlay = run_consult(payload)
    except Exception as exc:
        overlay = empty_overlay()
        overlay["notices"].append({
            "part": "consult",
            "citation": "",
            "text": "规范咨询失败，软参数走代码缺省：" + str(exc)[:200],
            "severity": "warn",
        })
    return {"code_overlay": overlay}


def spec_node(state: ModelingState) -> dict:
    """账本 + 白名单 overlay 生成梁格；拓扑仍写死。"""
    payload = state.get("payload") or {}
    overlay = state.get("code_overlay") or empty_overlay()
    try:
        spec = build_spec(payload, overlay)
    except Exception as exc:
        return {"error": "spec", "response": fail("建模 spec 异常：" + str(exc)[:400])}
    return {"spec": spec}


def build_node(state: ModelingState) -> dict:
    """SAP COM 写盘；notices 并入响应，不写账本。"""
    payload = state.get("payload") or {}
    spec = state.get("spec") or {}
    overlay = state.get("code_overlay") or empty_overlay()
    try:
        built = build_sdb(spec, payload)
    except Exception as exc:
        return {"error": "build", "response": fail("建模 build 异常：" + str(exc)[:400])}
    if not built.get("ok"):
        return {"response": built}
    notices = overlay.get("notices") or []
    extra = "；".join(
        str(item.get("text") or "") for item in notices if isinstance(item, dict) and item.get("text")
    )
    note = str(built.get("note") or spec.get("notes") or "")
    if extra:
        note = (note + "；" if note else "") + extra
    built["ready"] = True
    built["missingHard"] = []
    built["needSupplement"] = None
    built["gaps"] = spec.get("gaps") or []
    built["notices"] = notices
    built["note"] = note
    return {"response": built}


def finish(state: ModelingState) -> dict:
    if state.get("response"):
        return {}
    return {"response": fail(state.get("error") or "图执行未产生响应")}
