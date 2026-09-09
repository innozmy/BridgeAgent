<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { message } from "ant-design-vue";
import { listAdminAudits, type AuditRow } from "@/api/admin";
import { formatDateTime } from "@/stores/projects";

const ACTION_LABEL: Record<string, string> = {
  login_ok: "登录成功",
  login_fail: "登录失败",
  password_change: "本人改密",
  password_reset: "重置密码",
  user_nickname: "改昵称",
  user_avatar: "换头像",
  user_create: "新建用户",
  user_status: "停用/启用",
  user_role: "改角色",
  role_create: "新建角色",
  role_update: "改角色",
  role_delete: "删除角色",
  member_assign: "分配项目权限",
  member_remove: "移除项目成员",
};

const records = ref<AuditRow[]>([]);
const total = ref(0);
const loading = ref(false);
const filter = reactive({ action: undefined as string | undefined, actor: "" });
const page = ref(1);

const actionOptions = [
  { value: undefined, label: "全部动作" },
  ...Object.entries(ACTION_LABEL).map(([value, label]) => ({ value, label })),
];

async function load() {
  loading.value = true;
  try {
    const data = await listAdminAudits({
      action: filter.action,
      actor: filter.actor.trim() || undefined,
      page: page.value,
      size: 20,
    });
    records.value = data.records ?? [];
    total.value = data.total ?? 0;
  } catch (e) {
    message.error(e instanceof Error ? e.message : "加载失败");
  } finally {
    loading.value = false;
  }
}

function change() {
  page.value = 1;
  load();
}

function onTableChange(pag: { current?: number }) {
  page.value = pag.current ?? 1;
  load();
}

onMounted(() => load());
</script>

<template>
  <div>
    <p class="hint">只读。登录、账号角色、项目授权留痕。识图建模与知识文件不在这里。</p>
    <div class="row">
      <a-select
        v-model:value="filter.action"
        allow-clear
        placeholder="全部动作"
        style="width: 200px"
        :options="actionOptions.filter((o) => o.value)"
        @change="change"
      />
      <a-input
        v-model:value="filter.actor"
        allow-clear
        placeholder="操作者用户名"
        style="width: 180px"
        @pressEnter="change"
      />
      <a-button type="primary" @click="change">筛选</a-button>
    </div>
    <a-table
      class="mt"
      :data-source="records"
      :loading="loading"
      :pagination="{ current: page, pageSize: 20, total, showSizeChanger: false }"
      row-key="id"
      size="small"
      :columns="[
        { title: '时间', key: 'time', width: 170 },
        { title: '操作者', dataIndex: 'actorUsername', width: 110 },
        { title: '动作', key: 'action', width: 140 },
        { title: '对象', dataIndex: 'targetLabel' },
        { title: '结果', key: 'ok', width: 90 },
        { title: '改前 → 改后', key: 'diff', width: 180 },
      ]"
      @change="onTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'time'">{{ formatDateTime(record.createdAt) }}</template>
        <template v-else-if="column.key === 'action'">{{ ACTION_LABEL[record.action] ?? record.action }}</template>
        <template v-else-if="column.key === 'ok'">
          {{ record.success ? "成功" : record.reason || "失败" }}
        </template>
        <template v-else-if="column.key === 'diff'">
          <span v-if="record.beforeText || record.afterText">{{ record.beforeText || "—" }} → {{ record.afterText || "—" }}</span>
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
