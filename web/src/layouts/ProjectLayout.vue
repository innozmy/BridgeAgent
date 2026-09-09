<script setup lang="ts">
import { computed, provide, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { message } from "ant-design-vue";
import { getProject, deleteProject } from "@/api/project";
import type { ProjectDetail } from "@/api/types";
import { canManageProjects, canOperateProject } from "@/stores/session";
import {
  carriagewayLabel,
  formatSpanText,
  projectDetailKey,
  refreshProjectList,
  reloadProjectKey,
  statusLabel,
} from "@/stores/projects";

const route = useRoute();
const router = useRouter();
const id = computed(() => String(route.params.id));
const detail = ref<ProjectDetail | null>(null);
const canOperate = computed(() => canOperateProject(id.value));
const canDeleteProject = computed(() => canManageProjects.value);

async function load() {
  try {
    detail.value = await getProject(id.value);
  } catch (error) {
    detail.value = null;
    message.error(error instanceof Error ? error.message : "项目加载失败");
  }
}

provide("projectCanOperate", canOperate);
provide(projectDetailKey, detail);
provide(reloadProjectKey, load);

const tabs = [
  { key: "overview", label: "概览" },
  { key: "drawings", label: "图纸" },
  { key: "inquiry", label: "问询" },
  { key: "tasks", label: "任务" },
  { key: "models", label: "模型版本" },
  { key: "findings", label: "验证记录" },
  { key: "scope", label: "知识范围" },
  { key: "members", label: "成员" },
];

const active = computed(() => String(route.name ?? "overview"));

function onTab(key: string | number) {
  router.push({ name: String(key), params: { id: id.value } });
}

watch(id, load, { immediate: true });

async function onDelete() {
  try {
    await deleteProject(id.value);
    message.success("项目已删除");
    await refreshProjectList();
    await router.push("/workbench");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "删除失败");
  }
}
</script>

<template>
  <div>
    <div class="project-head">
      <div>
        <button class="back" type="button" @click="router.push('/workbench')">工作台</button>
        <span class="sep">/</span>
        <h1 class="ba-page-title" style="display: inline">{{ detail?.name ?? "加载中" }}</h1>
        <p class="ba-page-sub" style="margin: 6px 0 0">
          {{ detail?.region || "地区未填" }}
          · {{ detail?.openedOn || "落地日未填" }}
          · {{ carriagewayLabel[detail?.carriageway ?? ""] ?? detail?.carriageway }}
          · {{ formatSpanText(detail?.units) }}
        </p>
      </div>
      <div class="head-actions">
        <a-popconfirm
          v-if="canDeleteProject"
          title="删除项目后无法恢复，图纸与 SAP 模型一并删除。"
          ok-text="删除"
          cancel-text="取消"
          @confirm="onDelete"
        >
          <button class="danger" type="button">删除项目</button>
        </a-popconfirm>
        <span class="state">{{ statusLabel[detail?.status ?? ""] ?? detail?.status ?? "" }}</span>
      </div>
    </div>
    <a-tabs :active-key="active" @change="onTab" class="project-tabs" size="small">
      <a-tab-pane v-for="tab in tabs" :key="tab.key" :tab="tab.label" />
    </a-tabs>
    <router-view />
  </div>
</template>

<style scoped>
.project-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}
.back {
  border: 0;
  background: none;
  padding: 0;
  color: var(--ba-link);
  cursor: pointer;
  font: inherit;
  font-size: 14px;
}
.sep {
  margin: 0 6px;
  color: var(--ba-muted);
}
.state {
  margin-top: 0;
  font-size: 12px;
  color: var(--ba-muted);
  border: 1px solid var(--ba-line);
  border-radius: 999px;
  padding: 1px 8px;
}
.head-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.danger {
  border: 0;
  background: none;
  padding: 0;
  color: #cf222e;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}
.project-tabs {
  margin-top: 8px;
}
.project-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 16px;
}
.project-tabs :deep(.ant-tabs-content-holder) {
  display: none;
}
.project-tabs :deep(.ant-tabs-tab) {
  padding: 8px 4px;
  font-size: 14px;
}
</style>
