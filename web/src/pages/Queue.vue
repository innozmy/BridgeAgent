<script setup lang="ts">
import { onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import { getResourceQueue, type ResourceQueueLane } from "@/api/queue";
import { formatDateTime } from "@/stores/projects";

const router = useRouter();
const lanes = ref<ResourceQueueLane[]>([]);
const loading = ref(false);
const error = ref("");
let pollTimer: ReturnType<typeof setInterval> | null = null;

async function load() {
  loading.value = true;
  try {
    const data = await getResourceQueue();
    lanes.value = data.lanes ?? [];
    error.value = "";
  } catch (e) {
    error.value = e instanceof Error ? e.message : "加载失败";
  } finally {
    loading.value = false;
  }
}

function openItem(laneId: string, record: ResourceQueueLane["items"][number]) {
  if (record.refType === "knowledge") {
    router.push("/knowledge");
    return;
  }
  if (record.projectId) {
    router.push(`/projects/${record.projectId}/tasks`);
  }
}

function stateText(state: string) {
  return state === "running" ? "占用中" : "排队";
}

load();
pollTimer = setInterval(() => {
  load().catch(() => undefined);
}, 4000);
onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer);
});
</script>

<template>
  <div>
    <h1 class="ba-page-title">资源队列</h1>
    <p class="ba-page-sub">
      本机三条作业车道：识图、知识库、建模。核心线程同时执行；每条车道线程池内再等最多 5 个。再多的留在库里排队。问询不进此页。
    </p>
    <p v-if="error" class="err">{{ error }}</p>
    <section v-for="lane in lanes" :key="lane.id" class="lane">
      <div class="lane-head">
        <strong>{{ lane.name }}</strong>
        <span>容量 {{ lane.core }} · 占用 {{ lane.running }} · 排队 {{ lane.queued }}</span>
      </div>
      <p v-if="lane.running > (lane.items.filter((item) => item.state === 'running').length)" class="hint">
        另有占用属于你不可见的项目。
      </p>
      <a-table
        :columns="[
          { title: '状态', dataIndex: 'state', width: 88 },
          { title: '任务', dataIndex: 'title' },
          { title: '项目', dataIndex: 'projectName', width: 160 },
          { title: '操作者', dataIndex: 'actorUsername', width: 100 },
          { title: '更新', dataIndex: 'updatedAt', width: 168 },
        ]"
        :data-source="lane.items"
        :pagination="false"
        :row-key="(row) => lane.id + '-' + row.refType + '-' + row.refId + '-' + (row.step || '')"
        size="small"
        :locale="{ emptyText: loading ? '加载中…' : '空闲' }"
        :custom-row="(record) => ({ onClick: () => openItem(lane.id, record), style: { cursor: 'pointer' } })"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'state'">{{ stateText(record.state) }}</template>
          <template v-else-if="column.dataIndex === 'projectName'">{{ record.projectName || '—' }}</template>
          <template v-else-if="column.dataIndex === 'actorUsername'">{{ record.actorUsername || '—' }}</template>
          <template v-else-if="column.dataIndex === 'updatedAt'">{{ formatDateTime(record.updatedAt) }}</template>
        </template>
      </a-table>
    </section>
  </div>
</template>

<style scoped>
.lane {
  margin-bottom: 28px;
}
.lane-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.hint,
.ba-page-sub {
  color: var(--ba-muted, #667);
  margin-bottom: 12px;
}
.err {
  color: #c44;
}
</style>
