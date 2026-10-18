<template>
  <div class="dv-page ai-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('ai.title') }}</h2>
        <p class="dv-page-desc">{{ t('ai.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-tag v-if="status" :type="status.llmConfigured ? 'success' : 'danger'" disable-transitions>
          {{ status.llmConfigured ? t('ai.statusOn', { model: status.model || '-' }) : t('ai.statusOff') }}
        </el-tag>
        <el-button link :loading="statusLoading" @click="loadStatus">{{ t('common.refresh') }}</el-button>
      </div>
    </div>

    <el-alert
      v-if="status && !status.llmConfigured"
      class="llm-note"
      type="warning"
      :closable="false"
      show-icon
      :title="t('ai.statusOff')"
      :description="status.note || t('ai.statusNoNote')"
    />

    <el-tabs v-model="tab">
      <el-tab-pane :label="t('ai.tabChat')" name="chat">
        <div class="chat-layout">
          <div class="chat-side">
            <el-input
              v-model="newTitle"
              :placeholder="t('ai.convTitlePlaceholder')"
              clearable
              @keyup.enter="createConv"
            >
              <template #append>
                <el-button :loading="convCreating" @click="createConv">{{ t('common.create') }}</el-button>
              </template>
            </el-input>
            <div v-loading="convLoading" class="conv-list">
              <p v-if="!conversations.length" class="dv-text-3">{{ t('ai.noConversation') }}</p>
              <div
                v-for="c in conversations"
                :key="c.id"
                class="conv-item"
                :class="{ active: c.id === conversationId }"
                @click="openConversation(c.id)"
              >
                <span class="conv-title">{{ c.title || t('ai.untitled') }}</span>
                <el-button link type="danger" @click.stop="removeConversation(c.id)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
          </div>

          <div class="chat-main">
            <el-scrollbar v-loading="historyLoading" class="chat-body">
              <p v-if="!conversationId" class="dv-text-3">{{ t('ai.pickConversation') }}</p>
              <div v-for="m in messages" :key="m.id" class="chat-msg" :class="m.role">
                <div class="chat-meta">
                  <span>{{ m.role === 'user' ? t('ai.roleUser') : t('ai.roleAssistant') }}</span>
                  <span class="dv-text-3">{{ m.createTime }}</span>
                  <el-tag v-if="m.role !== 'user'" size="small" :type="m.llmUsed ? 'success' : 'info'" disable-transitions>
                    {{ m.llmUsed ? t('ai.llmUsed') : t('ai.llmNotUsed') }}
                  </el-tag>
                </div>
                <div class="chat-content">{{ m.content }}</div>
                <p v-if="m.llmNote" class="dv-text-3">{{ m.llmNote }}</p>
                <pre v-if="m.sqlGenerated" class="dv-mono chat-sql">{{ m.sqlGenerated }}</pre>
              </div>
            </el-scrollbar>
            <div class="chat-input">
              <el-input
                v-model="draft"
                type="textarea"
                :rows="2"
                :placeholder="t('ai.inputPlaceholder')"
                :disabled="!conversationId"
              />
              <el-button type="primary" :loading="sending" :disabled="!conversationId || !draft.trim()" @click="send">
                {{ t('ai.send') }}
              </el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane :label="t('ai.tabNl2sql')" name="nl2sql">
        <div class="dv-toolbar">
          <el-select v-model="dsId" :placeholder="t('ai.pickDatasource')" filterable style="width: 220px">
            <el-option v-for="d in datasources" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
          <el-input v-model="question" :placeholder="t('ai.questionPlaceholder')" style="width: 320px" @keyup.enter="translate" />
          <el-button type="primary" :loading="translating" :disabled="!dsId || !question.trim()" @click="translate">
            {{ t('ai.translate') }}
          </el-button>
          <el-button :loading="explaining" :disabled="!nl2sql?.sql" @click="explain">
            {{ t('ai.explain') }}
          </el-button>
        </div>
        <el-card v-if="nl2sql" shadow="never">
          <div class="result-head">
            <span class="dv-label">{{ t('ai.confidence') }}</span>
            <el-progress :percentage="Math.round((nl2sql.confidence || 0) * 100)" :stroke-width="8" style="width: 160px" />
            <el-tag size="small" :type="nl2sql.llmUsed ? 'success' : 'info'" disable-transitions>
              {{ nl2sql.llmUsed ? t('ai.llmUsed') : t('ai.llmNotUsed') }}
            </el-tag>
          </div>
          <pre class="dv-mono sql-block">{{ nl2sql.sql }}</pre>
          <p v-if="nl2sql.explanation" class="explain-block">{{ nl2sql.explanation }}</p>
          <p v-if="nl2sql.llmNote" class="dv-text-3">{{ nl2sql.llmNote }}</p>
        </el-card>
        <p v-else class="dv-text-3">{{ t('ai.noResult') }}</p>
      </el-tab-pane>

      <el-tab-pane :label="t('ai.tabChart')" name="chart">
        <div class="dv-toolbar">
          <el-input v-model="columnsText" :placeholder="t('ai.columns')" style="width: 240px" />
          <el-button @click="fillDemo">{{ t('ai.demoData') }}</el-button>
          <el-button type="primary" :loading="recommending" @click="recommend">{{ t('ai.recommend') }}</el-button>
        </div>
        <el-input
          v-model="rowsText"
          type="textarea"
          :rows="6"
          class="rows-input"
          :placeholder="t('ai.rowsJson')"
        />
        <el-table v-if="charts.length" :data="charts" stripe class="chart-table">
          <el-table-column prop="chartName" :label="t('ai.chartName')" width="140" />
          <el-table-column prop="chartType" :label="t('common.type')" width="120" class-name="dv-mono" />
          <el-table-column :label="t('ai.score')" width="90" align="right">
            <template #default="{ row }">{{ (row.score ?? 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="reason" :label="t('ai.reason')" min-width="220" show-overflow-tooltip />
          <el-table-column :label="t('ai.mappings')" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ (row.suggestedMappings || []).join(', ') || '-' }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="t('ai.tabInsight')" name="insight">
        <div class="dv-toolbar">
          <el-select v-model="dsId" :placeholder="t('ai.pickDatasource')" filterable style="width: 220px">
            <el-option v-for="d in datasources" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
          <el-select v-model="insightType" filterable allow-create default-first-option style="width: 170px">
            <el-option v-for="it in INSIGHT_TYPES" :key="it" :label="it" :value="it" />
          </el-select>
          <el-input v-model="datasetId" :placeholder="t('ai.datasetId')" style="width: 150px" />
          <el-button type="primary" :loading="generating" :disabled="!insightType" @click="generate">
            {{ t('ai.generateInsight') }}
          </el-button>
          <el-button :loading="insightsLoading" @click="loadInsights">{{ t('common.refresh') }}</el-button>
        </div>
        <el-input
          v-model="insightConfig"
          type="textarea"
          :rows="3"
          class="rows-input"
          :placeholder="t('ai.insightConfig')"
        />
        <div v-loading="insightsLoading" class="insight-list">
          <p v-if="!insights.length" class="dv-text-3">{{ t('common.empty') }}</p>
          <el-card v-for="i in insights" :key="i.id" shadow="never" class="insight-item">
            <div class="result-head">
              <span class="dv-label">{{ i.insightType }}</span>
              <el-tag size="small" :type="i.llmUsed ? 'success' : 'info'" disable-transitions>
                {{ i.llmUsed ? t('ai.llmUsed') : i.llmUsed === false ? t('ai.llmNotUsed') : t('ai.llmUnknown') }}
              </el-tag>
              <span class="dv-text-3">{{ i.createTime }}</span>
            </div>
            <p class="explain-block">{{ i.content }}</p>
            <p v-if="i.llmNote" class="dv-text-3">{{ i.llmNote }}</p>
          </el-card>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
  import { onMounted, ref } from 'vue'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { Delete } from '@element-plus/icons-vue'
  import { useI18n } from 'vue-i18n'
  import type { Datasource } from '@dataviz/shared-types'
  import { rowsOf } from '@dataviz/shared-types'
  import {
    createConversation,
    deleteConversation,
    explainSql,
    generateInsight,
    getAiStatus,
    getChatHistory,
    listConversations,
    listDatasources,
    listInsights,
    recommendChart,
    sendChatMessage,
    translateQuestion,
    type AiInsight,
    type ChartRecommendation,
    type ChatConversation,
    type ChatMessage,
    type Nl2SqlResult,
  } from '@dataviz/api-client'

  const { t } = useI18n()

  /** 后端 insight_type 是自由字符串，没有枚举；这里只给常用四项，允许自填 */
  const INSIGHT_TYPES = ['trend', 'anomaly', 'compare', 'summary']

  const tab = ref('chat')
  const status = ref<Awaited<ReturnType<typeof getAiStatus>> | null>(null)
  const statusLoading = ref(false)

  const conversations = ref<ChatConversation[]>([])
  const conversationId = ref<number | null>(null)
  const messages = ref<ChatMessage[]>([])
  const newTitle = ref('')
  const draft = ref('')
  const convLoading = ref(false)
  const convCreating = ref(false)
  const historyLoading = ref(false)
  const sending = ref(false)

  const datasources = ref<Datasource[]>([])
  const dsId = ref<string | number | ''>('')
  const question = ref('')
  const nl2sql = ref<Nl2SqlResult | null>(null)
  const translating = ref(false)
  const explaining = ref(false)

  const columnsText = ref('')
  const rowsText = ref('')
  const charts = ref<ChartRecommendation[]>([])
  const recommending = ref(false)

  const insightType = ref('trend')
  const datasetId = ref('')
  const insightConfig = ref('')
  const insights = ref<AiInsight[]>([])
  const generating = ref(false)
  const insightsLoading = ref(false)

  async function loadStatus() {
    statusLoading.value = true
    try {
      status.value = await getAiStatus()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      statusLoading.value = false
    }
  }

  async function loadConversations() {
    convLoading.value = true
    try {
      conversations.value = await listConversations()
      if (!conversationId.value && conversations.value.length) {
        await openConversation(conversations.value[0].id)
      }
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      convLoading.value = false
    }
  }

  async function createConv() {
    convCreating.value = true
    try {
      const conv = await createConversation(newTitle.value.trim() || undefined)
      newTitle.value = ''
      conversations.value = [conv, ...conversations.value]
      await openConversation(conv.id)
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      convCreating.value = false
    }
  }

  async function openConversation(id: number) {
    conversationId.value = id
    historyLoading.value = true
    try {
      messages.value = await getChatHistory(id)
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      historyLoading.value = false
    }
  }

  async function removeConversation(id: number) {
    try {
      await ElMessageBox.confirm(t('common.confirmDelete'), t('common.warning'), { type: 'warning' })
    } catch {
      return
    }
    try {
      await deleteConversation(id)
      conversations.value = conversations.value.filter((c) => c.id !== id)
      if (conversationId.value === id) {
        conversationId.value = null
        messages.value = []
      }
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function send() {
    if (!conversationId.value) return
    sending.value = true
    try {
      const reply = await sendChatMessage({ conversationId: conversationId.value, content: draft.value.trim() })
      // 问题也要重读：后端是先落问题再落回答，只 push 回答会让那条问题在界面上消失到刷新为止
      messages.value = await getChatHistory(conversationId.value)
      if (reply?.llmUsed === false) ElMessage.warning(reply?.llmNote || t('ai.llmNotUsed'))
      draft.value = ''
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      sending.value = false
    }
  }

  async function loadDatasources() {
    try {
      const res = await listDatasources({ pageNum: 1, pageSize: 100 })
      datasources.value = rowsOf<Datasource>(res)
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function translate() {
    translating.value = true
    try {
      nl2sql.value = await translateQuestion({ datasourceId: dsId.value as string | number, question: question.value.trim() })
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      translating.value = false
    }
  }

  async function explain() {
    if (!nl2sql.value?.sql) return
    explaining.value = true
    try {
      const res = await explainSql({
        datasourceId: dsId.value === '' ? undefined : (dsId.value as string | number),
        sql: nl2sql.value.sql,
      })
      nl2sql.value = { ...nl2sql.value, explanation: res.explanation, llmUsed: res.llmUsed, llmNote: res.llmNote }
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      explaining.value = false
    }
  }

  function fillDemo() {
    columnsText.value = 'date,region,sales'
    rowsText.value = JSON.stringify(
      [
        { date: '2026-01-01', region: '华东', sales: 120 },
        { date: '2026-02-01', region: '华东', sales: 150 },
        { date: '2026-01-01', region: '华南', sales: 90 },
        { date: '2026-02-01', region: '华南', sales: 132 },
      ],
      null,
      2,
    )
  }

  async function recommend() {
    const columns = columnsText.value.split(',').map((c) => c.trim()).filter(Boolean)
    if (!columns.length) {
      ElMessage.warning(t('ai.columnsRequired'))
      return
    }
    let rows: Array<Record<string, unknown>>
    try {
      const parsed = JSON.parse(rowsText.value || '[]')
      if (!Array.isArray(parsed)) throw new Error(t('ai.badRowsJson'))
      rows = parsed as Array<Record<string, unknown>>
    } catch (e) {
      ElMessage.error((e as Error).message || t('ai.badRowsJson'))
      return
    }
    recommending.value = true
    try {
      charts.value = await recommendChart(columns, rows)
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      recommending.value = false
    }
  }

  async function generate() {
    let config: Record<string, unknown> | undefined
    if (insightConfig.value.trim()) {
      try {
        config = JSON.parse(insightConfig.value) as Record<string, unknown>
      } catch {
        ElMessage.error(t('ai.badConfigJson'))
        return
      }
    }
    generating.value = true
    try {
      await generateInsight({
        datasourceId: dsId.value === '' ? undefined : (dsId.value as string | number),
        datasetId: datasetId.value ? datasetId.value : undefined,
        insightType: insightType.value,
        config,
      })
      await loadInsights()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      generating.value = false
    }
  }

  async function loadInsights() {
    insightsLoading.value = true
    try {
      insights.value = await listInsights({
        datasourceId: dsId.value === '' ? undefined : (dsId.value as string | number),
        datasetId: datasetId.value ? datasetId.value : undefined,
        insightType: insightType.value || undefined,
      })
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      insightsLoading.value = false
    }
  }

  onMounted(() => {
    loadStatus()
    loadConversations()
    loadDatasources()
    loadInsights()
  })
</script>

<style lang="scss" scoped>
  .llm-note {
    margin-bottom: 12px;
  }
  .chat-layout {
    display: flex;
    gap: 12px;
    height: 460px;
  }
  .chat-side {
    width: 220px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .conv-list {
    flex: 1;
    overflow: auto;
  }
  .conv-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 6px 8px;
    border-radius: 6px;
    cursor: pointer;
    font-size: 13px;
  }
  .conv-item:hover {
    background: var(--dv-surface-2);
  }
  .conv-item.active {
    background: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
  }
  .conv-title {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .chat-main {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    border: 1px solid var(--dv-border-2);
    border-radius: 8px;
  }
  .chat-body {
    flex: 1;
    padding: 12px;
  }
  .chat-msg {
    margin-bottom: 12px;
  }
  .chat-msg.user .chat-content {
    background: var(--el-color-primary-light-9);
  }
  .chat-meta {
    display: flex;
    gap: 8px;
    align-items: center;
    font-size: 12px;
    color: var(--dv-text-3);
  }
  .chat-content {
    padding: 8px 10px;
    border-radius: 6px;
    background: var(--dv-surface-1);
    white-space: pre-wrap;
  }
  .chat-sql {
    margin: 6px 0 0;
    font-size: 12px;
    white-space: pre-wrap;
  }
  .chat-input {
    display: flex;
    gap: 8px;
    padding: 8px;
    border-top: 1px solid var(--dv-border-2);
  }
  .result-head {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 8px;
  }
  .sql-block {
    margin: 0;
    padding: 10px;
    background: var(--dv-surface-1);
    border-radius: 6px;
    white-space: pre-wrap;
    word-break: break-all;
  }
  .explain-block {
    margin: 8px 0 0;
    white-space: pre-wrap;
  }
  .rows-input {
    margin-bottom: 12px;
  }
  .chart-table {
    margin-bottom: 12px;
  }
  .insight-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .insight-item {
    width: 100%;
  }
</style>
