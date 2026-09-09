"""给 Spring 的薄 HTTP。业务在 LangGraph；本文件只做路由与端口独占。"""

from __future__ import annotations

import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from importlib.metadata import version as pkg_version

from bridge_agent.graphs.registry import AGENT_KINDS, run_agent
from bridge_agent.knowledge.embed_job import run_drop_vectors, run_embed
from bridge_agent.knowledge.merge_job import run_merge
from bridge_agent.knowledge.parse_job import run_parse
from bridge_agent.knowledge.search_job import run_search
from bridge_agent.knowledge.split_job import run_split
from bridge_agent.runtime import fail
from bridge_agent.settings import API_KEY, HOST, MODEL, PORT, UNSTRUCTURED_API_KEY
from bridge_agent.tools.drawings import file_name


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != "/health":
            self.send_error(404)
            return
        self._json(
            200,
            {
                "ok": True,
                "service": "python-agent",
                "runtime": "langgraph",
                "langgraphVersion": pkg_version("langgraph"),
                "pythonVersion": sys.version.split()[0],
                "agents": list(AGENT_KINDS),
                "model": MODEL,
                "keyConfigured": bool(API_KEY),
                "unstructuredKeyConfigured": bool(UNSTRUCTURED_API_KEY),
            },
        )

    def do_POST(self):
        path = self.path.split("?", 1)[0]
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length) if length else b"{}"
        try:
            payload = json.loads(raw.decode("utf-8") or "{}")
        except json.JSONDecodeError:
            self._json(400, fail("请求体不是 JSON"))
            return

        if path == "/v1/knowledge/parse":
            print("[python-agent] 知识解析 documentId=%s force=%s pdf=%s" % (
                payload.get("documentId"),
                payload.get("force"),
                payload.get("pdfPath"),
            ), flush=True)
            result = run_parse(payload)
            if result.get("ok"):
                print("[python-agent] 知识解析成功 elements=%s figures=%s vl=%s/%s" % (
                    result.get("elementCount"),
                    result.get("figureCount"),
                    result.get("vlDone"),
                    result.get("vlFailed"),
                ), flush=True)
            else:
                print("[python-agent] 知识解析失败 %s" % result.get("error"), flush=True)
            self._json(200, result)
            return

        if path == "/v1/knowledge/merge":
            print("[python-agent] 知识合并 documentId=%s elements=%s" % (
                payload.get("documentId"),
                payload.get("elementsPath"),
            ), flush=True)
            result = run_merge(payload)
            if result.get("ok"):
                print("[python-agent] 知识合并成功 chunks=%s" % result.get("chunkCount"), flush=True)
            else:
                print("[python-agent] 知识合并失败 %s" % result.get("error"), flush=True)
            self._json(200, result)
            return

        if path == "/v1/knowledge/split":
            print("[python-agent] 知识分割 documentId=%s merge=%s" % (
                payload.get("documentId"),
                payload.get("mergeChunksPath"),
            ), flush=True)
            result = run_split(payload)
            if result.get("ok"):
                print("[python-agent] 知识分割成功 chunks=%s split=%s" % (
                    result.get("chunkCount"),
                    result.get("splitSourceCount"),
                ), flush=True)
            else:
                print("[python-agent] 知识分割失败 %s" % result.get("error"), flush=True)
            self._json(200, result)
            return

        if path == "/v1/knowledge/embed":
            print("[python-agent] 知识嵌入 documentId=%s split=%s" % (
                payload.get("documentId"),
                payload.get("splitChunksPath"),
            ), flush=True)
            result = run_embed(payload)
            if result.get("ok"):
                print("[python-agent] 知识嵌入成功 rows=%s text=%s fig=%s tab=%s" % (
                    result.get("rowCount"),
                    result.get("textCount"),
                    result.get("figureCount"),
                    result.get("tableCount"),
                ), flush=True)
            else:
                print("[python-agent] 知识嵌入失败 %s" % result.get("error"), flush=True)
            self._json(200, result)
            return

        if path == "/v1/knowledge/vectors/delete":
            print("[python-agent] 知识向量删除 documentId=%s" % payload.get("documentId"), flush=True)
            result = run_drop_vectors(payload)
            if result.get("ok"):
                print("[python-agent] 知识向量删除成功 deleted=%s" % result.get("deleted"), flush=True)
            else:
                print("[python-agent] 知识向量删除失败 %s" % result.get("error"), flush=True)
            self._json(200, result)
            return

        if path == "/v1/knowledge/search":
            print("[python-agent] 知识检索 docs=%s q=%s" % (
                payload.get("documentIds"),
                str(payload.get("query") or "")[:80],
            ), flush=True)
            result = run_search(payload)
            if result.get("ok"):
                print("[python-agent] 知识检索成功 hits=%s notice=%s" % (
                    len(result.get("hits") or []),
                    result.get("notice") or "",
                ), flush=True)
            else:
                print("[python-agent] 知识检索失败 %s" % result.get("error"), flush=True)
            self._json(200, result)
            return

        if path == "/v1/parse-drawings":
            kind = "drawing_parse"
        elif path.startswith("/v1/agents/"):
            kind = path[len("/v1/agents/") :].strip("/")
            if not kind or "/" in kind:
                self.send_error(404)
                return
        else:
            self.send_error(404)
            return

        if kind == "drawing_parse":
            incoming = payload.get("files") or []
            names = [file_name(item) if isinstance(item, dict) else str(item) for item in incoming]
            print("[python-agent] 识图 projectId=%s files=%s keys=%s model=%s" % (
                payload.get("projectId"),
                names,
                [list(item.keys()) if isinstance(item, dict) else type(item).__name__ for item in incoming],
                MODEL,
            ), flush=True)

        result = run_agent(kind, payload)
        if result.get("ok"):
            if kind == "inquiry":
                reply = str(result.get("reply") or "")
                print("[python-agent] inquiry 成功 chars=%s" % len(reply), flush=True)
            else:
                print("[python-agent] %s 成功 units=%s sap=%s need=%s" % (
                    kind,
                    result.get("units"),
                    result.get("sapPath"),
                    bool(result.get("needSupplement")),
                ), flush=True)
        else:
            print("[python-agent] %s 失败 %s" % (kind, result.get("error")), flush=True)
        self._json(200, result)

    def _json(self, status, data):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        print("[python-agent]", fmt % args, flush=True)


class ExclusiveServer(ThreadingHTTPServer):
    """第二份进程应直接报端口占用，避免 Windows 上两份都绑 8001。"""

    allow_reuse_address = False


def serve() -> None:
    if not API_KEY:
        print("警告：未配置 DASHSCOPE_API_KEY，识图 / 知识附图会失败。", flush=True)
    if not UNSTRUCTURED_API_KEY:
        print("警告：未配置 UNSTRUCTURED_API_KEY，知识 PDF 解析会失败。", flush=True)
    print(
        "Python Agent http://%s:%s  runtime=langgraph  识图=%s  kinds=%s"
        % (HOST, PORT, MODEL, ",".join(AGENT_KINDS)),
        flush=True,
    )
    try:
        ExclusiveServer((HOST, PORT), Handler).serve_forever()
    except OSError:
        print("8001 已被占用。请关掉另一份 python app.py 后再启动。", flush=True)
        raise
