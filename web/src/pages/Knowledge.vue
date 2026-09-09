<script setup lang="ts">
import { computed, onUnmounted, reactive, ref } from "vue";
import { Modal, message } from "ant-design-vue";
import {
  deleteKnowledgeDocument,
  downloadKnowledgeDocument,
  listKnowledgeDocuments,
  parseKnowledgeDocument,
  mergeKnowledgeDocument,
  splitKnowledgeDocument,
  embedKnowledgeDocument,
  uploadKnowledgeDocument,
} from "@/api/knowledge";
import type { KnowledgeDocument } from "@/api/types";
import {
  formatKnowledgeDate,
  groupKnowledgeFamilies,
  knowledgeGroups,
  type KnowledgeFamily,
} from "@/stores/knowledge";
import { canWriteKnowledge } from "@/stores/session";

const parseStatusLabel: Record<string, string> = {
  unparsed: "未解析",
  queued: "排队解析",
  parsing: "解析中",
  parsed: "已解析",
  failed: "解析失败",
};

const mergeStatusLabel: Record<string, string> = {
  unmerged: "未合并",
  queued: "排队合并",
  merging: "合并中",
  merged: "已合并",
  failed: "合并失败",
};

const splitStatusLabel: Record<string, string> = {
  unsplit: "未分割",
  queued: "排队分割",
  splitting: "分割中",
  split: "已分割",
  failed: "分割失败",
};

const embedStatusLabel: Record<string, string> = {
  unembedded: "未嵌入",
  queued: "排队嵌入",
  embedding: "嵌入中",
  embedded: "已嵌入",
  failed: "嵌入失败",
};

const docs = ref<KnowledgeDocument[]>([]);
const loading = ref(false);
const uploading = ref(false);
const modalOpen = ref(false);
const selected = reactive<Record<string, number>>({});
let pollTimer: ReturnType<typeof setInterval> | null = null;

const form = reactive({
  name: "",
  category: "code",
  familyCode: "",
  region: "",
  specialty: "",
  effectiveFrom: "",
  effectiveTo: "",
  file: null as File | null,
});

const grouped = computed(() =>
  knowledgeGroups.map((group) => ({
    ...group,
    families: groupKnowledgeFamilies(docs.value.filter((doc) => doc.category === group.key)),
  })),
);

function currentDoc(family: KnowledgeFamily) {
  const id = selected[family.key] ?? family.latest.id;
  return family.versions.find((item) => item.id === id) ?? family.latest;
}

async function load(silent = false) {
  if (!silent) loading.value = true;
  try {
    docs.value = await listKnowledgeDocuments();
    for (const family of groupKnowledgeFamilies(docs.value)) {
      if (selected[family.key] == null) {
        selected[family.key] = family.latest.id;
      }
    }
    if (docs.value.some((doc) => isBusy(doc))) {
      startPoll();
    } else {
      stopPoll();
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : "加载失败");
  } finally {
    if (!silent) loading.value = false;
  }
}

function startPoll() {
  if (pollTimer) return;
  pollTimer = setInterval(() => {
    void load(true);
  }, 3000);
}

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

onUnmounted(stopPoll);

function openUpload() {
  form.name = "";
  form.category = "code";
  form.familyCode = "";
  form.region = "";
  form.specialty = "";
  form.effectiveFrom = "";
  form.effectiveTo = "";
  form.file = null;
  modalOpen.value = true;
}

function onFile(event: Event) {
  const input = event.target as HTMLInputElement;
  form.file = input.files?.[0] ?? null;
}

async function submitUpload() {
  if (!form.name.trim()) {
    message.error("请填写文献名称");
    return;
  }
  if (!form.file) {
    message.error("请选择 PDF 文件");
    return;
  }
  uploading.value = true;
  try {
    const body = new FormData();
    body.append("name", form.name.trim());
    body.append("category", form.category);
    if (form.familyCode.trim()) body.append("familyCode", form.familyCode.trim());
    if (form.region.trim()) body.append("region", form.region.trim());
    if (form.specialty.trim()) body.append("specialty", form.specialty.trim());
    if (form.effectiveFrom) body.append("effectiveFrom", form.effectiveFrom);
    if (form.effectiveTo) body.append("effectiveTo", form.effectiveTo);
    body.append("file", form.file);
    const result = await uploadKnowledgeDocument(body);
    if (result.duplicate) {
      message.info("相同 PDF 已在总库中，未重复登记");
    } else {
      message.success("已加入总库（未解析）");
    }
    modalOpen.value = false;
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "上传失败");
  } finally {
    uploading.value = false;
  }
}

async function onDownload(family: KnowledgeFamily) {
  const doc = currentDoc(family);
  if (!doc.storagePath) {
    message.warning("该文献还没有 PDF");
    return;
  }
  try {
    await downloadKnowledgeDocument(doc.id, doc.originalName || `${doc.name}.pdf`);
  } catch (error) {
    message.error(error instanceof Error ? error.message : "下载失败");
  }
}

async function onDelete(family: KnowledgeFamily) {
  const doc = currentDoc(family);
  try {
    await deleteKnowledgeDocument(doc.id);
    message.success("已删除");
    delete selected[family.key];
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "删除失败");
  }
}

function isBusy(doc: KnowledgeDocument) {
  return doc.parseStatus === "parsing" || doc.parseStatus === "queued"
    || doc.mergeStatus === "merging" || doc.mergeStatus === "queued"
    || doc.splitStatus === "splitting" || doc.splitStatus === "queued"
    || doc.embedStatus === "embedding" || doc.embedStatus === "queued";
}

async function onParse(family: KnowledgeFamily, force = false) {
  const doc = currentDoc(family);
  if (!doc.storagePath) {
    message.warning("该文献还没有 PDF");
    return;
  }
  if (isBusy(doc)) {
    return;
  }
  try {
    const result = await parseKnowledgeDocument(doc.id, force);
    if (result.needConfirm) {
      Modal.confirm({
        title: "重新解析？",
        content: "将清空 parse/ 解析结果（含附图）并整本重跑。已有 merge/ 与 split/ 也会清掉，已写入 Milvus 的向量一并作废，PDF 保留。",
        async onOk() {
          await onParse(family, true);
        },
      });
      return;
    }
    message.success("已开始解析");
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "解析失败");
  }
}

async function onMerge(family: KnowledgeFamily, force = false) {
  const doc = currentDoc(family);
  if (doc.parseStatus !== "parsed") {
    message.warning("请先解析成功再合并");
    return;
  }
  if (isBusy(doc)) {
    return;
  }
  try {
    const result = await mergeKnowledgeDocument(doc.id, force);
    if (result.needConfirm) {
      Modal.confirm({
        title: "重新合并？",
        content: "只重写 merge/ 下的合并结果，不会改 parse/。已有 split/ 会清掉，已写入 Milvus 的向量一并作废。",
        async onOk() {
          await onMerge(family, true);
        },
      });
      return;
    }
    message.success("已开始合并");
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "合并失败");
  }
}

async function onSplit(family: KnowledgeFamily, force = false) {
  const doc = currentDoc(family);
  if ((doc.mergeStatus || "unmerged") !== "merged") {
    message.warning("请先合并成功再分割");
    return;
  }
  if (isBusy(doc)) {
    return;
  }
  try {
    const result = await splitKnowledgeDocument(doc.id, force);
    if (result.needConfirm) {
      Modal.confirm({
        title: "重新分割？",
        content: "只重写 split/ 下的分割结果，不会改 parse/ 与 merge/。已写入 Milvus 的向量会作废。",
        async onOk() {
          await onSplit(family, true);
        },
      });
      return;
    }
    message.success("已开始分割");
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "分割失败");
  }
}

async function onEmbed(family: KnowledgeFamily, force = false) {
  const doc = currentDoc(family);
  if ((doc.splitStatus || "unsplit") !== "split") {
    message.warning("请先分割成功再嵌入");
    return;
  }
  if (isBusy(doc)) {
    return;
  }
  try {
    const result = await embedKnowledgeDocument(doc.id, force);
    if (result.needConfirm) {
      Modal.confirm({
        title: "重新嵌入？",
        content: "只重写该文献在本机 Milvus 的向量行，不改 parse/、merge/、split/ 磁盘文件。",
        async onOk() {
          await onEmbed(family, true);
        },
      });
      return;
    }
    message.success("已开始嵌入");
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "嵌入失败");
  }
}

function scrollTo(key: string) {
  document.getElementById(`kb-${key}`)?.scrollIntoView({ behavior: "smooth", block: "start" });
}

load();
</script>

<template>
  <div>
    <div class="head">
      <div>
        <h1 class="ba-page-title">知识库</h1>
        <p class="ba-page-sub">公司目录。项目里再勾选启用范围。上传后按「解析 → 合并 → 分割 → 嵌入」四步走，每步独立；嵌入只写本机向量库，不改前三步磁盘。</p>
      </div>
      <a-button v-if="canWriteKnowledge" type="primary" @click="openUpload">上传 PDF</a-button>
    </div>

    <div class="filters">
      <button
        v-for="group in grouped"
        :key="group.key"
        type="button"
        class="chip"
        @click="scrollTo(group.key)"
      >
        {{ group.title }}
        <span>{{ group.families.length }}</span>
      </button>
    </div>

    <section v-for="group in grouped" :id="`kb-${group.key}`" :key="group.key" class="block">
      <div class="block-head">
        <h2>{{ group.title }}</h2>
        <span>{{ group.hint }} · {{ group.families.length }}</span>
      </div>
      <a-table
        :data-source="group.families"
        :pagination="false"
        :loading="loading"
        row-key="key"
        size="small"
        :columns="[
          { title: '文档', key: 'name' },
          { title: '地区', key: 'region', width: 88 },
          { title: '专业', key: 'specialty', width: 88 },
          { title: '生效', key: 'from', width: 108 },
          { title: '废止', key: 'to', width: 108 },
          { title: '状态', key: 'status', width: 240 },
          { title: '操作', key: 'action', width: 380 },
        ]"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <div>{{ record.latest.name }}</div>
            <a-select
              v-if="record.versions.length > 1"
              v-model:value="selected[record.key]"
              size="small"
              style="width: 100%; margin-top: 6px"
              :options="record.versions.map((item) => ({ value: item.id, label: item.name }))"
            />
          </template>
          <template v-else-if="column.key === 'region'">
            {{ currentDoc(record).region || "—" }}
          </template>
          <template v-else-if="column.key === 'specialty'">
            {{ currentDoc(record).specialty || "—" }}
          </template>
          <template v-else-if="column.key === 'from'">
            {{ formatKnowledgeDate(currentDoc(record).effectiveFrom) }}
          </template>
          <template v-else-if="column.key === 'to'">
            {{ formatKnowledgeDate(currentDoc(record).effectiveTo) }}
          </template>
          <template v-else-if="column.key === 'status'">
            {{ parseStatusLabel[currentDoc(record).parseStatus] ?? currentDoc(record).parseStatus }}
            <span v-if="currentDoc(record).parseStatus === 'parsed'" class="merge-st">
              · {{ mergeStatusLabel[currentDoc(record).mergeStatus || "unmerged"] }}
            </span>
            <span v-if="(currentDoc(record).mergeStatus || 'unmerged') === 'merged'" class="merge-st">
              · {{ splitStatusLabel[currentDoc(record).splitStatus || "unsplit"] }}
            </span>
            <span v-if="(currentDoc(record).splitStatus || 'unsplit') === 'split'" class="merge-st">
              · {{ embedStatusLabel[currentDoc(record).embedStatus || "unembedded"] }}
            </span>
          </template>
          <template v-else-if="column.key === 'action'">
            <button
              v-if="canWriteKnowledge && currentDoc(record).storagePath && !isBusy(currentDoc(record))"
              class="act"
              type="button"
              @click="onParse(record)"
            >
              解析
            </button>
            <button
              v-if="canWriteKnowledge && currentDoc(record).parseStatus === 'parsed' && !isBusy(currentDoc(record))"
              class="act"
              type="button"
              @click="onMerge(record)"
            >
              合并
            </button>
            <button
              v-if="canWriteKnowledge && (currentDoc(record).mergeStatus || 'unmerged') === 'merged' && !isBusy(currentDoc(record))"
              class="act"
              type="button"
              @click="onSplit(record)"
            >
              分割
            </button>
            <button
              v-if="canWriteKnowledge && (currentDoc(record).splitStatus || 'unsplit') === 'split' && !isBusy(currentDoc(record))"
              class="act"
              type="button"
              @click="onEmbed(record)"
            >
              嵌入
            </button>
            <button class="act" type="button" @click="onDownload(record)">下载</button>
            <a-popconfirm v-if="canWriteKnowledge" title="将从总库删除，各项目启用关系一并去掉。" @confirm="onDelete(record)">
              <button class="act danger" type="button">删除</button>
            </a-popconfirm>
          </template>
        </template>
      </a-table>
    </section>

    <a-modal v-model:open="modalOpen" title="上传知识 PDF" :confirm-loading="uploading" @ok="submitUpload">
      <div class="form">
        <label>名称</label>
        <a-input v-model:value="form.name" placeholder="文献全称" />
        <label>分类</label>
        <a-select
          v-model:value="form.category"
          :options="[
            { value: 'code', label: '标准规范' },
            { value: 'manual', label: '建模指导手册' },
            { value: 'case', label: '工程案例' },
          ]"
        />
        <label>规范号（可选，相同号会收成一组）</label>
        <a-input v-model:value="form.familyCode" placeholder="如 JTG D62" />
        <label>地区</label>
        <a-input v-model:value="form.region" placeholder="国家 / 上海市 / 通用" />
        <label>专业</label>
        <a-input v-model:value="form.specialty" placeholder="桥梁 / 抗震 / 混凝土" />
        <label>生效日</label>
        <a-input v-model:value="form.effectiveFrom" type="date" />
        <label>废止日</label>
        <a-input v-model:value="form.effectiveTo" type="date" />
        <label>PDF</label>
        <input type="file" accept=".pdf,application/pdf" @change="onFile" />
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
.filters {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}
.chip {
  border: 1px solid var(--ba-line);
  background: #fff;
  border-radius: 999px;
  padding: 3px 10px;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  color: var(--ba-ink);
}
.chip span {
  color: var(--ba-muted);
  margin-left: 6px;
}
.chip:hover {
  background: var(--ba-subtle);
}
.block {
  margin-bottom: 20px;
}
.block-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--ba-line);
}
h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
.block-head span {
  color: var(--ba-muted);
  font-size: 12px;
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
.merge-st {
  color: var(--ba-muted);
}
.form {
  display: grid;
  gap: 6px;
}
.form label {
  margin-top: 6px;
  font-size: 12px;
  color: var(--ba-muted);
}
</style>
