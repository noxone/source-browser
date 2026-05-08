<template>
  <RouterLink
    :to="to"
    :title="title"
    class="flex items-center justify-center w-10 h-10 rounded-lg text-gray-400 hover:text-white hover:bg-gray-700 transition-colors"
    :class="{ 'text-white bg-gray-700': isActive }"
  >
    <slot />
  </RouterLink>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'

const props = defineProps<{
  to: RouteLocationRaw
  title: string
}>()

const route = useRoute()

const isActive = computed(() => {
  if (typeof props.to === 'object' && 'name' in props.to) {
    return route.name === props.to.name
  }
  return false
})
</script>
