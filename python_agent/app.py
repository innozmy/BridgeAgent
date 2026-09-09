"""入口：python app.py。业务在 bridge_agent 包里的 LangGraph。"""

from __future__ import annotations

import sys

if sys.version_info < (3, 10):
    sys.stderr.write(
        "LangGraph 1.2 需要 Python 3.10+（本机请用 3.12）。\n"
        "在 python_agent 目录：py -3.12 -m venv .venv\n"
        "然后：.\\.venv\\Scripts\\python -m pip install -r requirements.txt\n"
        "启动：.\\.venv\\Scripts\\python app.py\n"
    )
    raise SystemExit(1)

from bridge_agent.server.http import serve

if __name__ == "__main__":
    serve()
