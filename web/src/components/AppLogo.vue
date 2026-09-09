<script setup lang="ts">
defineProps<{
  size?: number;
}>();
</script>

<template>
  <svg
    class="logo"
    :width="size ?? 32"
    :height="size ?? 32"
    viewBox="0 0 32 32"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <rect width="32" height="32" rx="8" fill="#4D6BFE" />
    <path
      d="M6 19.5h20M8.5 19.5V13l7.5-4.5L23.5 13v6.5M12.5 19.5V24M19.5 19.5V24"
      stroke="#fff"
      stroke-width="1.9"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>
</template>

<style scoped>
.logo {
  display: block;
  flex-shrink: 0;
}
</style>
