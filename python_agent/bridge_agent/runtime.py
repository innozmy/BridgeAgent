"""给 Spring 的失败 JSON 与百炼错误归类。无 Key / 额度不足禁止再造假跨径。"""

from __future__ import annotations

from openai import AuthenticationError, RateLimitError

from bridge_agent.settings import QUOTA_MARKERS


def fail(error: str, note: str | None = None) -> dict:
    body = {"ok": False, "error": error, "units": []}
    if note:
        body["note"] = note
    return body


def is_quota_message(text: str) -> bool:
    lower = text.lower()
    return any(marker in lower or marker in text for marker in QUOTA_MARKERS)


def classify_api_error(exc: Exception) -> str:
    """额度/密钥问题用固定前缀，方便任务时间线和联调时立刻发现。"""
    text = str(exc)
    status = getattr(exc, "status_code", None)
    if isinstance(exc, AuthenticationError) or status in (401, 403):
        return "【百炼密钥】无效、无权限或未开通视觉模型。请检查 DASHSCOPE_API_KEY。"
    if isinstance(exc, RateLimitError) or status == 429:
        if is_quota_message(text):
            return "【百炼额度】不足或已用尽，请到阿里云百炼控制台充值或开通模型额度。"
        return "【百炼限流】请求过于频繁，请稍后再识别。"
    if is_quota_message(text) or status == 402:
        return "【百炼额度】不足或已用尽，请到阿里云百炼控制台充值或开通模型额度。"
    return "【百炼调用失败】" + text[:400]
