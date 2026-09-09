"""只读打开一份 .sdb，打印构件统计后退出 SAP。不改模型。"""
from __future__ import annotations

import json
import sys

import comtypes.client


def _names(result):
    if isinstance(result, (list, tuple)):
        for item in result:
            if isinstance(item, (list, tuple)) and item and isinstance(item[0], str):
                return [str(x) for x in item]
            if isinstance(item, str) and item:
                return [item]
    return []


def dump(path: str) -> dict:
    helper = comtypes.client.CreateObject("SAP2000v1.Helper")
    helper = helper.QueryInterface(comtypes.gen.SAP2000v1.cHelper)
    sap = helper.CreateObjectProgID("CSI.SAP2000.API.SapObject")
    sap.ApplicationStart(6, True, "")
    model = sap.SapModel
    try:
        ret = model.File.OpenFile(path)
        if ret not in (0, (0,), None):
            code = ret[0] if isinstance(ret, (list, tuple)) else ret
            if code not in (0, None):
                return {"ok": False, "error": "OpenFile %s" % (ret,)}
        units = model.GetDatabaseUnits()
        n_pt = model.PointObj.Count()
        n_fr = model.FrameObj.Count()
        n_lk = 0
        try:
            n_lk = model.LinkObj.Count()
        except Exception:
            pass
        pt_names = _names(model.PointObj.GetNameList())
        fr_names = _names(model.FrameObj.GetNameList())
        lk_names = []
        try:
            lk_names = _names(model.LinkObj.GetNameList())
        except Exception:
            pass
        sects = _names(model.PropFrame.GetNameList())
        mats = _names(model.PropMaterial.GetNameList())
        links_prop = []
        try:
            links_prop = _names(model.PropLink.GetNameList())
        except Exception:
            pass
        xs, ys, zs = [], [], []
        for name in pt_names[:500]:
            got = model.PointObj.GetCoordCartesian(name)
            if isinstance(got, (list, tuple)) and len(got) >= 3:
                xs.append(float(got[0]))
                ys.append(float(got[1]))
                zs.append(float(got[2]))
        frame_sects = {}
        for name in fr_names[:800]:
            got = model.FrameObj.GetSection(name)
            sec = ""
            if isinstance(got, (list, tuple)):
                for item in got:
                    if isinstance(item, str) and item:
                        sec = item
                        break
            frame_sects[sec or "?"] = frame_sects.get(sec or "?", 0) + 1
        return {
            "ok": True,
            "units": str(units),
            "points": n_pt,
            "frames": n_fr,
            "links": n_lk,
            "pointNameSample": pt_names[:20],
            "frameNameSample": fr_names[:20],
            "linkNames": lk_names[:40],
            "sections": sects,
            "frameSectionCounts": frame_sects,
            "materials": mats,
            "linkProps": links_prop,
            "bbox": {
                "xmin": min(xs) if xs else None,
                "xmax": max(xs) if xs else None,
                "ymin": min(ys) if ys else None,
                "ymax": max(ys) if ys else None,
                "zmin": min(zs) if zs else None,
                "zmax": max(zs) if zs else None,
            },
        }
    finally:
        try:
            sap.ApplicationExit(False)
        except Exception:
            pass


if __name__ == "__main__":
    path = sys.argv[1]
    print(json.dumps(dump(path), ensure_ascii=False, indent=2))
