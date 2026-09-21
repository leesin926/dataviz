/**
 * 自动补全项类型
 */
export interface CompletionItem {
  label: string
  kind: 'keyword' | 'table' | 'column' | 'function' | 'snippet'
  detail?: string
  insertText?: string
  sortText?: string
}

/**
 * SqlCompleter - SQL 自动补全提供器
 * 支持表名、列名、关键字、函数的补全
 */
export class SqlCompleter {
  private tables: Map<string, string[]> = new Map() // table -> columns
  private keywords: string[] = []
  private functions: string[] = []

  constructor() {
    this.initDefaults()
  }

  /**
   * 注册表及其列信息
   */
  registerTable(tableName: string, columns: string[]): void {
    this.tables.set(tableName.toLowerCase(), columns.map((c) => c.toLowerCase()))
  }

  /**
   * 批量注册表
   */
  registerTables(tables: Record<string, string[]>): void {
    Object.entries(tables).forEach(([name, cols]) => {
      this.registerTable(name, cols)
    })
  }

  /**
   * 清除所有表注册
   */
  clearTables(): void {
    this.tables.clear()
  }

  /**
   * 设置关键字列表
   */
  setKeywords(keywords: string[]): void {
    this.keywords = keywords
  }

  /**
   * 设置函数列表
   */
  setFunctions(functions: string[]): void {
    this.functions = functions
  }

  /**
   * 根据上下文获取补全建议
   */
  getCompletions(sql: string, cursorPosition: number): CompletionItem[] {
    const beforeCursor = sql.substring(0, cursorPosition)
    const currentWord = this.extractCurrentWord(beforeCursor)

    if (!currentWord) return []

    const prefix = currentWord.toUpperCase()
    const context = this.analyzeContext(beforeCursor)
    const items: CompletionItem[] = []

    switch (context) {
      case 'column':
        // 补全列名
        this.tables.forEach((columns, table) => {
          columns
            .filter((c) => c.toUpperCase().startsWith(prefix))
            .forEach((col) => {
              items.push({
                label: col,
                kind: 'column',
                detail: `${table}.${col}`,
                sortText: `1_${col}`,
              })
            })
        })
        break

      case 'table':
        // 补全表名
        this.tables.forEach((_, table) => {
          if (table.toUpperCase().startsWith(prefix)) {
            items.push({
              label: table,
              kind: 'table',
              sortText: `2_${table}`,
            })
          }
        })
        break

      case 'function':
        // 补全函数
        this.functions
          .filter((f) => f.startsWith(prefix))
          .forEach((fn) => {
            items.push({
              label: fn,
              kind: 'function',
              insertText: `${fn}(`,
              sortText: `3_${fn}`,
            })
          })
        break

      default:
        // 通用补全：关键字 + 表名 + 函数
        this.keywords
          .filter((k) => k.startsWith(prefix))
          .forEach((kw) => {
            items.push({
              label: kw,
              kind: 'keyword',
              sortText: `0_${kw}`,
            })
          })

        this.tables.forEach((_, table) => {
          if (table.toUpperCase().startsWith(prefix)) {
            items.push({
              label: table,
              kind: 'table',
              sortText: `2_${table}`,
            })
          }
        })

        this.functions
          .filter((f) => f.startsWith(prefix))
          .forEach((fn) => {
            items.push({
              label: fn,
              kind: 'function',
              sortText: `3_${fn}`,
            })
          })
        break
    }

    return items
  }

  /**
   * 提取光标前的当前单词
   */
  private extractCurrentWord(text: string): string {
    const match = text.match(/(\w+)$/)
    return match ? match[1] : ''
  }

  /**
   * 分析当前光标位置的上下文
   */
  private analyzeContext(beforeCursor: string): 'column' | 'table' | 'function' | 'general' {
    const upper = beforeCursor.toUpperCase().trim()

    // SELECT 后 -> 列名 / 函数
    if (/\bSELECT\s+(DISTINCT\s+)?\w*$/.test(upper)) {
      return 'column'
    }

    // 逗号后如果在 SELECT 内 -> 列名
    if (/\bSELECT\b[\s\S]*,\s*\w*$/.test(upper) && !/\bFROM\b[\s\S]*$/.test(upper.split('SELECT').pop() || '')) {
      return 'column'
    }

    // FROM / JOIN 后 -> 表名
    if (/\b(?:FROM|JOIN)\s+\w*$/.test(upper)) {
      return 'table'
    }

    // WHERE / AND / OR 后 -> 列名
    if (/\b(?:WHERE|AND|OR|ON)\s+\w*$/.test(upper)) {
      return 'column'
    }

    // 函数调用
    if (/\w+\(\s*$/.test(beforeCursor)) {
      return 'function'
    }

    return 'general'
  }

  /**
   * 初始化默认关键字和函数
   */
  private initDefaults(): void {
    this.keywords = [
      'SELECT', 'FROM', 'WHERE', 'AND', 'OR', 'NOT', 'IN', 'BETWEEN', 'LIKE',
      'IS', 'NULL', 'AS', 'ON', 'JOIN', 'LEFT', 'RIGHT', 'INNER', 'OUTER',
      'GROUP', 'BY', 'ORDER', 'ASC', 'DESC', 'HAVING', 'LIMIT', 'OFFSET',
      'DISTINCT', 'INSERT', 'INTO', 'VALUES', 'UPDATE', 'SET', 'DELETE',
      'CREATE', 'TABLE', 'ALTER', 'DROP', 'INDEX', 'VIEW', 'UNION', 'ALL',
      'EXISTS', 'CASE', 'WHEN', 'THEN', 'ELSE', 'END', 'WITH',
    ]

    this.functions = [
      'COUNT', 'SUM', 'AVG', 'MAX', 'MIN', 'ABS', 'ROUND', 'CEIL', 'FLOOR',
      'UPPER', 'LOWER', 'TRIM', 'LENGTH', 'SUBSTRING', 'CONCAT', 'REPLACE',
      'NOW', 'CURDATE', 'DATE_FORMAT', 'DATEDIFF', 'YEAR', 'MONTH', 'DAY',
      'COALESCE', 'IFNULL', 'CAST', 'CONVERT', 'GROUP_CONCAT',
    ]
  }
}
