<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { message } from "ant-design-vue";
import { createAdminRole, deleteAdminRole, listAdminRoles, type AdminRole } from "@/api/admin";

const roles = ref<AdminRole[]>([]);
const open = ref(false);
const form = reactive({
  code: "",
  name: "",
  flagSuper: false,
  flagKnowledge: false,
  flagProjectAdmin: false,
});

async function load() {
  roles.value = await listAdminRoles();
}

async function create() {
  try {
    await createAdminRole({ ...form });
    open.value = false;
    await load();
  } catch (e) {
    message.error(e instanceof Error ? e.message : "失败");
  }
}

async function remove(row: AdminRole) {
  try {
    await deleteAdminRole(row.id);
    await load();
  } catch (e) {
    message.error(e instanceof Error ? e.message : "失败");
  }
}

onMounted(() => load().catch((e) => message.error(e instanceof Error ? e.message : "加载失败")));
</script>

<template>
  <div>
    <p class="hint">内置四角色不可删、开关不可改。自定义角色可组合高层开关。</p>
    <a-button type="primary" @click="open = true">新建角色</a-button>
    <a-table
      class="mt"
      :data-source="roles"
      :pagination="false"
      row-key="id"
      size="small"
      :columns="[
        { title: '编码', dataIndex: 'code' },
        { title: '名称', dataIndex: 'name' },
        { title: '超管', key: 'super', width: 80 },
        { title: '知识库', key: 'kb', width: 80 },
        { title: '项目管理', key: 'pm', width: 100 },
        { title: '操作', key: 'action', width: 100 },
      ]"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'super'">{{ record.flagSuper ? "是" : "" }}</template>
        <template v-else-if="column.key === 'kb'">{{ record.flagKnowledge ? "是" : "" }}</template>
        <template v-else-if="column.key === 'pm'">{{ record.flagProjectAdmin ? "是" : "" }}</template>
        <template v-else-if="column.key === 'action'">
          <a-button v-if="!record.builtin" size="small" danger @click="remove(record)">删除</a-button>
        </template>
      </template>
    </a-table>
    <a-modal v-model:open="open" title="新建角色" @ok="create">
      <a-form layout="vertical">
        <a-form-item label="编码"><a-input v-model:value="form.code" /></a-form-item>
        <a-form-item label="名称"><a-input v-model:value="form.name" /></a-form-item>
        <a-checkbox v-model:checked="form.flagSuper">超级管理</a-checkbox>
        <a-checkbox v-model:checked="form.flagKnowledge">知识库管理</a-checkbox>
        <a-checkbox v-model:checked="form.flagProjectAdmin">项目管理</a-checkbox>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.hint { color: #8b90a0; margin-bottom: 12px; }
.mt { margin-top: 12px; }
</style>
