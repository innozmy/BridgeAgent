<script setup lang="ts">
import { computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { message } from "ant-design-vue";
import { deleteProject } from "@/api/project";
import {
  carriagewayLabel,
  formatDateTime,
  projectList,
  refreshProjectList,
  statusLabel,
} from "@/stores/projects";
import { canManageProjects } from "@/stores/session";

const router = useRouter();
const count = computed(() => projectList.value.length);

function openProject(id: number) {
  router.push(`/projects/${id}/overview`);
}

async function onDelete(id: number) {
  try {
    await deleteProject(id);
    message.success("项目已删除");
    await refreshProjectList();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "删除失败");
  }
}

onMounted(async () => {
  try {
    await refreshProjectList();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "无法加载项目，请确认后端已启动");
  }
});
</script>

<template>
  <div>
    <h1 class="ba-page-title">工作台</h1>
    <p class="ba-page-sub">{{ count }} 个项目</p>

    <div v-if="!count" class="empty">还没有项目。左侧点「新建项目」，只需名称和幅面。</div>

    <div class="ba-list">
      <div
        v-for="item in projectList"
        :key="item.id"
        class="ba-list-item"
        role="button"
        tabindex="0"
        @click="openProject(item.id)"
      >
        <div>
          <div class="name-row">
            <span class="name">{{ item.name }}</span>
            <span class="status">{{ statusLabel[item.status] ?? item.status }}</span>
          </div>
          <div class="meta">
            {{ item.region || "地区未填" }}
            · {{ item.openedOn || "落地日未填" }}
            · {{ carriagewayLabel[item.carriageway] ?? item.carriageway }}
            · {{ item.girderType || "主梁形式未识别" }}
          </div>
        </div>
        <div class="row-right" @click.stop>
          <span class="meta time">{{ formatDateTime(item.updatedAt) }}</span>
          <a-popconfirm
            v-if="canManageProjects"
            title="删除项目后无法恢复，图纸文件一并删除。"
            ok-text="删除"
            cancel-text="取消"
            @confirm="onDelete(item.id)"
          >
            <button class="act danger" type="button">删除</button>
          </a-popconfirm>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.name {
  color: var(--ba-link);
  font-weight: 600;
  font-size: 14px;
}
.status {
  font-size: 12px;
  color: var(--ba-muted);
  border: 1px solid var(--ba-line);
  border-radius: 999px;
  padding: 0 7px;
  line-height: 18px;
}
.meta {
  color: var(--ba-muted);
  font-size: 12px;
  margin-top: 4px;
}
.row-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.time {
  margin-top: 0;
}
.ba-list-item {
  cursor: pointer;
  align-items: center;
}
.act {
  border: 0;
  background: none;
  padding: 0;
  color: #cf222e;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  flex-shrink: 0;
}
.empty {
  margin: 16px 0;
  color: var(--ba-muted);
  font-size: 14px;
}
</style>
