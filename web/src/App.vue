<script setup lang="ts">
import { ConfigProvider } from "ant-design-vue";
import zhCN from "ant-design-vue/es/locale/zh_CN";
import { appTheme } from "@/theme";
</script>

<template>
  <ConfigProvider :locale="zhCN" :theme="appTheme">
    <router-view />
  </ConfigProvider>
</template>
