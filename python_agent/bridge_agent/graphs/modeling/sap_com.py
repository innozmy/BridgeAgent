"""本机 SAP2000 COM：按规格加点加杆存 .sdb，并导出线框 previewJson。"""

from __future__ import annotations

import os
import shutil
import time
from typing import Any

from bridge_agent.skills.loader import modeling_skill

# 空模型也远大于此；未 NewBlank 或截断时只有约 6KB，SAP 打开会报流结尾
MIN_SDB_BYTES = 32_768


def _fail(msg: str) -> dict[str, Any]:
    return {"ok": False, "error": "【SAP2000】" + msg}


def _sap_ret(raw: Any) -> int:
    """comtypes 常把 HRESULT 包成元组，只取第一个整数码。"""
    if isinstance(raw, (list, tuple)):
        for item in raw:
            if isinstance(item, int) and not isinstance(item, bool):
                return int(item)
        return 0
    if isinstance(raw, bool):
        return 0 if raw else 1
    if isinstance(raw, int):
        return int(raw)
    return 0


def _bearing_area_m2(size) -> float:
    """平面尺寸只用于水平刚度；缺则用约定 0.4×0.5 m²，不挡开建。"""
    text = str(size or "").strip().lower().replace("×", "x").replace("*", "x").replace(" ", "")
    text = text.replace("mm", "").replace("cm", "").replace("m", "")
    parts = [p for p in text.split("x") if p]
    if len(parts) >= 2:
        try:
            a, b = float(parts[0]), float(parts[1])
            if a > 10 and b > 10:
                # 账本常写 mm（如 300×400）
                a, b = a / 1000.0, b / 1000.0
            if a > 0 and b > 0:
                return a * b
        except ValueError:
            pass
    return 0.4 * 0.5


def build_sdb(spec: dict[str, Any], payload: dict[str, Any]) -> dict[str, Any]:
    sap = payload.get("sap") if isinstance(payload.get("sap"), dict) else {}
    modeling_skill("sap_oapi.md")
    prog_id = str(sap.get("progId") or "CSI.SAP2000.API.SapObject")
    out_path = str(sap.get("sapOutputPath") or "")
    if not out_path:
        return _fail("Spring 未给 sapOutputPath")

    try:
        import comtypes.client
    except ImportError:
        return _fail("未安装 comtypes，无法调用本机 SAP2000")

    sap_object = None
    result: dict[str, Any] | None = None
    try:
        helper = comtypes.client.CreateObject("SAP2000v1.Helper")
        helper = helper.QueryInterface(comtypes.gen.SAP2000v1.cHelper)
        sap_object = helper.CreateObjectProgID(prog_id)
        # 先可见启动，让 OLE 复合文档真正建库；随后 Hide，避免一直挡前台
        ret = _sap_ret(sap_object.ApplicationStart(6, True, ""))
        if ret != 0:
            return _fail("ApplicationStart 返回 %s" % ret)
        model = sap_object.SapModel
        ret = _sap_ret(model.InitializeNewModel(6))
        if ret != 0:
            return _fail("InitializeNewModel 返回 %s" % ret)
        # CSI 官方顺序：InitializeNewModel 之后必须 NewBlank，否则 Save 只写出约 6KB 空壳
        ret = _sap_ret(model.File.NewBlank())
        if ret != 0:
            return _fail("File.NewBlank 返回 %s" % ret)
        try:
            model.SetPresentUnits(6)
        except Exception:
            pass
        try:
            sap_object.Hide()
        except Exception:
            pass
        _define_props(model, spec)
        _draw(model, spec)
        abs_path = os.path.abspath(out_path)
        parent = os.path.dirname(abs_path)
        if parent:
            os.makedirs(parent, exist_ok=True)
        size = _save_sdb(model, sap_object, abs_path)
        if size < 0:
            return _fail("File.Save 返回 %s" % (-size))
        if size < MIN_SDB_BYTES:
            return _fail(
                "保存的 .sdb 只有 %s 字节（空壳，不是截断）。未登记。请确认本机 SAP2000 可交互保存后重试建模。"
                % size
            )
        _copy_ok(abs_path)
        preview = {
            "joints": [{"id": j["id"], "x": j["x"], "y": j["y"], "z": j["z"]} for j in spec.get("joints") or []],
            "frames": [
                {"i": f["i"], "j": f["j"], "name": f.get("name"), "kind": f.get("kind")}
                for f in spec.get("frames") or []
            ],
        }
        version = str(sap.get("sapVersion") or "22.0.0")
        result = {
            "ok": True,
            "sapPath": abs_path,
            "sapVersion": version,
            "previewJson": preview,
            "jointCount": len(spec.get("joints") or []),
            "frameCount": len(spec.get("frames") or []),
            "note": "；".join(spec.get("notes") or []),
            "gaps": spec.get("gaps") or [],
        }
    except Exception as exc:
        return _fail(str(exc)[:400])
    finally:
        if sap_object is not None:
            try:
                sap_object.ApplicationExit(False)
            except Exception:
                pass
    if result and result.get("ok"):
        restored = _restore_after_exit(str(result["sapPath"]))
        if restored < MIN_SDB_BYTES:
            return _fail("退出 SAP 后 .sdb 仍只有 %s 字节（空壳或被截断），未登记。" % restored)
        _cleanup_sap_temps(str(result["sapPath"]))
    return result or _fail("未写出模型")


def _save_sdb(model: Any, sap_object: Any, abs_path: str) -> int:
    """解锁、删旧文件、短暂可见后 Save；过小再存一次。负值表示 Save 返回码。"""
    try:
        model.SetModelIsLocked(False)
    except Exception:
        pass
    if os.path.isfile(abs_path):
        try:
            os.remove(abs_path)
        except OSError:
            pass
    # 隐藏态下 SAP 22 有时只写出空壳，保存前亮一下窗口
    try:
        sap_object.Visible = True
    except Exception:
        pass
    ret = _sap_ret(model.File.Save(abs_path))
    if ret != 0:
        return -abs(ret) if ret else -1
    size = _wait_file_stable(abs_path)
    if size < MIN_SDB_BYTES:
        time.sleep(0.4)
        ret = _sap_ret(model.File.Save(abs_path))
        if ret != 0:
            return -abs(ret) if ret else -1
        size = _wait_file_stable(abs_path)
    try:
        sap_object.Hide()
    except Exception:
        pass
    return size


def _wait_file_stable(path: str, tries: int = 25, interval: float = 0.25) -> int:
    """等磁盘体积连续几次不变，避免 Save 尚未刷完。"""
    last = -1
    same = 0
    for _ in range(tries):
        if os.path.isfile(path):
            size = os.path.getsize(path)
            if size > 0 and size == last:
                same += 1
                if same >= 3:
                    return size
            else:
                same = 0
                last = size
        time.sleep(interval)
    return os.path.getsize(path) if os.path.isfile(path) else 0


def _copy_ok(path: str) -> None:
    try:
        shutil.copy2(path, path + ".ok")
    except OSError:
        pass


def _restore_after_exit(path: str) -> int:
    """Exit 若截断原文件，用 Exit 前的拷贝盖回去。"""
    bak = path + ".ok"
    orig = os.path.getsize(path) if os.path.isfile(path) else 0
    if os.path.isfile(bak):
        bak_size = os.path.getsize(bak)
        if bak_size >= orig and bak_size >= MIN_SDB_BYTES:
            try:
                shutil.move(bak, path)
                orig = os.path.getsize(path)
            except OSError:
                pass
        else:
            try:
                os.remove(bak)
            except OSError:
                pass
    return orig


def _cleanup_sap_temps(path: str) -> None:
    """Save 中断时会留下 .$2k；成功后清掉以免误当模型。"""
    stem, _ = os.path.splitext(path)
    for extra in (stem + ".$2k", path + ".$2k", path + ".ok"):
        try:
            if os.path.isfile(extra):
                os.remove(extra)
        except OSError:
            pass


def _ensure_concrete(model: Any, name: str, e: float) -> None:
    """库里已有 GB 标号则只校正 E 与重度；没有再新建。"""
    try:
        model.PropMaterial.SetMaterial(name, 2)
    except Exception:
        pass
    model.PropMaterial.SetMPIsotropic(name, e, 0.2, 1.0e-5)
    try:
        # MyOption=1：重度 kN/m³
        model.PropMaterial.SetWeightAndMass(name, 1, 25.0)
    except Exception:
        pass


def _set_linear_link(model: Any, name: str, kz: float, k2: float, k3: float) -> None:
    """Link 局部 U1 沿杆（竖向支座即竖向）；转动刚度全 0。只用 Linear，禁止 Wen / 并联合成。"""
    dof = [True, True, True, True, True, True]
    fixed = [False, False, False, False, False, False]
    ke = [float(kz), float(k2), float(k3), 0.0, 0.0, 0.0]
    ce = [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]
    model.PropLink.SetLinear(name, dof, fixed, ke, ce, 0.0, 0.0)


def _define_props(model: Any, spec: dict[str, Any]) -> None:
    material = spec.get("material") or "C50"
    pier_mat = spec.get("pierMaterial") or material
    _ensure_concrete(model, material, float(spec.get("E") or 3.45e7))
    _ensure_concrete(model, pier_mat, float(spec.get("EPier") or spec.get("E") or 3.45e7))
    g = spec.get("girder") or {}
    model.PropFrame.SetRectangle("GIRDER", material, float(g.get("h") or 1.8), float(g.get("w") or 1.7))
    dh = float(spec.get("dummyH") or 0.18)
    db = float(spec.get("dummyB") or 3.0)
    model.PropFrame.SetRectangle("DUMMY", material, dh, db)
    # 虚横梁不计自重与质量，避免与主梁/铺装重复
    try:
        model.PropFrame.SetModifiers("DUMMY", [1, 1, 1, 1, 1, 1, 0, 0])
    except Exception:
        pass
    pd = float((spec.get("pier") or {}).get("d") or 1.2)
    model.PropFrame.SetCircle("PIER", pier_mat, pd)
    model.PropFrame.SetCircle("PILE", pier_mat, float(spec.get("pileD") or 1.2))
    cap = spec.get("cap") if isinstance(spec.get("cap"), dict) else None
    if cap:
        model.PropFrame.SetRectangle("CAP", pier_mat, float(cap.get("h") or 1.5), float(cap.get("w") or 1.8))
    tie = spec.get("tie") if isinstance(spec.get("tie"), dict) else None
    if tie:
        model.PropFrame.SetRectangle("TIE", pier_mat, float(tie.get("h") or 1.0), float(tie.get("w") or 1.2))
    br = spec.get("bearing") or {}
    thick = max(float(br.get("thick") or 0.063), 1e-4)
    area = _bearing_area_m2(br.get("size"))
    gmod = float(br.get("G") or 1.0) * 1000.0
    kh = gmod * area / thick
    # U1=竖向 1e8；GJZ 两水平同 kh；GJZF 顺桥向 1.0 kN/m
    _set_linear_link(model, "GJZ", 1.0e8, kh, kh)
    _set_linear_link(model, "GJZF", 1.0e8, 1.0, kh)
    try:
        model.SourceMass.SetMassSource("MSSSRC1", True, True, False, True, 0, [], [])
    except Exception:
        pass


def _out_name(result: Any, fallback: str) -> str:
    if isinstance(result, (list, tuple)):
        for item in result:
            if isinstance(item, str) and item:
                return item
    return fallback


def _add_point(model: Any, x: float, y: float, z: float, user_name: str) -> str:
    # MergeOff：支座顶与梁节点坐标接近也不得合并，否则 Body 约束没了两个点
    result = model.PointObj.AddCartesian(x, y, z, "", user_name, "Global", True)
    return _out_name(result, user_name)


def _draw(model: Any, spec: dict[str, Any]) -> None:
    id_map: dict[int, str] = {}
    for joint in spec.get("joints") or []:
        sap_name = _add_point(
            model, joint["x"], joint["y"], joint["z"], str(joint.get("name") or joint["id"]),
        )
        id_map[int(joint["id"])] = sap_name
        rest = joint.get("restraint")
        if rest:
            model.PointObj.SetRestraint(sap_name, list(rest))
        spring = joint.get("spring")
        if spring:
            model.PointObj.SetSpring(sap_name, [float(v) for v in spring])
    for frame in spec.get("frames") or []:
        i = id_map.get(int(frame["i"]))
        j = id_map.get(int(frame["j"]))
        if not i or not j:
            continue
        user = str(frame.get("name") or "")
        prop = str(frame.get("prop") or "GIRDER")
        sap_f = _out_name(model.FrameObj.AddByPoint(i, j, "", prop, user), user)
        insert = frame.get("insert")
        if insert:
            try:
                model.FrameObj.SetInsertionPoint_1(
                    sap_f, int(insert), False, False, True, [0.0, 0.0, 0.0], [0.0, 0.0, 0.0],
                )
            except Exception:
                pass
        mass = float(frame.get("massPerLen") or 0)
        if mass > 0:
            try:
                model.FrameObj.SetMass(sap_f, mass, True)
            except Exception:
                pass
    for link in spec.get("links") or []:
        a = id_map.get(int(link.get("i") or 0))
        b = id_map.get(int(link.get("j") or 0))
        if not a or not b:
            continue
        prop = str(link.get("prop") or "GJZ")
        user = str(link.get("name") or "")
        try:
            model.LinkObj.AddByPoint(a, b, "", False, prop, user)
        except Exception:
            continue
    for body in spec.get("constraints") or []:
        cname = str(body.get("name") or "")
        ids = [id_map.get(int(j)) for j in (body.get("joints") or [])]
        ids = [n for n in ids if n]
        if len(ids) < 2 or not cname:
            continue
        try:
            model.ConstraintDef.SetBody(cname, [True, True, True, True, True, True])
            for n in ids:
                model.PointObj.SetConstraint(n, cname)
        except Exception:
            continue
