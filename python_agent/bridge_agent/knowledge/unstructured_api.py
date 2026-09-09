"""调用 Unstructured Transform jobs：hi_res 分区、抽表 HTML、裁图 Base64。"""

from __future__ import annotations

import json
import time
import zipfile
from io import BytesIO
from pathlib import Path

import httpx

from bridge_agent.settings import (
    UNSTRUCTURED_API_KEY,
    UNSTRUCTURED_API_URL,
    UNSTRUCTURED_JOB_TIMEOUT_SECONDS,
)

_PARTITION_NODE = {
    "name": "Partitioner",
    "type": "partition",
    "subtype": "unstructured_api",
    "settings": {
        "strategy": "hi_res",
        "include_page_breaks": True,
        "pdf_infer_table_structure": True,
        "infer_table_structure": True,
        "ocr_languages": ["chi_sim", "eng"],
        "extract_image_block_types": ["Image"],
        "coordinates": True,
    },
}


def partition_pdf(pdf_path: Path) -> list[dict]:
    """上传一本 PDF，等到 job 完成，返回 Unstructured 元素列表（可含 image_base64）。"""
    if not UNSTRUCTURED_API_KEY:
        raise RuntimeError("未配置 UNSTRUCTURED_API_KEY")
    headers = {
        "unstructured-api-key": UNSTRUCTURED_API_KEY,
        "accept": "application/json",
    }
    job_id = _create_job(pdf_path, headers)
    info = _wait_job(job_id, headers)
    raw = _download_output(job_id, info, headers)
    return _parse_elements(raw)


def _create_job(pdf_path: Path, headers: dict) -> str:
    url = f"{UNSTRUCTURED_API_URL}/jobs/"
    request_data = json.dumps({"job_nodes": [_PARTITION_NODE]}, ensure_ascii=False)
    with pdf_path.open("rb") as handle:
        files = {
            "input_files": (pdf_path.name, handle, "application/pdf"),
        }
        data = {"request_data": request_data}
        with httpx.Client(timeout=120.0) as client:
            response = client.post(url, headers=headers, data=data, files=files)
    if response.status_code >= 400:
        raise RuntimeError(
            "Unstructured 创建任务失败 HTTP %s %s"
            % (response.status_code, response.text[:500])
        )
    body = response.json()
    job_id = body.get("id") or (body.get("job") or {}).get("id")
    if not job_id:
        raise RuntimeError("Unstructured 未返回 job id：" + json.dumps(body, ensure_ascii=False)[:400])
    return str(job_id)


def _wait_job(job_id: str, headers: dict) -> dict:
    url = f"{UNSTRUCTURED_API_URL}/jobs/{job_id}"
    deadline = time.time() + UNSTRUCTURED_JOB_TIMEOUT_SECONDS
    last = {}
    with httpx.Client(timeout=60.0) as client:
        while time.time() < deadline:
            response = client.get(url, headers=headers)
            if response.status_code >= 400:
                raise RuntimeError(
                    "Unstructured 查询任务失败 HTTP %s %s"
                    % (response.status_code, response.text[:400])
                )
            last = response.json()
            status = str(last.get("status") or "").upper()
            if status == "COMPLETED":
                return last
            if status in {"FAILED", "STOPPED"}:
                raise RuntimeError("Unstructured 任务 %s：%s" % (status, json.dumps(last, ensure_ascii=False)[:400]))
            time.sleep(5)
    raise RuntimeError("Unstructured 任务超时 job_id=%s last=%s" % (job_id, last.get("status")))


def _download_output(job_id: str, info: dict, headers: dict) -> bytes:
    file_id = _pick_file_id(info)
    params = {}
    if file_id:
        params["file_id"] = file_id
    url = f"{UNSTRUCTURED_API_URL}/jobs/{job_id}/download"
    with httpx.Client(timeout=180.0) as client:
        response = client.get(url, headers=headers, params=params)
    if response.status_code >= 400:
        raise RuntimeError(
            "Unstructured 下载结果失败 HTTP %s %s"
            % (response.status_code, response.text[:400])
        )
    return response.content


def _pick_file_id(info: dict) -> str | None:
    files = info.get("output_node_files") or []
    if isinstance(files, list) and files:
        first = files[0] if isinstance(files[0], dict) else {}
        file_id = first.get("file_id") or first.get("id")
        if file_id:
            return str(file_id)
    inputs = info.get("input_file_ids") or []
    if isinstance(inputs, list) and inputs:
        return str(inputs[0])
    return None


def _parse_elements(raw: bytes) -> list[dict]:
    payload = _load_json_bytes(raw)
    if isinstance(payload, list):
        return [item for item in payload if isinstance(item, dict)]
    if isinstance(payload, dict):
        for key in ("elements", "data", "output"):
            value = payload.get(key)
            if isinstance(value, list):
                return [item for item in value if isinstance(item, dict)]
    raise RuntimeError("Unstructured 输出不是元素列表")


def _load_json_bytes(raw: bytes) -> object:
    if raw[:2] == b"PK":
        with zipfile.ZipFile(BytesIO(raw)) as archive:
            names = [name for name in archive.namelist() if name.endswith(".json")]
            if not names:
                raise RuntimeError("Unstructured zip 里没有 JSON")
            raw = archive.read(names[0])
    text = raw.decode("utf-8", errors="replace").lstrip("\ufeff")
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        rows = []
        for line in text.splitlines():
            line = line.strip()
            if not line:
                continue
            rows.append(json.loads(line))
        if rows:
            return rows
        raise
