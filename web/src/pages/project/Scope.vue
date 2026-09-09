<script setup lang="ts">
import { computed, inject, onUnmounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { message } from "ant-design-vue";
import {
  disableProjectKnowledge,
  enableProjectKnowledge,
  getProjectKnowledge,
  searchProjectKnowledge,
} from "@/api/knowledge";
import type { KnowledgeDocument, KnowledgeSearchHit, KnowledgeSearchResult } from "@/api/types";
import {
  formatKnowledgeDate,
  groupKnowledgeFamilies,
  knowledgeGroups,
  type KnowledgeFamily,
} from "@/stores/knowledge";

const canOperate = inject("projectCanOperate", computed(() => false));
const route = useRoute();
const docs = ref<KnowledgeDocument[]>([]);
const enabledIds = ref<number[]>([]);
const loading = ref(false);
const selected = reactive<Record<string, number>>({});
const projectId = computed(() => String(route.params.id));
const query = ref("");
const searching = ref(false);
const searchResult = ref<KnowledgeSearchResult | null>(null);
let pollTimer: ReturnType<typeof setInterval> | null = null;

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

function isEnabled(family: KnowledgeFamily) {
  return enabledIds.value.includes(currentDoc(family).id);
}

function enabledHint(family: KnowledgeFamily) {
  const names = family.versions
    .filter((item) => enabledIds.value.includes(item.id))
    .map((item) => formatKnowledgeDate(item.effectiveFrom));
  if (!names.length) return "";
  return `已启用 ${names.join("、")}`;
}

const kindLabel: Record<string, string> = {
  text: "条文",
  figure: "图",
  table: "表",
};

function cite(hit: KnowledgeSearchHit) {
  const loc = hit.clauseNo
    ? `第 ${hit.clauseNo} 条`
    : hit.figureNo
      ? `图 ${hit.figureNo}`
      : hit.tableNo
        ? `表 ${hit.tableNo}`
        : "";
  const pages = (hit.pageNumbers || []).join("、");
  return [hit.familyCode || hit.documentName, loc, pages ? `p.${pages}` : ""]
    .filter(Boolean)
    .join(" · ");
}

async function load(silent = false) {
  if (!silent) loading.value = true;
  try {
    const payload = await getProjectKnowledge(projectId.value);
    docs.value = payload.documents;
    enabledIds.value = payload.enabledIds;
    for (const family of groupKnowledgeFamilies(docs.value)) {
      if (selected[family.key] == null) {
        selected[family.key] = family.latest.id;
      }
    }
  } catch (error) {
    if (!silent) {
      message.error(error instanceof Error ? error.message : "加载失败");
    }
  } finally {
    if (!silent) loading.value = false;
  }
}

async function toggle(family: KnowledgeFamily, enabled: boolean) {
  if (!canOperate.value) {
    return;
  }
  const doc = currentDoc(family);
  try {
    if (enabled) {
      await enableProjectKnowledge(projectId.value, doc.id);
      message.success("已加入启用集");
    } else {
      await disableProjectKnowledge(projectId.value, doc.id);
      message.success("已移出启用集");
    }
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "更新失败");
  }
}

async function search() {
  const text = query.value.trim();
  if (!text) {
    message.warning("请输入问句");
    return;
  }
  searching.value = true;
  try {
    searchResult.value = await searchProjectKnowledge(projectId.value, text);
  } catch (error) {
    searchResult.value = null;
    message.error(error instanceof Error ? error.message : "检索失败");
  } finally {
    searching.value = false;
  }
}

watch(projectId, () => {
  searchResult.value = null;
  query.value = "";
  load();
}, { immediate: true });
pollTimer = setInterval(() => {
  load(true).catch(() => undefined);
}, 4000);
onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer);
});
</script>

<template>
  <div>
    <p class="hint">列出全部文献。同一规范号的主标签是最新年版，下拉可切换并分别启用。</p>
    <section class="search-box">
      <div class="block-head">
        <h2>试检索</h2>
        <span>只搜本项目已启用且已嵌入的规范；不够会改写再搜，仍不够会提示把握不足</span>
      </div>
      <div class="search-row">
        <a-input
          v-model:value="query"
          allow-clear
          placeholder="完整技术问题，例如：板式支座刚度怎么设置？"
          @press-enter="search"
        />
        <a-button type="primary" :loading="searching" @click="search">检索</a-button>
      </div>
      <p v-if="searchResult?.notice" class="notice">{{ searchResult.notice }}</p>
      <ul v-if="searchResult" class="hits">
        <li v-if="!searchResult.hits.length" class="sub">没有命中</li>
        <li v-for="(hit, index) in searchResult.hits" :key="hit.refId + '-' + index">
          <div class="hit-meta">
            <span class="kind">{{ kindLabel[hit.kind] || hit.kind }}</span>
            <span>{{ cite(hit) }}</span>
          </div>
          <p class="hit-body">{{ hit.body }}</p>
        </li>
      </ul>
    </section>
    <section v-for="group in grouped" :key="group.key" class="block">
      <div class="block-head">
        <h2>{{ group.title }}</h2>
        <span>{{ group.hint }}</span>
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
          { title: '生效期', key: 'life', width: 168 },
          { title: '启用', key: 'enabled', width: 72 },
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
            <div v-if="enabledHint(record)" class="sub">{{ enabledHint(record) }}</div>
          </template>
          <template v-else-if="column.key === 'region'">
            {{ currentDoc(record).region || "—" }}
          </template>
          <template v-else-if="column.key === 'specialty'">
            {{ currentDoc(record).specialty || "—" }}
          </template>
          <template v-else-if="column.key === 'life'">
            {{ formatKnowledgeDate(currentDoc(record).effectiveFrom) }}
            –
            {{ formatKnowledgeDate(currentDoc(record).effectiveTo) }}
          </template>
          <template v-else-if="column.key === 'enabled'">
            <a-switch size="small" :disabled="!canOperate" :checked="isEnabled(record)" @change="(v: boolean) => toggle(record, v)" />
          </template>
        </template>
      </a-table>
    </section>
  </div>
</template>

<style scoped>
.hint {
  margin: 0 0 16px;
  color: var(--ba-muted);
  font-size: 13px;
}
.block {
  margin-bottom: 24px;
}
.block-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
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
.sub {
  margin-top: 4px;
  color: var(--ba-muted);
  font-size: 12px;
}
.notice {
  margin: 10px 0 0;
  color: #ad6800;
  font-size: 13px;
}
.search-box {
  margin-bottom: 24px;
}
.search-row {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.hits {
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}
.hits li {
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--ba-line);
}
.hit-meta {
  display: flex;
  gap: 8px;
  align-items: baseline;
  font-size: 12px;
  color: var(--ba-muted);
}
.kind {
  flex: none;
  color: var(--ba-text);
  font-weight: 600;
}
.hit-body {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.55;
  white-space: pre-wrap;
}
</style>
