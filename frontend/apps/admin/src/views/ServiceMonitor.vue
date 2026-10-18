<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('monitor.title') }}</h2>
        <p class="dv-page-desc">{{ t('monitor.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button :loading="loading" @click="refresh">{{ t('common.refresh') }}</el-button>
      </div>
    </div>

    <!-- 实例清单来自服务进程自己的注册端点，而那组端点当前零调用方。
         空表在这里的真实含义是"没人注册过"，不是"所有服务都挂了" —— 这句话必须留在页面上。 -->
    <el-alert
      v-if="!loading && !serviceNames.length"
      class="notice"
      type="warning"
      :closable="false"
      :title="t('monitor.emptyTitle')"
      :description="t('monitor.emptyDesc')"
      show-icon
    />
    <el-alert
      v-else
      class="notice"
      type="info"
      :closable="false"
      :title="t('monitor.staleTitle')"
      show-icon
    />

    <div v-if="serviceNames.length" class="dv-toolbar">
      <el-select v-model="serviceName" style="width: 240px" :placeholder="t('monitor.pickService')" @change="onServiceChange">
        <el-option v-for="n in serviceNames" :key="n" :label="n" :value="n" />
      </el-select>
    </div>

    <template v-if="serviceName">
      <div class="health">
        <div class="cell">
          <span class="k">{{ t('monitor.overall') }}</span>
          <el-tag v-if="health" :type="healthTag(health.overallStatus)" size="small" disable-transitions>
            {{ healthText(health.overallStatus) }}
          </el-tag>
          <span v-else class="dv-text-3">—</span>
        </div>
        <div class="cell">
          <span class="k">{{ t('monitor.instances') }}</span>
          <span class="v">{{ health ? `${health.healthyCount} / ${health.instanceCount}` : '—' }}</span>
        </div>
        <div class="cell">
          <span class="k">{{ t('monitor.lastHeartbeat') }}</span>
          <span class="v mono">{{ health?.lastHeartbeat || t('monitor.never') }}</span>
        </div>
        <div class="cell">
          <span class="k">{{ t('monitor.activeAlerts') }}</span>
          <span class="v" :class="{ bad: (health?.activeAlertCount ?? 0) > 0 }">{{ health?.activeAlertCount ?? '—' }}</span>
        </div>
      </div>

      <el-card shadow="never" class="block">
        <template #header>{{ t('monitor.instanceList') }}</template>
        <el-table :data="instances" v-loading="loading" stripe>
          <el-table-column prop="instanceId" :label="t('monitor.instanceId')" min-width="180" show-overflow-tooltip>
            <template #default="{ row }"><span class="mono">{{ row.instanceId }}</span></template>
          </el-table-column>
          <el-table-column :label="t('monitor.address')" width="190">
            <template #default="{ row }"><span class="mono">{{ row.host }}:{{ row.port }}</span></template>
          </el-table-column>
          <el-table-column :label="t('common.status')" width="110">
            <template #default="{ row }">
              <el-tag :type="instanceTag(row.status)" size="small" disable-transitions>{{ instanceText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('monitor.lastHeartbeat')" width="170">
            <template #default="{ row }">{{ row.lastHeartbeat || t('monitor.never') }}</template>
          </el-table-column>
          <el-table-column :label="t('monitor.metadata')" min-width="220">
            <template #default="{ row }">
              <span v-if="!metaEntries(row).length" class="dv-text-3">{{ t('monitor.noMetadata') }}</span>
              <div v-else class="meta">
                <el-tag v-for="[k, v] in metaEntries(row)" :key="k" size="small" type="info" disable-transitions>
                  {{ k }}={{ v }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <template #empty><span class="dv-text-3">{{ t('common.empty') }}</span></template>
        </el-table>
      </el-card>

      <el-card shadow="never" class="block">
        <template #header>
          <div class="head-row">
            <span>{{ t('monitor.metricsNow') }}</span>
            <span class="dv-text-3">{{ t('monitor.metricsNowHint') }}</span>
          </div>
        </template>
        <el-table :data="metrics" v-loading="metricsLoading" stripe>
          <el-table-column prop="metricName" :label="t('monitor.metricName')" min-width="170" show-overflow-tooltip />
          <el-table-column :label="t('monitor.metricValue')" width="140">
            <template #default="{ row }"><span class="mono">{{ formatValue(row) }}</span></template>
          </el-table-column>
          <el-table-column :label="t('monitor.metricTypeLabel')" width="110">
            <template #default="{ row }">
              <span v-if="row.metricType">{{ metricTypeText(row.metricType) }}</span>
              <span v-else class="dv-text-3">—</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('monitor.instanceId')" min-width="160">
            <template #default="{ row }"><span class="mono dv-text-3">{{ row.instanceId || '—' }}</span></template>
          </el-table-column>
          <el-table-column prop="timestamp" :label="t('monitor.metricAt')" width="170" />
          <el-table-column :label="t('common.operations')" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openHistory(row)">{{ t('monitor.history') }}</el-button>
            </template>
          </el-table-column>
          <template #empty><span class="dv-text-3">{{ t('monitor.noMetrics') }}</span></template>
        </el-table>
      </el-card>
    </template>

    <el-drawer v-model="historyVisible" :title="t('monitor.historyTitle', { name: historyMetric })" size="620px">
      <div class="dv-toolbar">
        <el-date-picker
          v-model="range"
          type="datetimerange"
          :start-placeholder="t('monitor.from')"
          :end-placeholder="t('monitor.to')"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 100%"
        />
        <el-button type="primary" :loading="historyLoading" @click="loadHistory">{{ t('common.search') }}</el-button>
      </div>

      <div v-if="history.length" class="summary">
        <div class="cell">
          <span class="k">{{ t('monitor.points') }}</span>
          <span class="v">{{ history.length }}</span>
        </div>
        <div class="cell">
          <span class="k">{{ t('monitor.min') }}</span>
          <span class="v mono">{{ fmt(agg.min) }}</span>
        </div>
        <div class="cell">
          <span class="k">{{ t('monitor.max') }}</span>
          <span class="v mono">{{ fmt(agg.max) }}</span>
        </div>
        <div class="cell">
          <span class="k">{{ t('monitor.avg') }}</span>
          <span class="v mono">{{ fmt(agg.avg) }}</span>
        </div>
        <div class="cell">
          <span class="k">{{ t('monitor.latest') }}</span>
          <span class="v mono">{{ fmt(agg.latest) }}</span>
        </div>
      </div>

      <el-table :data="history" v-loading="historyLoading" stripe max-height="420">
        <el-table-column prop="timestamp" :label="t('monitor.metricAt')" width="180" />
        <el-table-column :label="t('monitor.metricValue')" width="140">
          <template #default="{ row }"><span class="mono">{{ fmt(row.metricValue) }}</span></template>
        </el-table-column>
        <el-table-column :label="t('monitor.relative')">
          <template #default="{ row }">
            <div class="bar"><div class="fill" :style="{ width: barWidth(row.metricValue) }"></div></div>
          </template>
        </el-table-column>
        <template #empty><span class="dv-text-3">{{ t('monitor.noMetrics') }}</span></template>
      </el-table>
      <p class="dv-page-desc">{{ t('monitor.historyNote') }}</p>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue'
  import { ElMessage } from 'element-plus'
  import type {
    MonitorMetric,
    MonitorMetricHistoryPoint,
    MonitorServiceHealth,
    MonitorServiceInstance,
  } from '@dataviz/shared-types'
  import {
    getHealth,
    getMetricHistory,
    listInstancesOfService,
    listMetrics,
    listServiceNames,
  } from '@dataviz/api-client'
  import { useI18n } from 'vue-i18n'

  const { t } = useI18n()

  const loading = ref(false)
  const metricsLoading = ref(false)
  const historyLoading = ref(false)

  const serviceNames = ref<string[]>([])
  const serviceName = ref('')
  const health = ref<MonitorServiceHealth | null>(null)
  const instances = ref<MonitorServiceInstance[]>([])
  const metrics = ref<MonitorMetric[]>([])

  const historyVisible = ref(false)
  const historyMetric = ref('')
  const history = ref<MonitorMetricHistoryPoint[]>([])
  const range = ref<[string, string] | null>(null)

  const agg = computed(() => {
    const values = history.value.map((p) => Number(p.metricValue)).filter((v) => !Number.isNaN(v))
    if (!values.length) return { min: null as number | null, max: null, avg: null, latest: null }
    let min = values[0]
    let max = values[0]
    let sum = 0
    for (const v of values) {
      if (v < min) min = v
      if (v > max) max = v
      sum += v
    }
    // values 已经按时间正序（后端 orderByAsc），末位就是最新值
    return { min, max, avg: sum / values.length, latest: values[values.length - 1] }
  })

  function fmt(value: number | null | undefined): string {
    if (value === null || value === undefined || Number.isNaN(value)) return '—'
    return Number.isInteger(value) ? String(value) : value.toFixed(2)
  }

  function formatValue(row: MonitorMetric): string {
    const v = Number(row.metricValue)
    return Number.isNaN(v) ? String(row.metricValue) : fmt(v)
  }

  function barWidth(value: number): string {
    const max = agg.value.max
    if (max === null || max <= 0) return '0%'
    const ratio = Math.max(0, Number(value)) / max
    return `${Math.min(100, Math.round(ratio * 100))}%`
  }

  function healthText(value?: string): string {
    if (!value) return '—'
    const key = `monitor.health.${value}`
    const text = t(key)
    return text === key ? value : text
  }

  function healthTag(value?: string): 'success' | 'warning' | 'danger' | 'info' {
    if (value === 'UP') return 'success'
    if (value === 'PARTIAL') return 'warning'
    if (value === 'DOWN') return 'danger'
    return 'info'
  }

  function instanceText(value: string): string {
    const key = `monitor.instance.${value}`
    const text = t(key)
    return text === key ? value : text
  }

  function instanceTag(value: string): 'success' | 'warning' | 'danger' | 'info' {
    if (value === 'UP') return 'success'
    if (value === 'STARTING') return 'warning'
    if (value === 'DOWN') return 'danger'
    return 'info'
  }

  function metricTypeText(value: string): string {
    const key = `monitor.metricType.${value}`
    const text = t(key)
    return text === key ? value : text
  }

  function metaEntries(row: MonitorServiceInstance): Array<[string, string]> {
    const meta = row.metadata
    if (!meta) return []
    return Object.keys(meta).map((k) => [k, String(meta[k])] as [string, string])
  }

  async function loadNames() {
    try {
      serviceNames.value = await listServiceNames()
    } catch (e) {
      ElMessage.error((e as Error).message)
      serviceNames.value = []
    }
  }

  async function loadService() {
    if (!serviceName.value) return
    loading.value = true
    metricsLoading.value = true
    // 三条读端点各自可能挂：健康取不到不能连带假装实例也是空的，所以分开兜
    try {
      health.value = await getHealth(serviceName.value)
    } catch (e) {
      health.value = null
      ElMessage.error((e as Error).message)
    } finally {
      loading.value = false
    }
    try {
      instances.value = await listInstancesOfService(serviceName.value)
    } catch (e) {
      instances.value = []
      ElMessage.error((e as Error).message)
    }
    try {
      metrics.value = await listMetrics({ serviceName: serviceName.value })
    } catch (e) {
      metrics.value = []
      ElMessage.error((e as Error).message)
    } finally {
      metricsLoading.value = false
    }
  }

  function onServiceChange() {
    loadService()
  }

  async function refresh() {
    await loadNames()
    if (!serviceName.value && serviceNames.value.length) {
      serviceName.value = serviceNames.value[0]
    }
    await loadService()
  }

  function openHistory(row: MonitorMetric) {
    historyMetric.value = row.metricName
    range.value = null
    history.value = []
    historyVisible.value = true
    loadHistory()
  }

  async function loadHistory() {
    if (!serviceName.value || !historyMetric.value) return
    historyLoading.value = true
    try {
      history.value = await getMetricHistory({
        serviceName: serviceName.value,
        metricName: historyMetric.value,
        startTime: range.value?.[0] || undefined,
        endTime: range.value?.[1] || undefined,
      })
    } catch (e) {
      ElMessage.error((e as Error).message)
      history.value = []
    } finally {
      historyLoading.value = false
    }
  }

  onMounted(async () => {
    await loadNames()
    if (serviceNames.value.length) {
      serviceName.value = serviceNames.value[0]
      await loadService()
    }
  })
</script>

<style lang="scss" scoped>
  .notice {
    margin-bottom: 12px;
  }
  .health,
  .summary {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    margin-bottom: 16px;
  }
  .cell {
    flex: 1 1 150px;
    padding: 12px 16px;
    border: 1px solid var(--dv-border-1);
    border-radius: var(--dv-radius-md);
    background: var(--dv-bg-1);
  }
  .cell .k {
    display: block;
    font-size: var(--dv-font-xs);
    color: var(--dv-text-3);
  }
  .cell .v {
    font-size: var(--dv-font-lg);
    font-weight: 600;
  }
  .cell .v.bad {
    color: var(--dv-danger);
  }
  .block {
    margin-bottom: 16px;
  }
  .head-row {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }
  .mono {
    font-family: var(--dv-font-mono, monospace);
  }
  .meta {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }
  .bar {
    height: 10px;
    border-radius: 5px;
    background: var(--dv-fill-light);
    overflow: hidden;
  }
  .fill {
    height: 100%;
    background: var(--dv-primary);
  }
</style>
