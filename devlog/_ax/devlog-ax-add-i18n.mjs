/**
 * AX1/AX2 i18n 插入：把 ai.* / file.* 两个命名空间与 nav.ai / nav.files 写进四份 locale。
 * 一次性脚本：键序按"跟随同组前一个键"插入，其余键原样保留。
 */
import fs from 'node:fs'

function readJson(p) {
  return JSON.parse(fs.readFileSync(p, 'utf8'))
}
function writeJson(p, obj) {
  fs.writeFileSync(p, JSON.stringify(obj, null, 2) + '\n', 'utf8')
}
/** 在 afterKey 之后插入若干键；找不到 afterKey 就追加到末尾 */
function insertAfter(target, afterKey, entries) {
  const out = {}
  let inserted = false
  for (const [k, v] of Object.entries(target)) {
    out[k] = v
    if (k === afterKey) {
      for (const [nk, nv] of Object.entries(entries)) {
        out[nk] = nv
        inserted = true
      }
    }
  }
  if (!inserted) Object.assign(out, entries)
  return out
}

const PC_WEB = 'apps/pc-web/src/locales'
const ADMIN = 'apps/admin/src/locales'

const aiZh = {
  title: 'AI 助手',
  desc: '对话问答、自然语言转 SQL、图表推荐与数据洞察。四组能力都走 ai-service，本页只是入口。',
  statusOn: '已接入：{model}',
  statusOff: '未接入大模型',
  statusNoNote: '后端未给出原因',
  tabChat: '助手对话',
  tabNl2sql: '自然语言转 SQL',
  tabChart: '图表推荐',
  tabInsight: '数据洞察',
  convTitlePlaceholder: '新会话标题（可留空）',
  noConversation: '还没有会话，先建一个',
  untitled: '未命名会话',
  pickConversation: '选择或新建一个会话',
  roleUser: '我',
  roleAssistant: '助手',
  llmUsed: '模型生成',
  llmNotUsed: '未走模型',
  llmUnknown: '未标注',
  inputPlaceholder: '输入问题，Enter 发送',
  send: '发送',
  pickDatasource: '选择数据源',
  questionPlaceholder: '例如：最近 7 天各地区的销售额是多少',
  translate: '生成 SQL',
  explain: '解释这段 SQL',
  confidence: '置信度',
  noResult: '还没有结果',
  columns: '列名，逗号分隔',
  demoData: '填入示例数据',
  recommend: '推荐图表',
  chartName: '图表',
  score: '评分',
  reason: '理由',
  mappings: '建议字段映射',
  datasetId: '数据集 ID（可留空）',
  generateInsight: '生成洞察',
  insightConfig: '附加配置 config（JSON，可留空）',
  columnsRequired: '至少要有一列',
  badRowsJson: '行数据必须是 JSON 数组',
  badConfigJson: 'config 必须是合法 JSON 对象',
}

const aiEn = {
  title: 'AI Assistant',
  desc: 'Chat, NL2SQL, chart recommendation and insights. All four go through ai-service; this page is just the entry point.',
  statusOn: 'Connected: {model}',
  statusOff: 'No model connected',
  statusNoNote: 'Backend gave no reason',
  tabChat: 'Chat',
  tabNl2sql: 'NL2SQL',
  tabChart: 'Chart recommendation',
  tabInsight: 'Insights',
  convTitlePlaceholder: 'New conversation title (optional)',
  noConversation: 'No conversations yet',
  untitled: 'Untitled',
  pickConversation: 'Pick or create a conversation',
  roleUser: 'Me',
  roleAssistant: 'Assistant',
  llmUsed: 'Model output',
  llmNotUsed: 'Not from model',
  llmUnknown: 'Not labelled',
  inputPlaceholder: 'Ask something, Enter to send',
  send: 'Send',
  pickDatasource: 'Select datasource',
  questionPlaceholder: 'e.g. sales by region over the last 7 days',
  translate: 'Generate SQL',
  explain: 'Explain this SQL',
  confidence: 'Confidence',
  noResult: 'No result yet',
  columns: 'Column names, comma separated',
  demoData: 'Fill demo data',
  recommend: 'Recommend charts',
  chartName: 'Chart',
  score: 'Score',
  reason: 'Reason',
  mappings: 'Suggested mappings',
  datasetId: 'Dataset ID (optional)',
  generateInsight: 'Generate insight',
  insightConfig: 'Extra config (JSON, optional)',
  columnsRequired: 'At least one column is required',
  badRowsJson: 'Rows must be a JSON array',
  badConfigJson: 'config must be a valid JSON object',
}

const fileZh = {
  title: '文件管理',
  desc: 'file-service 里已上传的对象。上传与删除要 file:write，图片另有一条免登直读地址给大屏用。',
  upload: '上传文件',
  keyword: '文件名关键字',
  bizType: '业务类型',
  uploaderId: '上传者 ID',
  perPage: '每页',
  publicNote: '/view/{id} 只放行 image/*，浏览器直接取用、不带登录态；download 需要登录与 file:read。',
  name: '文件名',
  mime: '类型',
  size: '大小',
  bucket: '存储桶',
  uploader: '上传者',
  download: '下载',
  uploaded: '上传成功',
  confirmDeleteRow: '删除「{name}」？服务端会同时删掉物理文件，删掉的背景图会让大屏当场空白。',
}

const fileEn = {
  title: 'File management',
  desc: 'Objects already stored in file-service. Upload and delete need file:write; images have a separate public read path for dashboards.',
  upload: 'Upload',
  keyword: 'File name keyword',
  bizType: 'Business type',
  uploaderId: 'Uploader ID',
  perPage: 'per page',
  publicNote: '/view/{id} only serves image/* without a session token; download needs login and file:read.',
  name: 'File name',
  mime: 'Type',
  size: 'Size',
  bucket: 'Bucket',
  uploader: 'Uploader',
  download: 'Download',
  uploaded: 'Uploaded',
  confirmDeleteRow: 'Delete "{name}"? The physical file goes with it, and a deleted background image blanks the dashboard.',
}

const jobs = [
  { dir: PC_WEB, file: 'zh-CN.json', navAfter: 'analysis', navKey: 'ai', ns: 'ai', nsAfter: 'collab', value: aiZh },
  { dir: PC_WEB, file: 'en-US.json', navAfter: 'analysis', navKey: 'ai', ns: 'ai', nsAfter: 'collab', value: aiEn },
  { dir: ADMIN, file: 'zh-CN.json', navAfter: 'permRules', navKey: 'files', ns: 'file', nsAfter: 'approval', value: fileZh },
  { dir: ADMIN, file: 'en-US.json', navAfter: 'permRules', navKey: 'files', ns: 'file', nsAfter: 'approval', value: fileEn },
]

for (const j of jobs) {
  const p = `${j.dir}/${j.file}`
  const doc = readJson(p)
  doc.nav = insertAfter(doc.nav, j.navAfter, { [j.navKey]: j.value.__nav || doc[j.ns]?.title })
  if (j.navKey === 'ai') doc.nav.ai = j.value === aiZh ? 'AI 助手' : 'AI Assistant'
  if (j.navKey === 'files') doc.nav.files = j.value === fileZh ? '文件管理' : 'Files'
  doc[j.ns] = j.value
  writeJson(p, doc)
  console.log('updated', p, 'nav.' + j.navKey + '=' + doc.nav[j.navKey], 'ns keys', Object.keys(j.value).length)
}
