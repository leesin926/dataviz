<template>
  <div class="screen-table" :style="tableStyle">
    <table>
      <thead>
        <tr>
          <th v-for="(col, i) in columns" :key="i">{{ col }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, r) in rows" :key="r">
          <td v-for="(cell, c) in row" :key="c">{{ cell }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import type { ScreenComponent } from '@dataviz/shared-types'
  import { CANVAS_INK } from '@dataviz/shared-types'
  import { pxOf, strOf } from './props'

  const props = defineProps<{
    component: ScreenComponent
  }>()

  const p = computed(() => props.component.props || {})
  const columns = computed(() => (p.value.columns as unknown[]) || [])
  const rows = computed(() => (p.value.rows as unknown[][]) || [])
  const tableStyle = computed<Record<string, string>>(() => ({
    fontSize: pxOf(p.value.fontSize, 14),
    color: strOf(p.value.color, CANVAS_INK.normal),
    '--dv-ink-accent': CANVAS_INK.accent,
    '--dv-ink-line': CANVAS_INK.line,
    '--dv-ink-faint': CANVAS_INK.faint,
    '--dv-ink-surface': CANVAS_INK.surface,
  }))
</script>

<style scoped>
  .screen-table {
    width: 100%;
    height: 100%;
    overflow: auto;
    font-size: 12px;
  }
  table {
    width: 100%;
    border-collapse: collapse;
  }
  th,
  td {
    padding: 6px 8px;
    border-bottom: 1px solid var(--dv-ink-line);
    text-align: left;
    white-space: nowrap;
  }
  th {
    color: var(--dv-ink-accent);
    background: var(--dv-ink-surface);
    font-weight: 600;
    position: sticky;
    top: 0;
  }
  td {
    color: inherit;
  }
  tbody tr:nth-child(even) {
    background: var(--dv-ink-faint);
  }
</style>
