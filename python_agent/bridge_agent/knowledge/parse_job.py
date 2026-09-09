"""知识解析作业：Unstructured 拆 PDF → parse/ sidecar + 附图千问 + 图/表目录。不写 merge/。"""

from __future__ import annotations

from pathlib import Path

from bridge_agent.knowledge.catalog import write_parse_indexes
from bridge_agent.knowledge.figures import (
    count_types,
    materialize,
    recognize_pending_figures,
    wipe_outputs,
)
from bridge_agent.knowledge.sidecar import can_resume, empty_sidecar, load_sidecar, write_sidecar
from bridge_agent.knowledge.unstructured_api import partition_pdf
from bridge_agent.settings import UNSTRUCTURED_API_KEY


def run_parse(payload: dict) -> dict:
    pdf_path = _abs(payload.get("pdfPath"))
    elements_path = _abs(payload.get("elementsPath"))
    figures_dir = _abs(payload.get("figuresDir"))
    if pdf_path is None or elements_path is None or figures_dir is None:
        return _fail("缺少 pdfPath / elementsPath / figuresDir")
    if not pdf_path.is_file():
        return _fail("PDF 不在磁盘上")
    parse_dir = _abs(payload.get("parseDir"))
    if parse_dir is None and elements_path.parent.name == "parse":
        parse_dir = elements_path.parent
    if parse_dir is None or parse_dir.name != "parse":
        return _fail("缺少 parseDir，且必须是名为 parse 的目录")

    force = bool(payload.get("force"))
    name = str(payload.get("name") or "")
    family_code = str(payload.get("familyCode") or "")
    sha256 = str(payload.get("sha256") or pdf_path.stem)
    warnings: list[str] = []
    resumed = False

    if force:
        wipe_outputs(elements_path, figures_dir)

    sidecar = None if force else load_sidecar(elements_path)
    if can_resume(sidecar):
        resumed = True
    else:
        # 旧瘦 sidecar 或强制重跑：只清 parse/，不动 source.pdf 与 merge/
        if sidecar is not None:
            wipe_outputs(elements_path, figures_dir)
        if not UNSTRUCTURED_API_KEY:
            return _fail("未配置 UNSTRUCTURED_API_KEY")
        try:
            raw = partition_pdf(pdf_path)
        except Exception as exc:
            return _fail("Unstructured 解析失败：" + str(exc)[:500])
        sidecar = empty_sidecar(
            {
                "documentId": payload.get("documentId"),
                "sha256": sha256,
                "name": name,
                "familyCode": family_code,
                "unstructured": {
                    "strategy": "hi_res",
                    "languages": ["chi_sim", "eng"],
                    "inferTableStructure": True,
                },
            }
        )
        sidecar = materialize(
            raw,
            sidecar=sidecar,
            elements_path=elements_path,
            figures_dir=figures_dir,
            sha256=sha256,
        )

    recognize_pending_figures(
        sidecar,
        elements_path=elements_path,
        figures_dir=figures_dir,
        name=name,
        family_code=family_code,
    )
    sidecar.setdefault("warnings", [])
    sidecar["warnings"].extend(warnings)
    write_sidecar(elements_path, sidecar)
    index_counts = write_parse_indexes(sidecar, parse_dir)

    summary = count_types(sidecar)
    summary["ok"] = True
    summary["resumed"] = resumed
    summary["warnings"] = sidecar.get("warnings") or []
    summary.update(index_counts)
    return summary


def _abs(value) -> Path | None:
    text = str(value or "").strip()
    if not text:
        return None
    return Path(text)


def _fail(error: str) -> dict:
    return {"ok": False, "error": error}
