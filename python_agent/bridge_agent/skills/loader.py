"""按节点加载跑时 Skill 切片。全文不要一次塞进模型。"""

from pathlib import Path

_DRAWING = Path(__file__).resolve().parent / "drawing_parse"
_MODELING = Path(__file__).resolve().parent / "modeling"


def drawing_skill(*names: str) -> str:
    """读取 drawing_parse 目录下的 markdown 切片，按顺序拼接。"""
    return _read(_DRAWING, names)


def modeling_skill(*names: str) -> str:
    """读取 modeling 目录下的 markdown 切片，按顺序拼接。图未接时也可单测读取。"""
    return _read(_MODELING, names)


def _read(folder: Path, names: tuple[str, ...]) -> str:
    parts: list[str] = []
    for name in names:
        path = folder / name
        parts.append(path.read_text(encoding="utf-8").strip())
    return "\n\n".join(parts)
