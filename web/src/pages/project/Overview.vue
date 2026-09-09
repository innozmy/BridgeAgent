<script setup lang="ts">
import { computed, inject, onUnmounted, reactive, ref, watch } from "vue";
import { useRouter, useRoute } from "vue-router";
import { message } from "ant-design-vue";
import { getProjectKnowledge } from "@/api/knowledge";
import { replaceProjectParams, replaceProjectUnits, updateProject } from "@/api/project";
import { listSapModels } from "@/api/models";
import { listModelTasks } from "@/api/tasks";
import type { FieldProvenance, KnowledgeDocument, ProjectFieldMeta, ProjectParam, ProjectSapModel, ProjectUnit } from "@/api/types";
import { knowledgeGroups } from "@/stores/knowledge";
import {
  canonicalSpanText,
  carriagewayLabel,
  formatSpanInput,
  projectDetailKey,
  reloadProjectKey,
  strategyLabel,
} from "@/stores/projects";

const canOperate = inject("projectCanOperate", computed(() => false));

const router = useRouter();
const route = useRoute();
const detail = inject(projectDetailKey);
const reload = inject(reloadProjectKey);
const project = computed(() => detail?.value);
const docs = ref<KnowledgeDocument[]>([]);
const enabledIds = ref<number[]>([]);
const latestModel = ref<ProjectSapModel | null>(null);
const saving = ref(false);
/** 本项目有排队或在跑的建模卡时提示方案 A 快照边界 */
const modelingBusy = ref(false);
let taskPoll: ReturnType<typeof setInterval> | null = null;

const form = reactive({
  name: "",
  code: "",
  carriageway: "undivided",
  girderType: "",
  layoutType: "",
  spansText: "",
  material: "",
  region: "",
  openedOn: undefined as string | undefined,
  codeStrategy: undefined as string | undefined,
  intro: "",
});

/** 联 → 墩台 → 柱；与跨径文本分开，避免把柱高塞进「40+60+40」 */
const unitDraft = ref<UnitDraft[]>([]);
const paramDraft = ref<ParamDraft[]>([]);

type ColumnDraft = { seq: number; side: string; heightM: string };
type SupportDraft = { seq: number; code: string; kind: string; columns: ColumnDraft[] };
type UnitDraft = { seq: number; supports: SupportDraft[] };
type ParamDraft = { paramKey: string; label: string; valueText: string; unit: string; source: string };

const enabled = computed(() => docs.value.filter((doc) => enabledIds.value.includes(doc.id)));
const showConcreteHint = computed(() => {
  const doc = docs.value.find((item) => item.name.includes("GB 50010-2002"));
  return Boolean(doc && !enabledIds.value.includes(doc.id));
});

const meta = computed<ProjectFieldMeta>(() => project.value?.fieldMeta ?? {});

function sourceOf(node?: FieldProvenance | null) {
  return node?.source === "drawing" || node?.source === "manual" ? node.source : null;
}

const conflicts = computed(() => {
  const p = project.value;
  if (!p) return [];
  const rows: { field: string; drawing: string; current: string }[] = [];
  pushConflict(rows, "标号", p.code, meta.value.code);
  pushConflict(rows, "主梁形式", p.girderType, meta.value.girderType);
  pushConflict(rows, "结构形式", p.layoutType, meta.value.layoutType);
  pushConflict(rows, "材料", p.material, meta.value.material);
  pushConflict(rows, "地区", p.region, meta.value.region);
  const currentSpans = canonicalSpanText(p.units);
  const drawingSpans = meta.value.spansM?.drawingValue?.trim() ?? "";
  if (drawingSpans && currentSpans && drawingSpans !== currentSpans) {
    rows.push({ field: "跨径", drawing: drawingSpans, current: currentSpans });
  }
  return rows;
});

function pushConflict(
  rows: { field: string; drawing: string; current: string }[],
  field: string,
  current: string | null | undefined,
  node?: FieldProvenance | null,
) {
  const drawing = node?.drawingValue?.trim() ?? "";
  const now = current?.trim() ?? "";
  if (!drawing || !now || drawing === now) return;
  rows.push({ field, drawing, current: now });
}

const gapHints = computed(() => {
  const p = project.value;
  if (!p) return [];
  const gaps = meta.value.gaps ?? [];
  const hasLayout = meta.value.hasLayoutPages;
  const parsed = Boolean(meta.value.gaps || meta.value.hasLayoutPages != null || meta.value.spansM);
  if (!parsed) return [];
  const hints: { text: string; toDrawings: boolean }[] = [];
  const emptySpans = !p.units?.length;
  if (emptySpans && (hasLayout === false || gaps.includes("missing_layout"))) {
    hints.push({ text: "缺总布置/立面一类图纸。请到图纸页补充上传后再识别。", toDrawings: true });
  } else if (emptySpans && (gaps.includes("spansM") || hasLayout === true)) {
    hints.push({ text: "有布置图但未读出跨径。请在本页手填，或去问询说明。", toDrawings: false });
  }
  if (!p.girderType && gaps.includes("girderType")) {
    hints.push({ text: "未读出主梁形式，可在本页修改或去问询说明。", toDrawings: false });
  }
  if (!p.layoutType && gaps.includes("layoutType")) {
    hints.push({ text: "未读出结构形式，可在本页修改或去问询说明。", toDrawings: false });
  }
  if (!p.material && gaps.includes("material")) {
    hints.push({ text: "未读出材料，可在本页修改或去问询说明。", toDrawings: false });
  }
  return hints;
});

function fillForm() {
  const p = project.value;
  form.name = p?.name ?? "";
  form.code = p?.code ?? "";
  form.carriageway = p?.carriageway ?? "undivided";
  form.girderType = p?.girderType ?? "";
  form.layoutType = p?.layoutType ?? "";
  form.spansText = formatSpanInput(p?.units);
  form.material = p?.material ?? "";
  form.region = p?.region ?? "";
  form.openedOn = p?.openedOn ?? undefined;
  form.codeStrategy = p?.codeStrategy ?? undefined;
  form.intro = p?.intro ?? "";
  unitDraft.value = cloneUnits(p?.units);
  paramDraft.value = cloneParams(p?.params);
}

function cloneUnits(units?: ProjectUnit[] | null): UnitDraft[] {
  return (units ?? []).map((unit) => ({
    seq: unit.seq,
    supports: (unit.supports ?? []).map((support) => ({
      seq: support.seq,
      code: support.code ?? "",
      kind: support.kind === "abutment" ? "abutment" : "pier",
      columns: (support.columns ?? []).map((column) => ({
        seq: column.seq,
        side: column.side ?? "",
        heightM: column.heightM == null ? "" : String(column.heightM),
      })),
    })),
  }));
}

function cloneParams(params?: ProjectParam[] | null): ParamDraft[] {
  return (params ?? []).map((row) => ({
    paramKey: row.paramKey,
    label: row.label,
    valueText: row.valueText ?? "",
    unit: row.unit ?? "",
    source: row.source || "manual",
  }));
}

function parseSpans(raw: string) {
  const chunks = raw
    .split(/[｜|]/)
    .map((item) => item.trim())
    .filter(Boolean);
  return chunks.map((chunk, index) => {
    const spansM = chunk
      .split(/[+＋]/)
      .map((part) => part.replace(/m$/i, "").trim())
      .filter(Boolean)
      .map(Number);
    if (!spansM.length || spansM.some((n) => !Number.isFinite(n) || n <= 0)) {
      throw new Error(`第 ${index + 1} 联跨径无法解析，请用「40 + 60 + 40」这种写法`);
    }
    return { seq: index + 1, spansM, source: "manual" };
  });
}

/** 改跨径孔数时只按联序号对齐已有墩柱，不自动删柱行里的数。 */
function syncUnitDraftToSpans(spans: { seq: number }[]) {
  const next = spans.map((unit) => {
    const old = unitDraft.value.find((item) => item.seq === unit.seq);
    return old ?? { seq: unit.seq, supports: [] };
  });
  unitDraft.value = next;
}

function serializeSupports(draft: UnitDraft) {
  return draft.supports.map((support) => ({
    seq: support.seq,
    code: support.code.trim() || null,
    kind: support.kind === "abutment" ? "abutment" : "pier",
    source: "manual" as const,
    columns: support.columns.map((column) => {
      const raw = column.heightM.trim();
      const heightM = raw === "" ? null : Number(raw);
      if (heightM != null && (!Number.isFinite(heightM) || heightM <= 0)) {
        throw new Error(`第 ${draft.seq} 联墩台 ${support.code || support.seq} 的柱高无法解析`);
      }
      return {
        seq: column.seq,
        side: column.side || null,
        heightM,
        source: "manual" as const,
      };
    }),
  }));
}

function fingerprintUnits(
  units: { seq: number; spansM: number[]; supports?: ReturnType<typeof serializeSupports> }[],
) {
  return JSON.stringify(
    units.map((unit) => ({
      seq: unit.seq,
      spansM: unit.spansM,
      supports: (unit.supports ?? []).map((support) => ({
        seq: support.seq,
        code: support.code ?? "",
        kind: support.kind,
        columns: (support.columns ?? []).map((column) => ({
          seq: column.seq,
          side: column.side ?? "",
          heightM: column.heightM,
        })),
      })),
    })),
  );
}

function canonicalizeParamKey(key: string, label: string) {
  const phrases: { phrase: string; canon: string }[] = [
    { phrase: "钻孔桩径", canon: "pileDiameterM" },
    { phrase: "桩直径", canon: "pileDiameterM" },
    { phrase: "桩径", canon: "pileDiameterM" },
    { phrase: "pilediameter", canon: "pileDiameterM" },
    { phrase: "桩长度", canon: "pileLengthM" },
    { phrase: "桩长", canon: "pileLengthM" },
    { phrase: "每墩桩数", canon: "pileCountPerPier" },
    { phrase: "桩数", canon: "pileCountPerPier" },
    { phrase: "桩位布置", canon: "pileLayout" },
    { phrase: "桩位", canon: "pileLayout" },
  ];
  const fold = (raw: string) => raw.trim().toLowerCase().replaceAll(/[\s_\-]/g, "");
  const fields = [fold(key), fold(label)].filter(Boolean);
  const sorted = [...phrases].sort((a, b) => b.phrase.length - a.phrase.length);
  for (const item of sorted) {
    const p = fold(item.phrase);
    if (fields.some((field) => field === p)) return item.canon;
  }
  for (const item of sorted) {
    const p = fold(item.phrase);
    if (p.length < 2) continue;
    if (fields.some((field) => field.includes(p))) return item.canon;
  }
  return key.trim();
}

function fingerprintParams(items: ParamDraft[], canonicalize = false) {
  return JSON.stringify(
    items.map((item) => ({
      paramKey: canonicalize ? canonicalizeParamKey(item.paramKey, item.label) : item.paramKey.trim(),
      label: item.label.trim(),
      valueText: item.valueText.trim(),
      unit: item.unit.trim(),
    })),
  );
}

function addSupport(unit: UnitDraft) {
  const seq = unit.supports.reduce((max, item) => Math.max(max, item.seq), -1) + 1;
  unit.supports.push({
    seq,
    code: seq === 0 ? "0#" : `${seq}#`,
    kind: "pier",
    columns: [{ seq: 1, side: "", heightM: "" }],
  });
}

function addColumn(support: SupportDraft) {
  const seq = support.columns.reduce((max, item) => Math.max(max, item.seq), 0) + 1;
  support.columns.push({ seq, side: "", heightM: "" });
}

function removeSupport(unit: UnitDraft, index: number) {
  unit.supports.splice(index, 1);
}

function removeColumn(support: SupportDraft, index: number) {
  support.columns.splice(index, 1);
}

function addParam() {
  paramDraft.value.push({ paramKey: "", label: "", valueText: "", unit: "", source: "manual" });
}

function removeParam(index: number) {
  paramDraft.value.splice(index, 1);
}

async function save() {
  const id = route.params.id;
  if (!id) return;
  if (!form.name.trim()) {
    message.warning("项目名称不能为空");
    return;
  }
  let units: { seq: number; spansM: number[]; source: string; supports: ReturnType<typeof serializeSupports> }[] | null =
    null;
  try {
    const parsed = form.spansText.trim() ? parseSpans(form.spansText) : [];
    syncUnitDraftToSpans(parsed);
    units = parsed.map((unit) => {
      const draft = unitDraft.value.find((item) => item.seq === unit.seq) ?? { seq: unit.seq, supports: [] };
      const original = project.value?.units?.find((item) => item.seq === unit.seq);
      const spansSame = original && JSON.stringify(original.spansM) === JSON.stringify(unit.spansM);
      return {
        ...unit,
        source: spansSame ? original.source || "manual" : "manual",
        supports: serializeSupports(draft),
      };
    });
  } catch (error) {
    message.warning(error instanceof Error ? error.message : "跨径或墩柱格式不对");
    return;
  }
  for (const item of paramDraft.value) {
    if (!item.paramKey.trim() || !item.label.trim()) {
      message.warning("其他参数的键和名称都要填");
      return;
    }
  }
  saving.value = true;
  try {
    const ledgerVersion = project.value?.version;
    if (ledgerVersion == null) {
      message.warning("请刷新后再保存");
      return;
    }
    let next = await updateProject(String(id), {
      name: form.name.trim(),
      carriageway: form.carriageway,
      code: form.code,
      intro: form.intro,
      region: form.region,
      openedOn: form.openedOn,
      codeStrategy: form.codeStrategy ?? "",
      girderType: form.girderType,
      layoutType: form.layoutType,
      material: form.material,
      version: ledgerVersion,
    });
    const currentUnits = fingerprintUnits(
      (project.value?.units ?? []).map((unit) => ({
        seq: unit.seq,
        spansM: unit.spansM,
        supports: (unit.supports ?? []).map((support) => ({
          seq: support.seq,
          code: support.code ?? null,
          kind: support.kind === "abutment" ? "abutment" : "pier",
          source: "manual" as const,
          columns: (support.columns ?? []).map((column) => ({
            seq: column.seq,
            side: column.side ?? null,
            heightM: column.heightM ?? null,
            source: "manual" as const,
          })),
        })),
      })),
    );
    const nextUnits = fingerprintUnits(units);
    if (currentUnits !== nextUnits) {
      next = await replaceProjectUnits(String(id), { version: next.version, units });
    }
    const currentParams = fingerprintParams(cloneParams(project.value?.params));
    const nextParams = fingerprintParams(paramDraft.value, true);
    if (currentParams !== nextParams) {
      await replaceProjectParams(String(id), {
        version: next.version,
        items: paramDraft.value.map((item) => {
          const original = project.value?.params?.find((row) => row.paramKey === item.paramKey.trim());
          const unchanged =
            original &&
            (original.label ?? "") === item.label.trim() &&
            (original.valueText ?? "") === item.valueText.trim() &&
            (original.unit ?? "") === item.unit.trim();
          return {
            paramKey: canonicalizeParamKey(item.paramKey, item.label),
            label: item.label.trim(),
            valueText: item.valueText.trim(),
            unit: item.unit.trim(),
            source: unchanged ? original.source || "manual" : "manual",
          };
        }),
      });
    }
    await reload?.();
    message.success("已保存");
  } catch (error) {
    const text = error instanceof Error ? error.message : "保存失败";
    message.error(text);
    if (text.includes("刷新") || text.includes("他人修改")) {
      await reload?.();
    }
  } finally {
    saving.value = false;
  }
}

function go(name: string) {
  router.push(`/projects/${route.params.id}/${name}`);
}

async function loadKnowledge() {
  const id = route.params.id;
  if (!id) return;
  try {
    const payload = await getProjectKnowledge(String(id));
    docs.value = payload.documents;
    enabledIds.value = payload.enabledIds;
  } catch {
    docs.value = [];
    enabledIds.value = [];
  }
}

async function loadLatestModel() {
  const id = route.params.id;
  if (!id) return;
  try {
    const list = await listSapModels(String(id));
    latestModel.value = list[0] ?? null;
  } catch {
    latestModel.value = null;
  }
}

async function loadModelingBusy() {
  const id = route.params.id;
  if (!id) return;
  try {
    const list = await listModelTasks(String(id));
    modelingBusy.value = list.some((item) => {
      const task = item.task;
      return task.kind === "modeling" && (task.status === "running" || task.status === "queued");
    });
  } catch {
    modelingBusy.value = false;
  }
}

function startTaskPoll() {
  if (taskPoll) return;
  taskPoll = setInterval(() => {
    loadModelingBusy().catch(() => undefined);
  }, 4000);
}

watch(() => project.value, fillForm, { immediate: true });
watch(() => route.params.id, () => {
  loadKnowledge();
  loadLatestModel();
  loadModelingBusy();
}, { immediate: true });
startTaskPoll();
onUnmounted(() => {
  if (taskPoll) clearInterval(taskPoll);
});
</script>

<template>
  <div class="grid">
    <div>
      <div v-if="modelingBusy" class="ba-flash">
        <span>本枪建模按开跑时账本快照计算。你现在改的数不会打进已经在跑的那一枪；硬缺口自动补识图后会再发一枪并重新读库。若本卡一轮就建成模型，要对齐新账本需再同意一张新建模卡。</span>
      </div>
      <div v-for="row in conflicts" :key="row.field" class="ba-flash">
        <span>{{ row.field }}：识图 {{ row.drawing }}，当前手填 {{ row.current }}。账本以当前值为准。</span>
      </div>
      <div v-for="hint in gapHints" :key="hint.text" class="ba-flash">
        <span>{{ hint.text }}</span>
        <a v-if="hint.toDrawings" href="javascript:void(0)" @click="go('drawings')">去图纸</a>
        <a v-else href="javascript:void(0)" @click="go('inquiry')">去问询</a>
      </div>
      <div class="head-row">
        <h2 class="h">项目</h2>
        <a-button v-if="canOperate" type="primary" size="small" :loading="saving" @click="save">保存</a-button>
      </div>
      <table class="kv">
        <tbody>
          <tr>
            <th>名称</th>
            <td><a-input :disabled="!canOperate" v-model:value="form.name" /></td>
          </tr>
          <tr>
            <th>
              标号
              <span v-if="sourceOf(meta.code)" class="src" :class="sourceOf(meta.code)">{{
                sourceOf(meta.code) === "drawing" ? "识图" : "手填"
              }}</span>
            </th>
            <td><a-input :disabled="!canOperate" v-model:value="form.code" placeholder="未填" /></td>
          </tr>
          <tr>
            <th>幅面</th>
            <td>
              <a-select :disabled="!canOperate" v-model:value="form.carriageway" style="width: 100%">
                <a-select-option value="left">左幅</a-select-option>
                <a-select-option value="right">右幅</a-select-option>
                <a-select-option value="undivided">不分幅</a-select-option>
              </a-select>
            </td>
          </tr>
          <tr>
            <th>
              主梁形式
              <span v-if="sourceOf(meta.girderType)" class="src" :class="sourceOf(meta.girderType)">{{
                sourceOf(meta.girderType) === "drawing" ? "识图" : "手填"
              }}</span>
            </th>
            <td><a-input :disabled="!canOperate" v-model:value="form.girderType" placeholder="未识别" /></td>
          </tr>
          <tr>
            <th>
              结构形式
              <span v-if="sourceOf(meta.layoutType)" class="src" :class="sourceOf(meta.layoutType)">{{
                sourceOf(meta.layoutType) === "drawing" ? "识图" : "手填"
              }}</span>
            </th>
            <td><a-input :disabled="!canOperate" v-model:value="form.layoutType" placeholder="未识别" /></td>
          </tr>
          <tr>
            <th>
              跨径
              <span v-if="sourceOf(meta.spansM)" class="src" :class="sourceOf(meta.spansM)">{{
                sourceOf(meta.spansM) === "drawing" ? "识图" : "手填"
              }}</span>
            </th>
            <td>
              <a-input
                :disabled="!canOperate"
                v-model:value="form.spansText"
                placeholder="例如 40 + 60 + 40；多联用 ｜ 分隔"
              />
            </td>
          </tr>
          <tr>
            <th>
              材料
              <span v-if="sourceOf(meta.material)" class="src" :class="sourceOf(meta.material)">{{
                sourceOf(meta.material) === "drawing" ? "识图" : "手填"
              }}</span>
            </th>
            <td><a-input :disabled="!canOperate" v-model:value="form.material" placeholder="未识别" /></td>
          </tr>
          <tr>
            <th>
              地区
              <span v-if="sourceOf(meta.region)" class="src" :class="sourceOf(meta.region)">{{
                sourceOf(meta.region) === "drawing" ? "识图" : "手填"
              }}</span>
            </th>
            <td><a-input :disabled="!canOperate" v-model:value="form.region" placeholder="未填" /></td>
          </tr>
          <tr>
            <th>通车日</th>
            <td>
              <a-date-picker :disabled="!canOperate" v-model:value="form.openedOn" value-format="YYYY-MM-DD" style="width: 100%" />
            </td>
          </tr>
          <tr>
            <th>策略</th>
            <td>
              <a-select :disabled="!canOperate" v-model:value="form.codeStrategy" allow-clear placeholder="未填" style="width: 100%">
                <a-select-option value="at_opening">按落地时点</a-select-option>
                <a-select-option value="current_review">按现行复核</a-select-option>
              </a-select>
            </td>
          </tr>
          <tr>
            <th>简介</th>
            <td><a-textarea :disabled="!canOperate" v-model:value="form.intro" :rows="2" placeholder="未填" /></td>
          </tr>
        </tbody>
      </table>
      <p class="hint">
        当前账本以本页保存的值为准。识图与手填不一致时上方会提示对照，不会默默覆盖。幅面建项时已定。
        幅面显示：{{ carriagewayLabel[form.carriageway] ?? form.carriageway }}
        · 策略 {{ strategyLabel[form.codeStrategy ?? ""] ?? (form.codeStrategy || "未填") }}
        跨径仍用一行文本；墩柱高度在下方按联 → 墩台 → 柱编辑，双柱不同高不要合成一个数。
      </p>
      <div class="head-row sub">
        <h2 class="h">联与墩柱</h2>
      </div>
      <p v-if="!unitDraft.length" class="hint">先填写跨径并保存后，再补各墩台柱高。n 跨通常对应 n+1 个墩台。</p>
      <div v-for="unit in unitDraft" :key="unit.seq" class="unit-block">
        <div class="unit-head">
          <span>第 {{ unit.seq }} 联</span>
          <a v-if="canOperate" href="javascript:void(0)" @click="addSupport(unit)">加墩台</a>
        </div>
        <table class="kv nested">
          <thead>
            <tr>
              <th>编号</th>
              <th>类型</th>
              <th>墩柱（每根单独一行）</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(support, sIndex) in unit.supports" :key="support.seq">
              <td><a-input :disabled="!canOperate" v-model:value="support.code" placeholder="1#" /></td>
              <td>
                <a-select :disabled="!canOperate" v-model:value="support.kind" style="width: 100%">
                  <a-select-option value="pier">桥墩</a-select-option>
                  <a-select-option value="abutment">桥台</a-select-option>
                </a-select>
              </td>
              <td>
                <div v-for="(column, cIndex) in support.columns" :key="column.seq" class="col-row">
                  <span class="col-seq">柱{{ column.seq }}</span>
                  <a-select :disabled="!canOperate" v-model:value="column.side" allow-clear placeholder="位置" style="width: 88px">
                    <a-select-option value="left">左</a-select-option>
                    <a-select-option value="right">右</a-select-option>
                    <a-select-option value="inner">内</a-select-option>
                    <a-select-option value="outer">外</a-select-option>
                  </a-select>
                  <a-input :disabled="!canOperate" v-model:value="column.heightM" placeholder="高度 m" style="width: 96px" />
                  <a v-if="canOperate" href="javascript:void(0)" @click="removeColumn(support, cIndex)">删柱</a>
                </div>
                <a v-if="canOperate" href="javascript:void(0)" @click="addColumn(support)">加柱</a>
              </td>
              <td><a v-if="canOperate" href="javascript:void(0)" @click="removeSupport(unit, sIndex)">删墩台</a></td>
            </tr>
            <tr v-if="!unit.supports.length">
              <td colspan="4" class="empty-cell">尚未填写墩台。n 跨通常有 n+1 个墩台。</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="head-row sub">
        <h2 class="h">其他参数</h2>
        <a v-if="canOperate" href="javascript:void(0)" @click="addParam">加一项</a>
      </div>
      <p class="hint">扩展结构量。键、名称用中文或英文均可（如「桩径」），建模按专业同义认到目录项。墩柱高请在上方联表点「加柱」，不要放这里。</p>
      <table class="kv nested">
        <thead>
          <tr>
            <th>键</th>
            <th>名称</th>
            <th>值</th>
            <th>单位</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(item, index) in paramDraft" :key="index">
            <td><a-input :disabled="!canOperate" v-model:value="item.paramKey" placeholder="pileDiameterM" /></td>
            <td><a-input :disabled="!canOperate" v-model:value="item.label" placeholder="主梁梁高" /></td>
            <td><a-input :disabled="!canOperate" v-model:value="item.valueText" placeholder="未填" /></td>
            <td><a-input :disabled="!canOperate" v-model:value="item.unit" placeholder="m" /></td>
            <td><a v-if="canOperate" href="javascript:void(0)" @click="removeParam(index)">删</a></td>
          </tr>
          <tr v-if="!paramDraft.length">
            <td colspan="5" class="empty-cell">暂无扩展参数。识图写入或点「加一项」。</td>
          </tr>
        </tbody>
      </table>
      <h2 class="h">最新模型</h2>
      <table class="kv">
        <tbody v-if="latestModel">
          <tr><th>版本</th><td>v{{ latestModel.seq }}</td></tr>
          <tr><th>SAP</th><td>{{ latestModel.sapVersion || "未记录" }}</td></tr>
          <tr><th>文件</th><td>{{ latestModel.originalName }}</td></tr>
          <tr><th>说明</th><td>{{ latestModel.note || "—" }}</td></tr>
        </tbody>
        <tbody v-else>
          <tr><th>版本</th><td>暂无。建模完成后会出现在<a href="javascript:void(0)" @click="go('models')">模型版本</a>页，可预览并删除占盘版本。</td></tr>
        </tbody>
      </table>
    </div>
    <div>
      <div v-if="showConcreteHint" class="ba-flash">
        <span>建议启用 GB 50010-2002</span>
        <a href="javascript:void(0)" @click="go('scope')">处理</a>
      </div>
      <h2 class="h">已启用</h2>
      <div v-for="group in knowledgeGroups" :key="group.key" class="g">
        <div class="g-title">{{ group.title }}</div>
        <ul>
          <li v-for="doc in enabled.filter((d) => d.category === group.key)" :key="doc.id">
            {{ doc.name }}
          </li>
          <li v-if="!enabled.some((d) => d.category === group.key)" class="empty">无</li>
        </ul>
      </div>
      <a href="javascript:void(0)" @click="go('scope')">管理启用集</a>
    </div>
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: 1.3fr 0.9fr;
  gap: 32px;
}
.head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.h {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
}
.head-row .h {
  margin: 0;
}
.head-row.sub {
  margin: 16px 0 8px;
}
.unit-block {
  margin-bottom: 16px;
  padding: 8px 10px 4px;
  border: 1px solid var(--ba-line);
  border-radius: 6px;
}
.unit-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  margin-bottom: 6px;
}
.col-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}
.col-seq {
  color: var(--ba-muted);
  font-size: 12px;
  width: 32px;
}
.nested th {
  font-size: 12px;
}
.empty-cell {
  color: var(--ba-muted);
  font-size: 12px;
}
.kv {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 12px;
  font-size: 14px;
}
.kv th,
.kv td {
  border-bottom: 1px solid var(--ba-line);
  padding: 7px 0;
  text-align: left;
  vertical-align: middle;
}
.kv th {
  width: 108px;
  color: var(--ba-muted);
  font-weight: 400;
  padding-right: 12px;
}
.src {
  display: inline-block;
  margin-left: 6px;
  font-size: 11px;
  line-height: 16px;
  padding: 0 6px;
  border-radius: 999px;
  border: 1px solid var(--ba-line);
  color: var(--ba-muted);
  vertical-align: middle;
}
.src.drawing {
  border-color: #9db4ff;
  color: #3451c7;
  background: #eef2ff;
}
.src.manual {
  border-color: var(--ba-line);
  color: var(--ba-muted);
  background: var(--ba-subtle);
}
.hint {
  margin: 0 0 20px;
  color: var(--ba-muted);
  font-size: 12px;
}
.g {
  margin-bottom: 12px;
}
.g-title {
  font-size: 12px;
  color: var(--ba-muted);
  margin-bottom: 4px;
}
ul {
  margin: 0;
  padding-left: 16px;
}
li {
  font-size: 13px;
  line-height: 1.6;
}
.empty {
  color: var(--ba-muted);
  list-style: none;
  margin-left: -16px;
}
</style>
