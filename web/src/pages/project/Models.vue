<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { message } from "ant-design-vue";
import { deleteSapModel, downloadSapModel, listSapModels } from "@/api/models";
import type { ProjectSapModel, SapModelPreview } from "@/api/types";
import { canOperateProject } from "@/stores/session";

const KIND_COLOR: Record<string, string> = {
  girder: "#1f6feb",
  virtual: "#8c959f",
  diaphragm: "#8250df",
  pier: "#bc4c00",
  pile: "#6e7781",
  cap: "#1a7f37",
  tie: "#0969da",
  bearing: "#cf222e",
};

const route = useRoute();
const projectId = computed(() => String(route.params.id));
const canOperate = computed(() => canOperateProject(projectId.value));
const models = ref<ProjectSapModel[]>([]);
const loading = ref(false);
const selectedId = ref<number | null>(null);
const view = ref<"xy" | "xz">("xy");

const columns = [
  { title: "版本", dataIndex: "seq", width: 72 },
  { title: "SAP", dataIndex: "sapVersion", width: 96 },
  { title: "文件", dataIndex: "originalName", ellipsis: true },
  { title: "节点", dataIndex: "jointCount", width: 64 },
  { title: "杆件", dataIndex: "frameCount", width: 64 },
  { title: "大小", dataIndex: "size", width: 88 },
  { title: "时间", dataIndex: "createdAt", width: 168 },
  { title: "操作", dataIndex: "action", width: 112 },
];

const rows = computed(() =>
  models.value.map((item) => ({
    ...item,
    seqLabel: `v${item.seq}`,
    sapLabel: item.sapVersion || "—",
    size: formatSize(item.sizeBytes),
    jointLabel: item.jointCount ?? "—",
    frameLabel: item.frameCount ?? "—",
    timeLabel: formatTime(item.createdAt),
  })),
);

const selected = computed(() => models.value.find((item) => item.id === selectedId.value) ?? null);

const preview = computed(() => parsePreview(selected.value?.previewJson));

const svg = computed(() => buildSvg(preview.value, view.value));

async function load() {
  loading.value = true;
  try {
    models.value = await listSapModels(projectId.value);
    if (selectedId.value && !models.value.some((item) => item.id === selectedId.value)) {
      selectedId.value = null;
    }
    if (selectedId.value == null && models.value.length) {
      selectedId.value = models.value[0].id;
    }
  } catch (error) {
    models.value = [];
    message.error(error instanceof Error ? error.message : "加载模型失败");
  } finally {
    loading.value = false;
  }
}

watch(projectId, load, { immediate: true });

function onRowClick(record: ProjectSapModel) {
  selectedId.value = record.id;
}

async function onDownload(record: ProjectSapModel) {
  try {
    await downloadSapModel(projectId.value, record.id, record.originalName);
  } catch (error) {
    message.error(error instanceof Error ? error.message : "下载失败");
  }
}

async function onDelete(record: ProjectSapModel) {
  try {
    await deleteSapModel(projectId.value, record.id);
    message.success("已删除该版本");
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "删除失败");
  }
}

function formatSize(bytes: number) {
  if (!bytes) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}

function formatTime(value: string | null | undefined) {
  if (!value) return "—";
  return value.replace("T", " ").slice(0, 19);
}

function parsePreview(raw: string | null | undefined): SapModelPreview | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as SapModelPreview;
    if (!parsed?.joints?.length) return parsed;
    return parsed;
  } catch {
    return null;
  }
}

function buildSvg(data: SapModelPreview | null, plane: "xy" | "xz") {
  const joints = data?.joints ?? [];
  const frames = data?.frames ?? [];
  if (!joints.length) {
    return { width: 640, height: 280, paths: [] as { d: string; color: string }[], dots: [] as { cx: number; cy: number }[] };
  }
  const byId = new Map(joints.map((j) => [String(j.id), j]));
  const xs = joints.map((j) => j.x);
  const ys = joints.map((j) => (plane === "xy" ? j.y : (j.z ?? 0)));
  const minX = Math.min(...xs);
  const maxX = Math.max(...xs);
  const minY = Math.min(...ys);
  const maxY = Math.max(...ys);
  const spanX = Math.max(maxX - minX, 0.01);
  const spanY = Math.max(maxY - minY, 0.01);
  const pad = 24;
  const width = 640;
  const height = 280;
  const sx = (width - pad * 2) / spanX;
  const sy = (height - pad * 2) / spanY;
  const scale = Math.min(sx, sy);
  const mapX = (x: number) => pad + (x - minX) * scale;
  const mapY = (y: number) => height - pad - (y - minY) * scale;
  const paths = frames.flatMap((frame) => {
    const a = byId.get(String(frame.i));
    const b = byId.get(String(frame.j));
    if (!a || !b) return [];
    const ya = plane === "xy" ? a.y : (a.z ?? 0);
    const yb = plane === "xy" ? b.y : (b.z ?? 0);
    return [{
      d: `M ${mapX(a.x)} ${mapY(ya)} L ${mapX(b.x)} ${mapY(yb)}`,
      color: KIND_COLOR[frame.kind ?? ""] ?? "#57606a",
    }];
  });
  const dots = joints.map((j) => ({
    cx: mapX(j.x),
    cy: mapY(plane === "xy" ? j.y : (j.z ?? 0)),
  }));
  return { width, height, paths, dots };
}
</script>

<template>
  <p class="hint">
    每个版本对应一份 SAP2000 模型。点一行查看该版本线框；不需要的版本请删除，以免占盘。本任务不跑分析，没有周期与质量参与比。
  </p>
  <a-table
    :data-source="rows"
    :columns="columns"
    :pagination="false"
    :loading="loading"
    row-key="id"
    size="small"
    :row-class-name="(record: ProjectSapModel) => (record.id === selectedId ? 'row-on' : '')"
    :custom-row="(record: ProjectSapModel) => ({ onClick: () => onRowClick(record) })"
  >
    <template #bodyCell="{ column, record }">
      <template v-if="column.dataIndex === 'seq'">v{{ record.seq }}</template>
      <template v-else-if="column.dataIndex === 'sapVersion'">{{ record.sapVersion || "—" }}</template>
      <template v-else-if="column.dataIndex === 'jointCount'">{{ record.jointCount ?? "—" }}</template>
      <template v-else-if="column.dataIndex === 'frameCount'">{{ record.frameCount ?? "—" }}</template>
      <template v-else-if="column.dataIndex === 'size'">{{ record.size }}</template>
      <template v-else-if="column.dataIndex === 'createdAt'">{{ record.timeLabel }}</template>
      <template v-else-if="column.dataIndex === 'action'">
        <button class="act" type="button" @click.stop="onDownload(record)">下载</button>
        <a-popconfirm v-if="canOperate" title="删除后无法恢复，磁盘上的该版本模型一并删除。" @confirm="onDelete(record)">
          <button class="act danger" type="button" @click.stop>删除</button>
        </a-popconfirm>
      </template>
    </template>
  </a-table>
  <p v-if="!models.length && !loading" class="hint">
    还没有模型版本。建模任务生成 .sdb 后会出现在这里，并带该版本的几何预览。建模图尚未接入时列表为空。
  </p>

  <div v-if="selected" class="preview">
    <div class="preview-head">
      <h2 class="h">v{{ selected.seq }} · {{ selected.originalName }}</h2>
      <div class="views">
        <button type="button" :class="{ on: view === 'xy' }" @click="view = 'xy'">平面 XY</button>
        <button type="button" :class="{ on: view === 'xz' }" @click="view = 'xz'">立面 XZ</button>
      </div>
    </div>
    <p class="meta">
      SAP {{ selected.sapVersion || "未记录版本" }}
      · 节点 {{ selected.jointCount ?? "—" }}
      · 杆件 {{ selected.frameCount ?? "—" }}
      · {{ formatSize(selected.sizeBytes) }}
    </p>
    <p v-if="selected.note" class="note">{{ selected.note }}</p>
    <div v-if="svg.paths.length || svg.dots.length" class="canvas">
      <svg :viewBox="`0 0 ${svg.width} ${svg.height}`" class="wire">
        <path
          v-for="(path, index) in svg.paths"
          :key="'f' + index"
          :d="path.d"
          fill="none"
          :stroke="path.color"
          stroke-width="1.4"
        />
        <circle
          v-for="(dot, index) in svg.dots"
          :key="'j' + index"
          :cx="dot.cx"
          :cy="dot.cy"
          r="2.2"
          fill="#24292f"
        />
      </svg>
      <ul class="legend">
        <li v-for="(color, kind) in KIND_COLOR" :key="kind">
          <i :style="{ background: color }" />{{ kind }}
        </li>
      </ul>
    </div>
    <p v-else class="hint">
      该版本还没有几何预览（节点/杆件）。可下载 .sdb 在 SAP2000 中打开。建模接入后会写入线框供本页展示。
    </p>
  </div>
</template>

<style scoped>
.hint {
  margin: 0 0 12px;
  color: var(--ba-muted);
  font-size: 13px;
}
.h {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}
.act {
  border: 0;
  background: none;
  padding: 0;
  margin-right: 10px;
  color: var(--ba-link);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}
.act.danger {
  color: #cf222e;
}
.preview {
  margin-top: 20px;
  border: 1px solid var(--ba-line);
  border-radius: 8px;
  padding: 12px 14px 16px;
}
.preview-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}
.views button {
  border: 1px solid var(--ba-line);
  background: #fff;
  border-radius: 999px;
  padding: 2px 10px;
  margin-left: 6px;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
}
.views button.on {
  border-color: var(--ba-link);
  color: var(--ba-link);
}
.meta,
.note {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--ba-muted);
}
.note {
  white-space: pre-wrap;
}
.canvas {
  margin-top: 12px;
}
.wire {
  width: 100%;
  height: 280px;
  background: #f6f8fa;
  border-radius: 6px;
}
.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
  font-size: 12px;
  color: var(--ba-muted);
}
.legend i {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  margin-right: 4px;
  vertical-align: -1px;
}
:deep(.row-on) td {
  background: #eef4ff;
}
</style>
