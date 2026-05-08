<template>
  <RouterLink
    :to="to"
    :title="expanded ? undefined : label"
    class="flex items-center gap-3 w-full h-10 px-2.5 rounded-lg text-gray-400 hover:text-white hover:bg-gray-700 transition-colors"
    :class="{ 'text-white bg-gray-700': isActive }"
  >
    <slot name="icon" />
    <span
      v-if="expanded"
      class="text-sm whitespace-nowrap overflow-hidden"
    >{{ label }}</span>
  </RouterLink>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'

const props = defineProps<{
  to: RouteLocationRaw
  label: string
  expanded: boolean
}>()

const route = useRoute()

const isActive = computed(() => {
  if (typeof props.to === 'object' && 'name' in props.to) {
    return route.name === props.to.name
  }
  return false
})
</script>
