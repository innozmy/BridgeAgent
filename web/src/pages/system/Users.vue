<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { message } from "ant-design-vue";
import {
  createAdminUser,
  listAdminRoles,
  listAdminUsers,
  updateAdminUser,
  type AdminRole,
  type AdminUser,
} from "@/api/admin";

const users = ref<AdminUser[]>([]);
const roles = ref<AdminRole[]>([]);
const open = ref(false);
const form = reactive({ username: "", nickname: "", password: "1234", roleId: undefined as number | undefined });

async function load() {
  users.value = await listAdminUsers();
  roles.value = await listAdminRoles();
}

async function create() {
  if (!form.username || !form.nickname || !form.roleId) {
    message.warning("请填用户名、昵称、角色");
    return;
  }
  try {
    await createAdminUser({
      username: form.username,
      nickname: form.nickname,
      password: form.password || "1234",
      roleId: form.roleId,
    });
    open.value = false;
    await load();
  } catch (e) {
    message.error(e instanceof Error ? e.message : "创建失败");
  }
}

async function setStatus(row: AdminUser, status: string) {
  try {
    await updateAdminUser(row.id, { status });
    await load();
  } catch (e) {
    message.error(e instanceof Error ? e.message : "更新失败");
  }
}

async function resetPass(row: AdminUser) {
  try {
    await updateAdminUser(row.id, { password: "1234" });
    message.success("已重置为 1234，旧票立即失效");
  } catch (e) {
    message.error(e instanceof Error ? e.message : "失败");
  }
}

onMounted(() => load().catch((e) => message.error(e instanceof Error ? e.message : "加载失败")));
</script>

<template>
  <div>
    <a-button type="primary" @click="open = true">新建用户</a-button>
    <a-table class="mt" :data-source="users" :pagination="false" row-key="id" size="small" :columns="[
      { title: '编号', dataIndex: 'id', width: 70 },
      { title: '用户名', dataIndex: 'username' },
      { title: '昵称', dataIndex: 'nickname' },
      { title: '角色', dataIndex: 'roleName' },
      { title: '状态', dataIndex: 'status', width: 90 },
    ]">
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'status'">
          <a-button size="small" @click="setStatus(record, record.status === 'enabled' ? 'disabled' : 'enabled')">
            {{ record.status === 'enabled' ? '停用' : '启用' }}
          </a-button>
          <a-button size="small" class="ml" @click="resetPass(record)">重置密码</a-button>
        </template>
      </template>
    </a-table>
    <a-modal v-model:open="open" title="新建用户" @ok="create">
      <a-form layout="vertical">
        <a-form-item label="用户名"><a-input v-model:value="form.username" /></a-form-item>
        <a-form-item label="昵称"><a-input v-model:value="form.nickname" /></a-form-item>
        <a-form-item label="密码"><a-input v-model:value="form.password" /></a-form-item>
        <a-form-item label="角色">
          <a-select v-model:value="form.roleId" :options="roles.map((r) => ({ value: r.id, label: r.name }))" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.mt { margin-top: 12px; }
.ml { margin-left: 8px; }
</style>
