<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('analysis.title') }}</h2>
        <p class="dv-page-desc">{{ t('analysis.desc') }}</p>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="6">
        <el-card shadow="never">
          <template #header>
            <div class="card-title">{{ t('analysis.selectDataset') }}</div>
          </template>
          <el-select v-model="datasetId" :placeholder="t('analysis.pickDataset')" style="width: 100%" @change="loadDataset">
            <el-option v-for="ds in datasets" :key="ds.id" :label="ds.name" :value="ds.id" />
          </el-select>
          <div class="field-section">
            <h4>{{ t('analysis.dimension') }}</h4>
            <div v-for="d in dimensions" :key="d.field" class="field-item">
              {{ d.displayName }}
              <el-button size="small" link type="primary" @click="addDimension(d.field)">+</el-button>
            </div>
            <p v-if="!dimensions.length" class="dv-text-3">{{ t('common.empty') }}</p>
          </div>
          <div class="field-section">
            <h4>{{ t('analysis.measure') }}</h4>
            <div v-for="m in measures" :key="m.field" class="field-item">
              {{ m.displayName }}
              <el-button size="small" link type="primary" @click="addMeasure(m.field)">+</el-button>
            </div>
            <p v-if="!measures.length" class="dv-text-3">{{ t('common.empty') }}</p>
          </div>
        </el-card>
      </el-col>
      <el-col :span="18">
        <el-card shadow="never">
          <template #header>
            <div class="card-title">{{ t('analysis.resultPreview') }}</div>
          </template>
          <div class="query-config">
            <div class="config-row">
              <span class="config-label">{{ t('analysis.dimension') }}:</span>
              <el-tag v-for="(d, i) in queryDimensions" :key="i" closable @close="removeDim(i)">{{ d.field }}</el-tag>
              <span v-if="!queryDimensions.length" class="dv-text-3">-</span>
            </div>
            <div class="config-row">
              <span class="config-label">{{ t('analysis.measure') }}:</span>
              <el-tag v-for="(m, i) in queryMeasures" :key="i" closable @close="removeMeasure(i)">
                {{ m.aggregation }}({{ m.field }})
              </el-tag>
              <span v-if="!queryMeasures.length" class="dv-text-3">-</span>
            </div>
            <el-button type="primary" @click="executeQuery" :loading="loading">{{ t('analysis.run') }}</el-button>
          </div>
          <div class="chart-area">
            <ChartEngine
              v-if="chartConfig && queryResult"
              :config="chartConfig"
              :data="queryResult"
              width="100%"
              height="320px"
            />
          </div>
          <div class="table-area">
            <el-table :data="queryResult?.rows || []" v-loading="loading" stripe max-height="300">
              <el-table-column
                v-for="col in queryResult?.columns || []"
                :key="col.field"
                :prop="col.field"
                :label="col.displayName || col.field"
              />
              <template #empty>
                <span class="dv-text-3">{{ t('common.empty') }}</span>
              </template>
            </el-table>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
  import { ref, onMounted } from 'vue'
  import { useRoute } from 'vue-router'
  import { ElMessage } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import type { ChartConfig, QueryDataset } from '@dataviz/shared-types'
  import { rowsOf } from '@dataviz/shared-types'
  import { ChartType } from '@dataviz/shared-types'
  import { ChartEngine } from '@dataviz/chart-engine'
  import { executeQuery as execApi, getDataset, listDatasets } from '@dataviz/api-client'

  const { t } = useI18n()
  const route = useRoute()

  const datasets = ref<QueryDataset[]>([])
  const datasetId = ref<string | number>('')
  const dimensions = ref<QueryDataset['dimensions']>([])
  const measures = ref<QueryDataset['measures']>([])
  const queryDimensions = ref<Array<{ field: string }>>([])
  const queryMeasures = ref<Array<{ field: string; aggregation: 'sum' | 'avg' | 'count' | 'max' | 'min' }>>([])
  const queryResult = ref<{ columns: Array<{ field: string; type: string; displayName?: string }>; rows: Record<string, unknown>[] } | null>(null)
  const chartConfig = ref<ChartConfig | null>(null)
  const loading = ref(false)

  async function loadDatasets() {
    try {
      const res = await listDatasets()
      datasets.value = rowsOf(res)
      const preset = route.query.datasetId as string | undefined
      if (preset && datasets.value.some((d) => String(d.id) === preset)) {
        datasetId.value = preset
        loadDataset()
      }
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function loadDataset() {
    if (!datasetId.value) return
    try {
      const ds = await getDataset(datasetId.value)
      dimensions.value = ds.dimensions
      measures.value = ds.measures
      queryDimensions.value = []
      queryMeasures.value = []
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  function addDimension(field: string) {
    queryDimensions.value.push({ field })
  }

  function addMeasure(field: string) {
    queryMeasures.value.push({ field, aggregation: 'sum' })
  }

  function removeDim(i: number) {
    queryDimensions.value.splice(i, 1)
  }

  function removeMeasure(i: number) {
    queryMeasures.value.splice(i, 1)
  }

  async function executeQuery() {
    if (!datasetId.value) {
      ElMessage.warning(t('analysis.pickDataset'))
      return
    }
    loading.value = true
    try {
      const result = await execApi({
        datasetId: datasetId.value,
        dimensions: queryDimensions.value,
        measures: queryMeasures.value,
      })
      queryResult.value = { columns: result.columns, rows: result.rows }
      chartConfig.value = {
        chartType: ChartType.BAR,
        datasetId: datasetId.value,
        dimensions: queryDimensions.value.map((d) => ({ field: d.field })),
        measures: queryMeasures.value.map((m) => ({ field: m.field, alias: m.field, aggregation: m.aggregation })),
        options: {},
      }
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      loading.value = false
    }
  }

  onMounted(loadDatasets)
</script>

<style lang="scss" scoped>
  .card-title {
    font-weight: 600;
  }

  .field-section {
    margin-top: var(--dv-space-lg);

    h4 {
      margin: 0 0 var(--dv-space-sm);
      font-size: var(--dv-font-sm);
      color: var(--dv-text-3);
    }

    .field-item {
      display: flex;
      justify-content: space-between;
      padding: 6px var(--dv-space-sm);
      border-radius: var(--dv-radius-sm);
      font-size: var(--dv-font-md);
      color: var(--dv-text-2);

      &:hover {
        background: var(--dv-surface-sunken);
      }
    }
  }

  .query-config {
    margin-bottom: var(--dv-space-lg);

    .config-row {
      display: flex;
      align-items: center;
      gap: var(--dv-space-sm);
      margin-bottom: var(--dv-space-sm);
    }

    .config-label {
      color: var(--dv-text-3);
      font-size: var(--dv-font-sm);
      width: 52px;
      flex-shrink: 0;
    }
  }

  .chart-area {
    margin-bottom: var(--dv-space-lg);
  }
</style>
