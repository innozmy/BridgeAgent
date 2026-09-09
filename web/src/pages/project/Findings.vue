<script setup lang="ts">
import { findings } from "@/mock/data";
</script>

<template>
  <a-table
    :data-source="findings"
    :pagination="false"
    row-key="id"
    size="small"
    :columns="[
      { title: '编号', dataIndex: 'id', width: 72 },
      { title: '层', dataIndex: 'level', width: 56 },
      { title: '条目', dataIndex: 'title' },
      { title: '期望', dataIndex: 'expect' },
      { title: '实际', dataIndex: 'actual' },
      { title: '状态', dataIndex: 'status', width: 80 },
    ]"
  >
    <template #bodyCell="{ column, record }">
      <template v-if="column.dataIndex === 'status'">
        {{ record.status === "open" ? "待处理" : "已通过" }}
      </template>
    </template>
  </a-table>
</template>
