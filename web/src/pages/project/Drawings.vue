<script setup lang="ts">
import { computed, inject, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { message } from "ant-design-vue";
import { deleteProjectFile, downloadProjectFile, uploadProjectFiles } from "@/api/project";
import { parseProjectDrawings } from "@/api/tasks";
import { projectDetailKey, reloadProjectKey } from "@/stores/projects";

const canOperate = inject("projectCanOperate", computed(() => false));

const kindLabel: Record<string, string> = {
  drawing: "图纸",
  cad: "CAD",
  other: "其他",
};

const parseStatusLabel: Record<string, string> = {
  uploaded: "已上传",
  parsing: "解析中",
  parsed: "已解析",
  failed: "失败",
};

const route = useRoute();
const router = useRouter();
const detail = inject(projectDetailKey);
const reload = inject(reloadProjectKey);
const kind = ref("drawing");
const uploading = ref(false);
const parsing = ref(false);
const inputRef = ref<HTMLInputElement | null>(null);
const projectId = computed(() => String(route.params.id));

const files = computed(() =>
  (detail?.value?.files ?? []).map((file) => ({
    id: file.id,
    name: file.originalName,
    kind: kindLabel[file.kind] ?? file.kind,
    status: parseStatusLabel[file.parseStatus] ?? file.parseStatus,
    size: file.sizeBytes ? formatSize(file.sizeBytes) : "—",
  })),
);

const columns = [
  { title: "文件", dataIndex: "name", ellipsis: true },
  { title: "归类", dataIndex: "kind", width: 64 },
  { title: "状态", dataIndex: "status", width: 72 },
  { title: "大小", dataIndex: "size", width: 80 },
  { title: "操作", dataIndex: "action", width: 96 },
];

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}

function pick() {
  inputRef.value?.click();
}

async function onPick(event: Event) {
  const input = event.target as HTMLInputElement;
  const selected = Array.from(input.files ?? []);
  input.value = "";
  if (!selected.length) return;
  uploading.value = true;
  try {
    const result = await uploadProjectFiles(projectId.value, kind.value, selected);
    const added = result.saved.filter((item) => !item.duplicate).length;
    const dup = result.saved.filter((item) => item.duplicate).length;
    if (added) message.success(`已上传 ${added} 份`);
    if (dup) message.info(`${dup} 份已存在，未重复入库`);
    for (const err of result.errors) {
      message.warning(err);
    }
    await reload?.();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "上传失败");
  } finally {
    uploading.value = false;
  }
}

async function onDownload(record: { id: number; name: string }) {
  try {
    await downloadProjectFile(projectId.value, record.id, record.name);
  } catch (error) {
    message.error(error instanceof Error ? error.message : "下载失败");
  }
}

async function onDelete(record: { id: number }) {
  try {
    await deleteProjectFile(projectId.value, record.id);
    message.success("已删除");
    await reload?.();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "删除失败");
  }
}

/** 一键全册识图：立刻 running，跳任务页看时间线；细抽请到任务页或问询出卡。 */
async function onParse() {
  parsing.value = true;
  try {
    await parseProjectDrawings(projectId.value);
    await reload?.();
    message.success("已开始识别，请到任务页查看进度。");
    await router.push({ name: "tasks", params: { id: projectId.value } });
  } catch (error) {
    message.error(error instanceof Error ? error.message : "识别失败");
  } finally {
    parsing.value = false;
  }
}
</script>

<template>
  <div class="toolbar">
    <a-select
      v-model:value="kind"
      style="width: 128px"
      :options="[
        { value: 'drawing', label: '图纸' },
        { value: 'cad', label: 'CAD' },
        { value: 'other', label: '其他' },
      ]"
    />
    <a-button v-if="canOperate" type="primary" :loading="uploading" @click="pick">上传文件</a-button>
    <a-button v-if="canOperate" :loading="parsing" @click="onParse">识别本项目</a-button>
    <input
      ref="inputRef"
      type="file"
      multiple
      accept=".pdf,.dwg,.dxf,application/pdf"
      hidden
      @change="onPick"
    />
  </div>
  <a-table
    :data-source="files"
    :columns="columns"
    :pagination="false"
    row-key="id"
    size="small"
  >
    <template #bodyCell="{ column, record }">
      <template v-if="column.dataIndex === 'action'">
        <button class="act" type="button" @click="onDownload(record)">下载</button>
        <a-popconfirm v-if="canOperate" title="删除后无法恢复，磁盘文件一并删除。" @confirm="onDelete(record)">
          <button class="act danger" type="button">删除</button>
        </a-popconfirm>
      </template>
    </template>
  </a-table>
  <p v-if="!files.length" class="hint">还没有图纸。可一次多选 PDF / DWG / DXF；JPG 等图片目前不会入库。</p>
  <p class="hint">
    「识别本项目」立刻开跑全册识图（后台进行，到任务页看时间线）。细抽 / 按指令重识请到
    <a href="javascript:void(0)" @click="router.push({ name: 'tasks', params: { id: projectId } })">任务</a>
    页起草，或在问询里让助手出卡后同意。CAD 本步不识。缺跨径会部分写入并在概览标缺口；已有值冲突须在任务页确认写入。
  </p>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  align-items: center;
}
.hint {
  margin-top: 12px;
  color: var(--ba-muted);
  font-size: 13px;
}
.toolbar :deep(.ant-btn) {
  height: 32px;
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
</style>
