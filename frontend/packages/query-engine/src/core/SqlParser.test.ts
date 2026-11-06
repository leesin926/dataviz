import { describe, expect, it } from 'vitest'
import { SqlParser } from './SqlParser'

describe('query-engine/SqlParser.validate —— 只读语句的结构检查', () => {
  it('空串与纯空白都判为"SQL 不能为空"，且不再跑后续检查', () => {
    expect(SqlParser.validate('')).toEqual({ valid: false, errors: ['SQL 不能为空'] })
    expect(SqlParser.validate('   ')).toEqual({ valid: false, errors: ['SQL 不能为空'] })
  })

  it('SELECT 缺 FROM 只报这一条，合法语句零告警', () => {
    expect(SqlParser.validate('SELECT 1').errors).toEqual(['SELECT 语句缺少 FROM 子句'])
    expect(SqlParser.validate('SELECT id, name FROM sys_user WHERE id = 1')).toEqual({ valid: true, errors: [] })
  })

  it('括号两种失衡分别给出多余右括号 / 缺少右括号', () => {
    expect(SqlParser.validate('SELECT a) FROM t').errors).toEqual(['括号不匹配：多余的右括号'])
    expect(SqlParser.validate('SELECT SUM(a FROM t').errors).toEqual(['括号不匹配：缺少右括号'])
  })

  it('危险操作按整词命中并让 valid 变 false，列名里的同前缀不误报', () => {
    expect(SqlParser.validate('DROP TABLE sys_user')).toEqual({
      valid: false,
      errors: ['警告: SQL 包含危险操作 "DROP"'],
    })
    expect(SqlParser.validate('SELECT id, is_deleted FROM sys_user')).toEqual({ valid: true, errors: [] })
  })

  it('分号多语句被拒绝并报出语句条数，字符串/注释里的分号不算分隔符（API-141）', () => {
    // given：注入面最需要拦的写法，前端现在与后端 resolveFrom 同一套语义（先掩字面量/注释）
    const out = SqlParser.validate('SELECT id FROM sys_user; DROP TABLE sys_user')

    expect(out.valid).toBe(false)
    expect(out.errors).toEqual([
      'SQL 包含 2 条语句，禁止一次执行多条',
      '警告: SQL 包含危险操作 "DROP"',
    ])

    // 纯多读语句此前会 pass，现在按语义拒绝并给出条数
    const twoReads = SqlParser.validate('SELECT 1 FROM t; SELECT 2 FROM d')
    expect(twoReads.valid).toBe(false)
    expect(twoReads.errors).toEqual(['SQL 包含 2 条语句，禁止一次执行多条'])

    // 但字符串字面量与注释里的 ';' 不是分隔符，只读单语句照常放行（尾部单个 ';' 也不算多语句）
    expect(SqlParser.validate("SELECT 'a;b' AS k FROM t")).toEqual({ valid: true, errors: [] })
    expect(SqlParser.validate('SELECT 1 FROM t -- x;y\n')).toEqual({ valid: true, errors: [] })
    expect(SqlParser.validate('SELECT 1 FROM t;')).toEqual({ valid: true, errors: [] })
  })
})

describe('query-engine/SqlParser.format / extractTableNames / getCompletions', () => {
  it('format 把关键字大写并在主句前换行缩进两格；ON 不在断行表里，跟着 JOIN 同行', () => {
    expect(SqlParser.format('select * from t where a = 1')).toBe('SELECT *\n  FROM t\n  WHERE a = 1')
    expect(SqlParser.format('select a from t join d on t.id = d.id where x = 1 and y = 2 order by a desc')).toBe(
      'SELECT a\n  FROM t\n  JOIN d ON t.id = d.id\n  WHERE x = 1\n  AND y = 2\n  ORDER BY a desc',
    )
  })

  it('extractTableNames 覆盖 FROM 与 JOIN 并去重', () => {
    expect(SqlParser.extractTableNames('SELECT * FROM sys_user a JOIN sys_dept b ON a.dept_id = b.id')).toEqual([
      'sys_user',
      'sys_dept',
    ])
    expect(SqlParser.extractTableNames('SELECT * FROM orders o LEFT JOIN order_items i ON o.id = i.order_id')).toEqual([
      'orders',
      'order_items',
    ])
  })

  it('extractTableNames 收全逗号连接的两张表（API-145）', () => {
    // given：隐式笛卡尔积写法，两张表都真实出现在 SQL 里
    expect(SqlParser.extractTableNames('SELECT * FROM sys_user, sys_dept WHERE sys_user.dept_id = sys_dept.id')).toEqual([
      'sys_user',
      'sys_dept',
    ])
  })

  it('getCompletions 前缀大小写不敏感，关键字/函数同名项去重且关键字优先（API-146）', () => {
    expect(SqlParser.getCompletions('sel')).toEqual(['SELECT'])
    expect(SqlParser.getCompletions('SUM')).toEqual(['SUM'])
    expect(SqlParser.getCompletions('max')).toEqual(['MAX'])
  })
})
