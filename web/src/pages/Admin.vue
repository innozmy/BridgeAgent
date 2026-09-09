<script setup lang="ts">
const roles = [
  { role: "系统管理员", desc: "租户、账号、审计" },
  { role: "知识管理员", desc: "上传与发布目录" },
  { role: "项目负责人", desc: "确认 IR、启用知识、批准校准" },
  { role: "建模工程师", desc: "问询与提交任务" },
  { role: "只读审查", desc: "查看版本与验证记录" },
];
</script>

<template>
  <div>
    <h1 class="ba-page-title">成员</h1>
    <p class="ba-page-sub">知识权限与项目权限分开。此页为占位。</p>
    <a-table
      :data-source="roles"
      :pagination="false"
      row-key="role"
      size="small"
      :columns="[
        { title: '角色', dataIndex: 'role', width: 140 },
        { title: '说明', dataIndex: 'desc' },
      ]"
    />
  </div>
</template>
