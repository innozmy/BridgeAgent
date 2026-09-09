<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { message } from "ant-design-vue";
import { listProjectMembers, type ProjectMember } from "@/api/admin";

const route = useRoute();
const projectId = computed(() => String(route.params.id));
const members = ref<ProjectMember[]>([]);
const loading = ref(false);

function permText(row: ProjectMember) {
  if (row.overlay) {
    return "高层覆盖（操作）";
  }
  return row.perm === "operate" ? "操作" : "只读";
}

async function load() {
  loading.value = true;
  try {
    members.value = await listProjectMembers(projectId.value);
  } catch (error) {
    members.value = [];
    message.error(error instanceof Error ? error.message : "无法加载成员");
  } finally {
    loading.value = false;
  }
}

watch(projectId, load, { immediate: true });
</script>

<template>
  <div>
    <p class="hint">本页只读。分配请到系统管理 → 项目层权限。超管与项目管理员默认覆盖全部项目，不必出现在成员表。</p>
    <a-table
      :data-source="members"
      :loading="loading"
      :pagination="false"
      row-key="userId"
      size="small"
      :columns="[
        { title: '成员', dataIndex: 'nickname', width: 140 },
        { title: '登录名', dataIndex: 'username', width: 140 },
        { title: '权限', key: 'perm' },
      ]"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'perm'">{{ permText(record) }}</template>
      </template>
    </a-table>
  </div>
</template>

<style scoped>
.hint {
  color: var(--ba-muted, #8b90a0);
  margin-bottom: 12px;
}
</style>
