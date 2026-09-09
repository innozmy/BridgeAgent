"""千问 VL：页图 + 提示词 → JSON 对象。"""

from __future__ import annotations

import base64
import json

from openai import OpenAI

from bridge_agent.settings import API_KEY, BASE_URL, MODEL


def client() -> OpenAI:
    return OpenAI(api_key=API_KEY, base_url=BASE_URL, timeout=180.0)


def image_part(jpeg: bytes) -> dict:
    b64 = base64.b64encode(jpeg).decode("ascii")
    return {
        "type": "image_url",
        "image_url": {"url": "data:image/jpeg;base64," + b64},
    }


def chat_json(images: list[bytes], prompt: str) -> dict:
    """一次独立 user 消息，不带历史。批与批之间的记忆由页地图/图状态承担。"""
    content: list[dict] = [image_part(img) for img in images]
    content.append({"type": "text", "text": prompt})
    completion = client().chat.completions.create(
        model=MODEL,
        messages=[{"role": "user", "content": content}],
        response_format={"type": "json_object"},
        extra_body={"enable_thinking": False},
    )
    raw = completion.choices[0].message.content or "{}"
    return json.loads(raw)


def chat_text_json(system: str, turns: list[dict]) -> dict:
    """问询：带历史的 JSON 对象（reply + 可选 taskCard）。"""
    result = chat_messages(system, turns, force_json=True)
    data = parse_json_object(result.get("content"))
    return data if isinstance(data, dict) else {"reply": str(data)}


def chat_messages(
    system: str,
    turns: list[dict],
    *,
    tools: list[dict] | None = None,
    force_json: bool = False,
    images: list[bytes] | None = None,
) -> dict:
    """
    带可选工具与附图的一轮对话。
    返回 {content, tool_calls:[{id,name,arguments}]}。arguments 已是 dict。
    """
    messages: list[dict] = [{"role": "system", "content": system}]
    messages.extend(turns)
    if images:
        content: list[dict] = [image_part(img) for img in images]
        content.append({"type": "text", "text": "以上是本轮补发的规范附图，请结合工具返回的短文与表阅读。"})
        messages.append({"role": "user", "content": content})
    kwargs: dict = {
        "model": MODEL,
        "messages": messages,
        "extra_body": {"enable_thinking": False},
    }
    if force_json:
        kwargs["response_format"] = {"type": "json_object"}
    if tools:
        kwargs["tools"] = tools
        kwargs["tool_choice"] = "auto"
    completion = client().chat.completions.create(**kwargs)
    message = completion.choices[0].message
    calls = []
    for raw in message.tool_calls or []:
        fn = raw.function
        args = {}
        try:
            parsed = json.loads(fn.arguments or "{}")
            if isinstance(parsed, dict):
                args = parsed
        except json.JSONDecodeError:
            args = {}
        calls.append({"id": raw.id, "name": fn.name, "arguments": args})
    return {"content": (message.content or "").strip(), "tool_calls": calls}


def parse_json_object(raw: str):
    """从模型文本里取出 JSON 对象；允许前后有废话。"""
    text = (raw or "").strip()
    if not text:
        return {}
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        start = text.find("{")
        end = text.rfind("}")
        if start >= 0 and end > start:
            return json.loads(text[start : end + 1])
        raise


def _parse_json_content(raw: str):
    return parse_json_object(raw)


def chat_text(system: str, turns: list[dict]) -> str:
    """问询用的纯文本对话。turns 为 OpenAI 风格 role/content，不含系统提示。"""
    messages: list[dict] = [{"role": "system", "content": system}]
    messages.extend(turns)
    completion = client().chat.completions.create(
        model=MODEL,
        messages=messages,
        extra_body={"enable_thinking": False},
    )
    return (completion.choices[0].message.content or "").strip()
