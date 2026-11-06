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

    // 检查多语句：与后端 SqlBuilder.resolveFrom + maskLiteralsAndComments 同一套语义（API-141）。
    // 先掩掉字符串字面量/反引号标识符与注释，分号只有在掩码串里才算语句分隔符；
    // 前端与后端这两层判定必须一致，否则会出现"前端放行、后端拒绝"（或反之）的分歧 —— 那本身就是 bug。
    const statements = SqlParser.maskLiteralsAndComments(trimmed)
      .split(';')
      .filter((s) => s.trim())
    if (statements.length > 1) {
      errors.push(`SQL 包含 ${statements.length} 条语句，禁止一次执行多条`)
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
   * 把字符串字面量、反引号标识符与注释替换成占位符，仅用于校验判据（返回值不进入 SQL）。
   * 移植自后端 SqlBuilder.maskLiteralsAndComments：反斜杠转义与连续两个同名引号的转义都按原样处理，
   * 否则一个 'it''s' 会让后半句逃脱检查，字面量里的 ';' 也会被误当成语句分隔符。
   */
  private static maskLiteralsAndComments(sql: string): string {
    let out = ''
    let i = 0
    const n = sql.length
    while (i < n) {
      const c = sql[i]
      if (c === "'" || c === '"' || c === '`') {
        const identifier = c === '`'
        i++
        while (i < n) {
          const d = sql[i]
          if (!identifier && d === '\\' && i + 1 < n) {
            i += 2
            continue
          }
          if (d === c) {
            if (i + 1 < n && sql[i + 1] === c) {
              i += 2
              continue
            }
            i++
            break
          }
          i++
        }
        out += "''"
        continue
      }
      if (c === '-' && i + 1 < n && sql[i + 1] === '-') {
        i += 2
        while (i < n && sql[i] !== '\n') i++
        out += ' '
        continue
      }
      if (c === '#') {
        while (i < n && sql[i] !== '\n') i++
        out += ' '
        continue
      }
      if (c === '/' && i + 1 < n && sql[i + 1] === '*') {
        i += 2
        while (i + 1 < n && !(sql[i] === '*' && sql[i + 1] === '/')) i++
        i = Math.min(n, i + 2)
        out += ' '
        continue
      }
      out += c
      i++
    }
    return out
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
    // FROM 后可能是一段逗号分隔的表清单（老式连接写法，本平台的数据集里合法存在），
    // 逐项取第一个标识符作表名，遇到下一个子句关键字即停（API-145）。
    const clauseBoundary =
      /\b(?:WHERE|JOIN|INNER|LEFT|RIGHT|FULL|CROSS|OUTER|GROUP|ORDER|HAVING|LIMIT|OFFSET|UNION|SET)\b/i
    const fromRegex = /\bFROM\s+/gi
    let match: RegExpExecArray | null
    while ((match = fromRegex.exec(sql)) !== null) {
      const rest = sql.slice(match.index + match[0].length)
      const boundary = rest.search(clauseBoundary)
      const segment = boundary === -1 ? rest : rest.slice(0, boundary)
      for (const part of segment.split(',')) {
        const name = part.trim().match(/^[`"']?(\w+)/)
        if (name) tables.push(name[1])
      }
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
    // 关键字表与函数表存在同名项（SUM/MAX/…），去重且保持稳定顺序：先关键字命中、再函数（API-146）。
    const seen = new Set<string>()
    const out: string[] = []
    for (const item of [...SQL_KEYWORDS, ...SQL_FUNCTIONS]) {
      if (item.startsWith(upperPrefix) && !seen.has(item)) {
        seen.add(item)
        out.push(item)
      }
    }
    return out
  }
}
