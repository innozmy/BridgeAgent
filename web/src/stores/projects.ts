import type { InjectionKey, Ref } from "vue";
import { ref } from "vue";
import { listProjects } from "@/api/project";
import type { ProjectDetail, ProjectRecord } from "@/api/types";

export const projectList = ref<ProjectRecord[]>([]);

export const projectDetailKey: InjectionKey<Ref<ProjectDetail | null>> = Symbol("projectDetail");

export const reloadProjectKey: InjectionKey<() => Promise<void>> = Symbol("reloadProject");

export async function refreshProjectList() {
  projectList.value = await listProjects();
}

export const carriagewayLabel: Record<string, string> = {
  left: "左幅",
  right: "右幅",
  undivided: "不分幅",
};

export const strategyLabel: Record<string, string> = {
  at_opening: "按落地时点",
  current_review: "按现行复核",
};

export const statusLabel: Record<string, string> = {
  draft: "草稿",
  modeling: "建模中",
  validating: "验证中",
  calibrating: "校准中",
  done: "已完成",
};

export function formatDateTime(value?: string | null) {
  if (!value) return "—";
  return value.replace("T", " ").slice(0, 16);
}

export function formatSpanText(units?: { spansM: number[] }[] | null) {
  if (!units?.length) return "跨径未识别";
  return units
    .map((unit) => unit.spansM.join(" + ") + " m")
    .join(" ｜ ");
}

/** 概览编辑框：40 + 60 ｜ 30 + 30 */
export function formatSpanInput(units?: { spansM: number[] }[] | null) {
  if (!units?.length) return "";
  return units.map((unit) => unit.spansM.join(" + ")).join(" ｜ ");
}

/** 与后端 field_meta.spansM.drawingValue 同一套规范化 */
export function canonicalSpanText(units?: { spansM: number[] }[] | null) {
  if (!units?.length) return "";
  return units.map((unit) => unit.spansM.join("+")).join(" | ");
}
