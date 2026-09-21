/**
 * SQL 关键字列表
 */
const SQL_KEYWORDS = [
  'SELECT', 'FROM', 'WHERE', 'AND', 'OR', 'NOT', 'IN', 'BETWEEN', 'LIKE',
  'IS', 'NULL', 'AS', 'ON', 'JOIN', 'LEFT', 'RIGHT', 'INNER', 'OUTER',
  'FULL', 'CROSS', 'UNION', 'ALL', 'INSERT', 'INTO', 'VALUES', 'UPDATE',
  'SET', 'DELETE', 'CREATE', 'TABLE', 'ALTER', 'DROP', 'INDEX', 'VIEW',
  'GROUP', 'BY', 'ORDER', 'ASC', 'DESC', 'HAVING', 'LIMIT', 'OFFSET',
  'DISTINCT', 'COUNT', 'SUM', 'AVG', 'MAX', 'MIN', 'CASE', 'WHEN',
  'THEN', 'ELSE', 'END', 'EXISTS', 'ANY', 'SOME', 'WITH', 'RECURSIVE',
  'OVER', 'PARTITION', 'ROW_NUMBER', 'RANK', 'DENSE_RANK', 'WINDOW',
  'EXPLAIN', 'ANALYZE', 'SHOW', 'DESCRIBE', 'TRUNCATE',
]

/**
 * SQL 函数列表
 */
const SQL_FUNCTIONS = [
  'COUNT', 'SUM', 'AVG', 'MAX', 'MIN', 'ABS', 'CEIL', 'FLOOR', 'ROUND',
  'UPPER', 'LOWER', 'TRIM', 'LTRIM', 'RTRIM', 'LENGTH', 'SUBSTRING',
  'CONCAT', 'REPLACE', 'REVERSE', 'NOW', 'CURDATE', 'DATE_FORMAT',
  'DATEDIFF', 'DATE_ADD', 'DATE_SUB', 'YEAR', 'MONTH', 'DAY', 'HOUR',
  'MINUTE', 'SECOND', 'COALESCE', 'IFNULL', 'NULLIF', 'CAST', 'CONVERT',
  'GROUP_CONCAT', 'CONCAT_WS', 'LEFT', 'RIGHT', 'LPAD', 'RPAD',
  'IF', 'IIF', 'NVL', 'NVL2', 'DECODE', 'SIGN', 'MOD', 'POWER', 'SQRT',
  'LOG', 'LOG10', 'EXP', 'RAND',
]

/**
 * SqlParser - SQL 解析与验证
 */
export class SqlParser {
  /**
   * 基础 SQL 语法验证
   */
  static validate(sql: string): { valid: boolean; errors: string[] } {
    const errors: string[] = []
    const trimmed = sql.trim()

    if (!trimmed) {
      errors.push('SQL 不能为空')
      return { valid: false, errors }
    }

    // 检查基本结构
    const upperSql = trimmed.toUpperCase()

    // SELECT 语句检查
    if (upperSql.startsWith('SELECT')) {
      if (!upperSql.includes('FROM')) {
        errors.push('SELECT 语句缺少 FROM 子句')
      }
    }

    // 检查括号匹配
    let parenDepth = 0
    for (const char of trimmed) {
      if (char === '(') parenDepth++
      if (char === ')') parenDepth--
      if (parenDepth < 0) {
        errors.push('括号不匹配：多余的右括号')
        break
      }
    }
    if (parenDepth > 0) {
      errors.push('括号不匹配：缺少右括号')
    }

    // 检查分号
    const statements = trimmed.split(';').filter((s) => s.trim())
    if (statements.length > 1) {
      // 多语句检测（允许但提示）
    }

    // 检查危险操作
    const dangerousOps = ['DROP', 'TRUNCATE', 'DELETE', 'ALTER']
    for (const op of dangerousOps) {
      const regex = new RegExp(`\\b${op}\\b`, 'i')
      if (regex.test(trimmed)) {
        errors.push(`警告: SQL 包含危险操作 "${op}"`)
      }
    }

    return { valid: errors.length === 0, errors }
  }

  /**
   * 格式化 SQL（基础美化）
   */
  static format(sql: string): string {
    const keywords = [
      'SELECT', 'FROM', 'WHERE', 'AND', 'OR', 'JOIN', 'LEFT JOIN',
      'RIGHT JOIN', 'INNER JOIN', 'OUTER JOIN', 'FULL JOIN', 'CROSS JOIN',
      'ON', 'GROUP BY', 'ORDER BY', 'HAVING', 'LIMIT', 'OFFSET',
      'UNION', 'UNION ALL', 'INSERT INTO', 'VALUES', 'UPDATE', 'SET',
      'DELETE FROM', 'CREATE TABLE', 'ALTER TABLE', 'DROP TABLE',
    ]

    let formatted = sql.trim()

    // 关键字大写
    keywords.forEach((kw) => {
      const regex = new RegExp(`\\b${kw}\\b`, 'gi')
      formatted = formatted.replace(regex, kw)
    })

    // 在主要关键字前换行
    const breakKeywords = [
      'SELECT', 'FROM', 'WHERE', 'AND', 'OR', 'GROUP BY',
      'ORDER BY', 'HAVING', 'LIMIT', 'OFFSET', 'JOIN',
      'LEFT JOIN', 'RIGHT JOIN', 'INNER JOIN', 'UNION',
    ]

    breakKeywords.forEach((kw) => {
      const regex = new RegExp(`\\s+(${kw})\\b`, 'gi')
      formatted = formatted.replace(regex, `\n  $1`)
    })

    return formatted
  }

  /**
   * 提取表名
   */
  static extractTableNames(sql: string): string[] {
    const tables: string[] = []
    const regex = /\bFROM\s+([`"']?)(\w+)\1/gi
    let match: RegExpExecArray | null

    while ((match = regex.exec(sql)) !== null) {
      tables.push(match[2])
    }

    // JOIN 表名
    const joinRegex = /\bJOIN\s+([`"']?)(\w+)\1/gi
    while ((match = joinRegex.exec(sql)) !== null) {
      tables.push(match[2])
    }

    return [...new Set(tables)]
  }

  /**
   * 获取 SQL 关键字列表
   */
  static getKeywords(): string[] {
    return SQL_KEYWORDS
  }

  /**
   * 获取 SQL 函数列表
   */
  static getFunctions(): string[] {
    return SQL_FUNCTIONS
  }

  /**
   * 获取自动补全候选项
   */
  static getCompletions(prefix: string): string[] {
    const upperPrefix = prefix.toUpperCase()
    const all = [...SQL_KEYWORDS, ...SQL_FUNCTIONS]
    return all.filter((item) => item.startsWith(upperPrefix))
  }
}
