<script setup lang="ts">
import { computed, inject, onUnmounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { message } from "ant-design-vue";
import {
  agreeTaskCard,
  confirmDrawingParse,
  dismissTaskCard,
  draftTaskCard,
  editTaskCard,
  listModelTasks,
  rejectDrawingParse,
} from "@/api/tasks";
import type { ModelTaskDetail, TaskKind } from "@/api/types";
import { formatDateTime, projectDetailKey, reloadProjectKey } from "@/stores/projects";

const canOperate = inject("projectCanOperate", computed(() => false));

const kindOptions: { value: TaskKind; label: string }[] = [
  { value: "drawing_full", label: "全册识图" },
  { value: "drawing_supplement", label: "补充识别" },
  { value: "modeling", label: "建模" },
  { value: "analysis", label: "分析" },
];

const pageKindOptions = [
  { value: "project_notes", label: "竣工说明" },
  { value: "general_layout", label: "总体布置" },
  { value: "elevation", label: "立面" },
  { value: "pier", label: "墩柱/盖梁" },
  { value: "foundation", label: "基础" },
  { value: "bearing", label: "支座" },
  { value: "cross_section", label: "横断面" },
  { value: "quantity", label: "数量表" },
];

const statusLabel: Record<string, string> = {
  proposed: "待同意",
  queued: "排队",
  running: "进行中",
  waiting: "待写入确认",
  done: "完成",
  failed: "失败",
  rejected: "已拒绝",
};

const kindLabel: Record<string, string> = {
  drawing_full: "全册识图",
  drawing_supplement: "补充识别",
  modeling: "建模",
  analysis: "分析",
  图纸识别: "全册识图",
};

const route = useRoute();
const reloadProject = inject(reloadProjectKey);
const detail = inject(projectDetailKey);
const projectId = computed(() => String(route.params.id));
const tasks = ref<ModelTaskDetail[]>([]);
const loading = ref(false);
const modalOpen = ref(false);
const creating = ref(false);
const actingId = ref<number | null>(null);
const proposedOpen = ref(false);
const editingId = ref<number | null>(null);
const form = reactive({
  kind: "drawing_supplement" as TaskKind,
  fileId: undefined as number | undefined,
  pageKinds: [] as string[],
  unitSeq: undefined as number | undefined,
  supportCode: "",
  directive: "",
});

const pdfFiles = computed(() =>
  (detail?.value?.files ?? []).filter((file) => file.originalName.toLowerCase().endsWith(".pdf")),
);

const proposed = computed(() => tasks.value.filter((item) => item.task.status === "proposed"));
const rest = computed(() => tasks.value.filter((item) => item.task.status !== "proposed"));
const proposedVisible = computed(() =>
  proposedOpen.value || proposed.value.length <= 5 ? proposed.value : proposed.value.slice(0, 5),
);
const hasRunning = computed(() =>
  tasks.value.some((item) => item.task.status === "running" || item.task.status === "queued"),
);

let pollTimer: ReturnType<typeof setInterval> | null = null;

async function load() {
  loading.value = true;
  try {
    tasks.value = await listModelTasks(projectId.value);
  } catch (error) {
    message.error(error instanceof Error ? error.message : "加载失败");
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  form.kind = "drawing_supplement";
  form.fileId = pdfFiles.value[0]?.id;
  form.pageKinds = [];
  form.unitSeq = undefined;
  form.supportCode = "";
  form.directive = "";
  editingId.value = null;
}

function openCreate() {
  resetForm();
  modalOpen.value = true;
}

function parseKinds(json?: string | null): string[] {
  if (!json) return [];
  try {
    const parsed = JSON.parse(json) as unknown;
    return Array.isArray(parsed) ? parsed.map(String) : [];
  } catch {
    return [];
  }
}

function openEdit(item: ModelTaskDetail) {
  const task = item.task;
  form.kind = (task.kind as TaskKind) || "drawing_supplement";
  form.fileId = task.fileId ?? undefined;
  form.pageKinds = parseKinds(task.pageKindsJson);
  form.unitSeq = task.unitSeq ?? undefined;
  form.supportCode = task.supportCode ?? "";
  form.directive = task.directive ?? "";
  editingId.value = task.id;
  modalOpen.value = true;
}

function kindText(kind: string) {
  return kindLabel[kind] ?? kind;
}

function fileName(fileId?: number | null) {
  if (fileId == null) return "本项目全部 PDF";
  return pdfFiles.value.find((file) => file.id === fileId)?.originalName ?? `图纸 #${fileId}`;
}

function kindsText(json?: string | null) {
  const kinds = parseKinds(json);
  if (!kinds.length) return "默认细看（说明 + 总布置/立面）";
  const labels = kinds.map((kind) => pageKindOptions.find((item) => item.value === kind)?.label ?? kind);
  return labels.join("、");
}

function isDrawing(kind: string) {
  return kind === "drawing_full" || kind === "drawing_supplement" || kind === "图纸识别";
}

async function submit() {
  if (form.kind === "drawing_supplement") {
    if (!form.fileId) {
      message.error("补充识别必须指定图纸");
      return;
    }
    if (!form.pageKinds.length) {
      message.error("补充识别必须选择要细看的页类");
      return;
    }
  }
  if (form.directive.length > 500) {
    message.error("本轮指令不能超过 500 字");
    return;
  }
  creating.value = true;
  const body = {
    kind: form.kind,
    fileId: form.fileId ?? null,
    pageKinds: form.pageKinds,
    unitSeq: form.unitSeq ?? null,
    supportCode: form.supportCode.trim() || null,
    directive: form.directive.trim() || null,
  };
  try {
    if (editingId.value != null) {
      await editTaskCard(projectId.value, editingId.value, body);
      message.success("已修改任务卡");
    } else {
      await draftTaskCard(projectId.value, body);
      message.success("任务卡已起草，同意后才会执行");
    }
    modalOpen.value = false;
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "保存失败");
  } finally {
    creating.value = false;
  }
}

async function onAgree(taskId: number, threadId?: number | null) {
  actingId.value = taskId;
  try {
    await agreeTaskCard(projectId.value, taskId, threadId);
    message.success("已同意，开始执行");
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "同意失败");
  } finally {
    actingId.value = null;
  }
}

async function onDismiss(taskId: number, threadId?: number | null) {
  actingId.value = taskId;
  try {
    await dismissTaskCard(projectId.value, taskId, threadId);
    message.success("已拒绝，未执行");
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "拒绝失败");
  } finally {
    actingId.value = null;
  }
}

function originText(origin?: string | null) {
  const map: Record<string, string> = {
    draft: "任务页起草",
    inquiry: "问询提议",
    drawing_button: "图纸一键识别",
    auto_supplement: "建模自动补识",
  };
  if (!origin) return "";
  return map[origin] ?? origin;
}

/** 作业链：来源 + 创建人 + 开跑人；自动补识创建人记系统 */
function jobMeta(task: ModelTaskDetail["task"]) {
  const bits: string[] = [];
  const origin = originText(task.origin);
  if (origin) bits.push(origin);
  if (task.createdByUsername) {
    bits.push("创建 " + task.createdByUsername);
  } else if (task.origin === "auto_supplement") {
    bits.push("创建 系统");
  }
  if (task.agreedByUsername) bits.push("开跑 " + task.agreedByUsername);
  return bits.join(" · ");
}

/** 有提案且仍在 waiting = 识图与账本冲突，不要和同意任务卡混用 */
function pendingConfirm(item: ModelTaskDetail) {
  return isDrawing(item.task.kind) && item.task.status === "waiting" && Boolean(item.task.proposalJson);
}

function conflictRows(json?: string | null) {
  if (!json) return [];
  try {
    const proposal = JSON.parse(json) as {
      conflicts?: { field?: string; before?: string; after?: string }[];
    };
    return (proposal.conflicts ?? []).filter((row) => row.field || row.before || row.after);
  } catch {
    return [];
  }
}

function proposalText(json?: string | null) {
  if (!json) return "";
  try {
    const proposal = JSON.parse(json) as {
      girderType?: string;
      layoutType?: string;
      material?: string;
      code?: string;
      region?: string;
      units?: {
        seq: number;
        spansM: number[];
        supports?: {
          code?: string;
          columns?: { heightM?: number }[];
        }[];
      }[];
    };
    const spans = (proposal.units ?? [])
      .map((unit) => {
        const piers = (unit.supports ?? [])
          .map((support) => {
            const heights = (support.columns ?? [])
              .map((column) => column.heightM)
              .filter((n): n is number => n != null)
              .join("/");
            return `${support.code ?? ""}${heights ? ":" + heights : ""}`;
          })
          .filter(Boolean)
          .join(",");
        return `第${unit.seq}联 ${(unit.spansM ?? []).join("+")} m${piers ? " 墩柱 " + piers : ""}`;
      })
      .join("；");
    return [proposal.code, proposal.girderType, proposal.layoutType, proposal.material, proposal.region, spans]
      .filter(Boolean)
      .join(" · ");
  } catch {
    return json;
  }
}

async function onConfirm(taskId: number) {
  actingId.value = taskId;
  try {
    await confirmDrawingParse(projectId.value, taskId);
    message.success("已写入账本");
    await load();
    await reloadProject?.();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "确认失败");
  } finally {
    actingId.value = null;
  }
}

async function onReject(taskId: number) {
  actingId.value = taskId;
  try {
    await rejectDrawingParse(projectId.value, taskId);
    message.success("已放弃，账本未改");
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "放弃失败");
  } finally {
    actingId.value = null;
  }
}

watch(projectId, load, { immediate: true });
watch(
  hasRunning,
  (running) => {
    if (pollTimer) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
    if (running) {
      pollTimer = setInterval(() => {
        load().catch(() => undefined);
      }, 4000);
    }
  },
  { immediate: true },
);
onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer);
});
</script>

<template>
  <div>
    <div class="toolbar">
      <p class="hint">
        干活页：起草任务卡，同意后才会跑 Agent。有项目只读即可看作业过程（来源、开跑人、时间线）。
        图纸页「识别本项目」会直接开跑全册。问询只能提议补充识别。进行中的任务不能取消。
      </p>
      <a-button type="primary" @click="openCreate">起草任务卡</a-button>
    </div>

    <section v-if="proposed.length" class="group">
      <div class="group-head">
        <strong>待同意</strong>
        <span>{{ proposed.length }} 张</span>
      </div>
      <div v-for="item in proposedVisible" :key="item.task.id" class="ba-list-item" style="display: block">
        <div class="head">
          <span class="id">T-{{ item.task.id }}</span>
          <strong>{{ item.task.title }}</strong>
          <span class="kind">{{ kindText(item.task.kind) }}</span>
          <span class="status">{{ statusLabel[item.task.status] }}</span>
        </div>
        <div class="meta">
          {{ fileName(item.task.fileId) }} · {{ kindsText(item.task.pageKindsJson) }}
          <template v-if="item.task.unitSeq != null"> · 第{{ item.task.unitSeq }}联</template>
          <template v-if="item.task.supportCode"> · {{ item.task.supportCode }}</template>
          <template v-if="jobMeta(item.task)"> · {{ jobMeta(item.task) }}</template>
        </div>
        <p v-if="item.task.proposeReason" class="reason">提议原因：{{ item.task.proposeReason }}</p>
        <p v-if="item.task.directive" class="directive">本轮指令：{{ item.task.directive }}</p>
        <div v-if="canOperate" class="actions">
          <a-button type="primary" size="small" :loading="actingId === item.task.id" @click="onAgree(item.task.id, item.task.inquiryThreadId)">
            同意并执行
          </a-button>
          <a-button size="small" :disabled="actingId === item.task.id" @click="onDismiss(item.task.id, item.task.inquiryThreadId)">
            拒绝
          </a-button>
          <a-button size="small" :disabled="actingId === item.task.id" @click="openEdit(item)">改范围/指令</a-button>
        </div>
      </div>
      <button
        v-if="proposed.length > 5"
        class="more"
        type="button"
        @click="proposedOpen = !proposedOpen"
      >
        {{ proposedOpen ? "收起" : `展开其余 ${proposed.length - 5} 张` }}
      </button>
    </section>

    <p v-if="!loading && !tasks.length" class="hint">还没有任务。可在本页起草，或到图纸页一键识别。</p>
    <div class="ba-list">
      <div v-for="item in rest" :key="item.task.id" class="ba-list-item" style="display: block">
        <div class="head">
          <span class="id">T-{{ item.task.id }}</span>
          <strong>{{ item.task.title }}</strong>
          <span class="kind">{{ kindText(item.task.kind) }}</span>
          <span class="status">{{ statusLabel[item.task.status] ?? item.task.status }}</span>
        </div>
        <div class="meta">
          {{ formatDateTime(item.task.createdAt) }}
          <template v-if="jobMeta(item.task)"> · {{ jobMeta(item.task) }}</template>
          <span v-if="item.task.parentTaskId"> · 由建模 T-{{ item.task.parentTaskId }} 自动启动</span>
        </div>
        <p v-if="item.task.kind === 'modeling' && item.task.status === 'running'" class="hint">
          进行中：硬缺口会自动开补充识别。若子任务出现「确认写入」，确认后才会继续建模。
        </p>
        <div v-if="pendingConfirm(item)" class="proposal">
          <p>识别结果与账本不一致，确认后才会按「覆盖后」写入（这不是「同意开跑」）：</p>
          <table v-if="conflictRows(item.task.proposalJson).length" class="conflict-table">
            <thead>
              <tr>
                <th>字段</th>
                <th>覆盖前（账本）</th>
                <th>覆盖后（本轮识别）</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in conflictRows(item.task.proposalJson)" :key="index">
                <td>{{ row.field || "—" }}</td>
                <td>{{ row.before || "未给" }}</td>
                <td>{{ row.after || "未给" }}</td>
              </tr>
            </tbody>
          </table>
          <p v-else>{{ proposalText(item.task.proposalJson) }}</p>
          <div v-if="canOperate" class="actions">
            <a-button
              type="primary"
              size="small"
              :loading="actingId === item.task.id"
              @click="onConfirm(item.task.id)"
            >
              确认写入
            </a-button>
            <a-button size="small" :disabled="actingId === item.task.id" @click="onReject(item.task.id)">
              放弃
            </a-button>
          </div>
        </div>
        <ul>
          <li v-for="event in item.events" :key="event.id">
            <span>{{ event.actorUsername || "系统" }} · {{ formatDateTime(event.createdAt) }}</span>
            {{ event.body }}
          </li>
        </ul>
      </div>
    </div>

    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '修改任务卡' : '起草任务卡'"
      :confirm-loading="creating"
      @ok="submit"
    >
      <div class="form">
        <label>工种</label>
        <a-select v-model:value="form.kind" :options="kindOptions" />
        <p v-if="form.kind === 'modeling'" class="hint">建模建本幅全部联，不必选图纸。缺尺寸时后台自动补识别，或到概览参数袋手填。</p>
        <label>图纸{{ form.kind === "drawing_supplement" ? "（必选）" : "（可空，空则本项目全部 PDF）" }}</label>
        <a-select
          v-model:value="form.fileId"
          allow-clear
          :options="pdfFiles.map((file) => ({ value: file.id, label: file.originalName }))"
          placeholder="选择一份 PDF"
        />
        <label>页类{{ form.kind === "drawing_supplement" ? "（必选）" : "（可空）" }}</label>
        <a-select
          v-model:value="form.pageKinds"
          mode="multiple"
          :options="pageKindOptions"
          placeholder="补充识别要细看的页"
        />
        <label>联号（可空）</label>
        <a-input-number v-model:value="form.unitSeq" :min="1" style="width: 100%" />
        <label>墩台编号（可空）</label>
        <a-input v-model:value="form.supportCode" placeholder="例如 1# / P1" />
        <label>本轮指令（可空，最多 500 字）</label>
        <a-textarea v-model:value="form.directive" :maxlength="500" :rows="3" show-count placeholder="只约束本轮细看重点，不能覆盖空写/冲突规则" />
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}
.hint {
  margin: 0;
  color: var(--ba-muted);
  font-size: 13px;
}
.group {
  margin-bottom: 16px;
  padding: 10px 12px;
  border: 1px solid var(--ba-warn-line, #f0d78c);
  background: var(--ba-warn-bg, #fff8e6);
  border-radius: 6px;
}
.group-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
}
.group .ba-list-item {
  margin-bottom: 8px;
  background: #fff;
  padding: 8px 10px;
  border-radius: 6px;
}
.head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.id {
  color: var(--ba-muted);
  font-size: 12px;
}
.kind {
  font-size: 12px;
  color: var(--ba-muted);
  border: 1px solid var(--ba-line);
  border-radius: 999px;
  padding: 0 7px;
}
.status {
  margin-left: auto;
  font-size: 12px;
  color: var(--ba-muted);
}
.meta,
li span {
  color: var(--ba-muted);
  font-size: 12px;
}
.reason,
.directive {
  margin: 6px 0 0;
  font-size: 13px;
}
.actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  flex-wrap: wrap;
}
.proposal {
  margin-top: 8px;
  padding: 8px 10px;
  background: #fff8e6;
  border: 1px solid #f0d78c;
  border-radius: 6px;
  font-size: 13px;
}
.proposal p {
  margin: 0 0 6px;
}
.conflict-table {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0 10px;
  font-size: 12px;
}
.conflict-table th,
.conflict-table td {
  border: 1px solid #e8d59a;
  padding: 6px 8px;
  text-align: left;
  vertical-align: top;
  word-break: break-word;
}
.conflict-table th {
  background: #fff3c4;
  font-weight: 600;
  white-space: nowrap;
}
.conflict-table td:first-child {
  width: 88px;
  font-weight: 600;
}
.more {
  border: 0;
  background: none;
  color: var(--ba-link);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  padding: 4px 0 0;
}
ul {
  margin: 8px 0 0;
  padding-left: 16px;
}
li {
  font-size: 13px;
  line-height: 1.6;
}
.form {
  display: grid;
  gap: 8px;
}
.form label {
  font-size: 12px;
  color: var(--ba-muted);
}
</style>
