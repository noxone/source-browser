<template>
  <div>
    <dt class="text-xs text-gray-400 mb-0.5">{{ label }}</dt>
    <dd :class="['text-gray-700 break-all', mono ? 'font-mono text-xs' : '']">
      <a v-if="href" :href="href" target="_blank" rel="noopener noreferrer"
         class="text-indigo-600 hover:underline">{{ value }}</a>
      <template v-else>{{ value }}</template>
    </dd>
  </div>
</template>

<script setup lang="ts">
defineProps<{ label: string; value: string; mono?: boolean; href?: string }>()
</script>
