<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { canManageUsers } from "@/stores/session";

const route = useRoute();
const router = useRouter();

const tabs = computed(() => {
  const items = [];
  if (canManageUsers.value) {
    items.push(
      { key: "/system/users", label: "用户管理" },
      { key: "/system/roles", label: "角色管理" },
      { key: "/system/audit", label: "操作审计" },
    );
  }
  items.push({ key: "/system/project-members", label: "项目层权限" });
  return items;
});
</script>

<template>
  <div>
    <h1 class="ba-page-title">系统管理</h1>
    <p class="ba-page-sub">用户、角色与项目成员。菜单由权限推导，没有独立菜单管理表。</p>
    <div class="tabs">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        class="tab"
        :class="{ on: route.path === tab.key }"
        @click="router.push(tab.key)"
      >
        {{ tab.label }}
      </button>
    </div>
    <router-view />
  </div>
</template>

<style scoped>
.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.tab {
  border: 1px solid #eceef5;
  background: #fff;
  border-radius: 8px;
  padding: 6px 12px;
  cursor: pointer;
  font: inherit;
}
.tab.on {
  background: #e8edff;
  color: #3b5bfd;
  border-color: #c9d2ff;
}
</style>
