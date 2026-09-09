<script setup lang="ts">
import { onMounted, ref } from "vue";
import { message } from "ant-design-vue";
import { assignProjectMember, listAdminUsers, listProjectMembers, removeProjectMember, type AdminUser, type ProjectMember } from "@/api/admin";
import { listProjects } from "@/api/project";
import type { ProjectRecord } from "@/api/types";

const projects = ref<ProjectRecord[]>([]);
const users = ref<AdminUser[]>([]);
const projectId = ref<number | undefined>();
const members = ref<ProjectMember[]>([]);
const pickUser = ref<number | undefined>();
const pickPerm = ref<"read" | "operate">("read");

async function loadProjects() {
  projects.value = await listProjects();
  if (!projectId.value && projects.value.length) {
    projectId.value = projects.value[0].id;
  }
  users.value = await listAdminUsers();
  await loadMembers();
}

async function loadMembers() {
  if (!projectId.value) {
    members.value = [];
    return;
  }
  members.value = await listProjectMembers(projectId.value);
}

async function assign() {
  if (!projectId.value || !pickUser.value) {
    message.warning("请选择项目和用户");
    return;
  }
  try {
    await assignProjectMember(projectId.value, pickUser.value, pickPerm.value);
    await loadMembers();
  } catch (e) {
    message.error(e instanceof Error ? e.message : "失败");
  }
}

async function remove(row: ProjectMember) {
  if (!projectId.value || row.overlay) {
    return;
  }
  try {
    await removeProjectMember(projectId.value, row.userId);
    await loadMembers();
  } catch (e) {
    message.error(e instanceof Error ? e.message : "失败");
  }
}

onMounted(() => loadProjects().catch((e) => message.error(e instanceof Error ? e.message : "加载失败")));
</script>

<template>
  <div>
    <p class="hint">超管与项目管理员默认覆盖全部项目，不必再分配。每位用户每项目只能 read 或 operate 一种。</p>
    <div class="row">
      <a-select
        v-model:value="projectId"
        style="width: 240px"
        :options="projects.map((p) => ({ value: p.id, label: p.name }))"
        @change="loadMembers"
      />
      <a-select
        v-model:value="pickUser"
        style="width: 180px"
        :options="users.filter((u) => !u.coveredByHighRole).map((u) => ({ value: u.id, label: u.nickname + ' / ' + u.username }))"
        placeholder="用户"
      />
      <a-select v-model:value="pickPerm" style="width: 120px" :options="[{ value: 'read', label: '只读' }, { value: 'operate', label: '操作' }]" />
      <a-button type="primary" @click="assign">分配</a-button>
    </div>
    <a-table
      class="mt"
      :data-source="members"
      :pagination="false"
      row-key="userId"
      size="small"
      :columns="[
        { title: '用户', dataIndex: 'nickname' },
        { title: '登录名', dataIndex: 'username' },
        { title: '权限', key: 'perm' },
        { title: '', key: 'action', width: 88 },
      ]"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'perm'">
          {{ record.overlay ? "高层覆盖（操作全部项目）" : record.perm === "operate" ? "操作" : "只读" }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button v-if="!record.overlay" size="small" danger @click="remove(record)">移除</a-button>
        </template>
      </template>
    </a-table>
  </div>
</template>

<style scoped>
.hint { color: #8b90a0; margin-bottom: 12px; }
.row { display: flex; gap: 8px; flex-wrap: wrap; }
.mt { margin-top: 12px; }
</style>
