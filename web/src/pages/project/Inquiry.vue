<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { message } from "ant-design-vue";
import {
  createInquiryThread,
  deleteInquiryThread,
  getInquiryThread,
  listInquiryThreads,
  postInquiryMessage,
} from "@/api/inquiry";
import { agreeTaskCard, dismissTaskCard } from "@/api/tasks";
import type { InquiryThread, InquiryThreadDetail, ModelTaskDetail } from "@/api/types";
import { formatDateTime } from "@/stores/projects";
import { canOperateProject } from "@/stores/session";

const kindLabel: Record<string, string> = {
  drawing_full: "全册识图",
  drawing_supplement: "补充识别",
  modeling: "建模",
  analysis: "分析",
};

const route = useRoute();
const router = useRouter();
const projectId = computed(() => String(route.params.id));
const canOperate = computed(() => canOperateProject(projectId.value));
const threads = ref<InquiryThread[]>([]);
const currentId = ref<number | null>(null);
const detail = ref<InquiryThreadDetail | null>(null);
const draft = ref("");
const sending = ref(false);
const actingId = ref<number | null>(null);

async function loadThreads(selectId?: number | null) {
  threads.value = await listInquiryThreads(projectId.value);
  const next = selectId ?? currentId.value;
  const exists = threads.value.find((item) => item.id === next);
  if (exists) {
    await open(exists.id);
  } else if (threads.value.length) {
    await open(threads.value[0].id);
  } else {
    currentId.value = null;
    detail.value = null;
  }
}

async function open(id: number) {
  currentId.value = id;
  detail.value = await getInquiryThread(projectId.value, id);
}

async function onCreate() {
  try {
    const thread = await createInquiryThread(projectId.value);
    message.success("已新建会话");
    await loadThreads(thread.id);
  } catch (error) {
    message.error(error instanceof Error ? error.message : "新建失败");
  }
}

async function onDelete(id: number) {
  try {
    await deleteInquiryThread(projectId.value, id);
    message.success("已删除会话");
    if (currentId.value === id) currentId.value = null;
    await loadThreads();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "删除失败");
  }
}

async function send(value?: string) {
  const text = String(value ?? draft.value).trim();
  if (!text || currentId.value == null) return;
  sending.value = true;
  try {
    detail.value = await postInquiryMessage(projectId.value, currentId.value, text);
    draft.value = "";
    threads.value = await listInquiryThreads(projectId.value);
  } catch (error) {
    message.error(error instanceof Error ? error.message : "发送失败");
  } finally {
    sending.value = false;
  }
}

function kindsText(item: ModelTaskDetail) {
  try {
    const kinds = JSON.parse(item.task.pageKindsJson || "[]") as string[];
    return Array.isArray(kinds) && kinds.length ? kinds.join("、") : "未指定页类";
  } catch {
    return "未指定页类";
  }
}

async function onAgreeCard(item: ModelTaskDetail) {
  actingId.value = item.task.id;
  try {
    await agreeTaskCard(projectId.value, item.task.id, currentId.value);
    message.success("已提交，转到任务页查看进度");
    await router.push({ name: "tasks", params: { id: projectId.value } });
  } catch (error) {
    message.error(error instanceof Error ? error.message : "同意失败");
  } finally {
    actingId.value = null;
  }
}

async function onDismissCard(item: ModelTaskDetail) {
  actingId.value = item.task.id;
  try {
    await dismissTaskCard(projectId.value, item.task.id, currentId.value);
    message.success("已拒绝该任务卡");
    if (currentId.value != null) await open(currentId.value);
  } catch (error) {
    message.error(error instanceof Error ? error.message : "拒绝失败");
  } finally {
    actingId.value = null;
  }
}

watch(projectId, () => loadThreads().catch((error) => {
  message.error(error instanceof Error ? error.message : "加载失败");
}), { immediate: true });
</script>

<template>
  <div class="wrap">
    <aside>
      <div class="side-head">
        <div class="side-title">会话</div>
        <button class="act" type="button" @click="onCreate">新会话</button>
      </div>
      <p v-if="!threads.length" class="empty">还没有问询。点「新会话」开始。</p>
      <div v-for="thread in threads" :key="thread.id" class="thread-row">
        <button
          class="thread"
          :class="{ on: thread.id === currentId }"
          type="button"
          @click="open(thread.id)"
        >
          <div>{{ thread.title }}</div>
          <span>{{ formatDateTime(thread.updatedAt) }}</span>
        </button>
        <a-popconfirm title="删除后无法恢复。仅删你自己的会话。" @confirm="onDelete(thread.id)">
          <button class="act danger" type="button">删</button>
        </a-popconfirm>
      </div>
    </aside>
    <section class="chat">
      <p class="note">
        问询只读：不改账本。会话按你本人私有，同项目其他用户（含超管）看不到你的记录。
        需要细抽结构量时，助手最多出一张补充识别卡；同意后任务是项目级的，同事能在任务页看到并执行。
        全册重识请到图纸页；建模/分析请到任务页起草。
      </p>
      <template v-if="detail">
        <div class="messages">
          <div
            v-for="msg in detail.messages"
            :key="msg.id"
            class="ba-chat-bubble"
            :class="msg.role"
          >
            {{ msg.body }}
          </div>
          <div v-for="card in detail.proposedCards ?? []" :key="card.task.id" class="task-card">
            <div class="card-title">待同意 · {{ kindLabel[card.task.kind] ?? card.task.kind }}</div>
            <p v-if="card.task.proposeReason">{{ card.task.proposeReason }}</p>
            <p class="card-meta">页类：{{ kindsText(card) }}</p>
            <p v-if="card.task.directive" class="card-meta">指令：{{ card.task.directive }}</p>
            <div v-if="canOperate" class="card-actions">
              <a-button type="primary" size="small" :loading="actingId === card.task.id" @click="onAgreeCard(card)">
                同意并去任务页
              </a-button>
              <a-button size="small" :disabled="actingId === card.task.id" @click="onDismissCard(card)">
                拒绝
              </a-button>
            </div>
            <p v-else class="card-meta">只读账号不能同意或拒绝任务卡。</p>
          </div>
          <p v-if="!detail.messages.length" class="empty">在下方提问，例如：跨径组合是什么？</p>
        </div>
        <a-input-search
          v-model:value="draft"
          placeholder="询问本项目，例如：跨径组合是什么？"
          enter-button="发送"
          :loading="sending"
          @search="send"
        />
      </template>
      <p v-else class="empty">请选择或新建一个会话。</p>
    </section>
  </div>
</template>

<style scoped>
.wrap {
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 16px;
  min-height: 440px;
}
aside {
  border: 1px solid var(--ba-line);
  border-radius: 6px;
  padding: 10px;
}
.side-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.side-title {
  font-weight: 600;
  font-size: 12px;
  color: var(--ba-muted);
}
.thread-row {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  margin-bottom: 2px;
}
.thread {
  flex: 1;
  display: block;
  text-align: left;
  border: 0;
  background: transparent;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}
.thread span {
  color: var(--ba-muted);
  font-size: 12px;
}
.thread.on {
  background: var(--ba-subtle);
}
.chat {
  border: 1px solid var(--ba-line);
  border-radius: 6px;
  padding: 12px;
  display: flex;
  flex-direction: column;
}
.note {
  margin: 0 0 10px;
  color: var(--ba-muted);
  font-size: 12px;
}
.messages {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
  min-height: 240px;
}
.empty {
  color: var(--ba-muted);
  font-size: 13px;
}
.task-card {
  border: 1px solid var(--ba-warn-line, #f0d78c);
  background: var(--ba-warn-bg, #fff8e6);
  border-radius: 6px;
  padding: 10px;
  font-size: 13px;
}
.card-title {
  font-weight: 600;
  margin-bottom: 4px;
}
.task-card p {
  margin: 0 0 4px;
}
.card-meta {
  color: var(--ba-muted);
  font-size: 12px;
}
.card-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.act {
  border: 0;
  background: none;
  padding: 0;
  color: var(--ba-link);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  white-space: nowrap;
}
.act.danger {
  color: #cf222e;
  margin-top: 8px;
}
</style>
