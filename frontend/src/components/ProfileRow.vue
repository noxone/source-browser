<template>
  <div class="flex items-center px-6 py-4">
    <span class="w-28 shrink-0 text-xs font-medium text-gray-500 uppercase tracking-wider">{{ label }}</span>
    <span class="text-sm text-gray-900">{{ value }}</span>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  label: string
  value: string
}>()
</script>
