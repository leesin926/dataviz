# AW5 · 三份待执行 SQL 的落库前后校验脚本（2026-10-17，任务 #165，报告 19.31.8）

这四份是**只读校验**与**快照备份**脚本，本身不含 DDL/INSERT —— 真正执行的三份是
`deploy/sql/patch/2026-10-16-{sys-license,data-permission-rule,r1-permission-codes}.sql`。

跑法（口令只在容器内展开，不落本机、不打印）：

```
docker exec -i dataviz-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4' < devlog/_archive/aw5-sql-20261017/<file>.sql
```

| 文件 | 用途 | 可复用在哪 |
|---|---|---|
| `pre.sql` | 执行前现状：两张新表在不在、`sys_permission` 有效行数、三个角色各自授权行数、五个新目录哪个已有目录行 | 任何"台账说待执行"的补丁开工前 —— **待执行 ≠ 未执行**（D72） |
| `backup.sql` | `CREATE TABLE IF NOT EXISTS zz_bak_<date>_<tbl> AS SELECT` 快照 | 改名代替删除的既有口径；`mysqldump` 在本仓属策略受限 |
| `post.sql` | 执行后读数 + 中文 `HEX()` 校验 + 孤儿子节点检查 | **D31**：中文灌错不可逆，所以看字节不看屏幕 |
| `delta.sql` | 与 `zz_bak_*` 快照逐行 diff（旧行变更数、新增行数、各角色行数变化） | "总数相等"不算通过，要的是**变更 0 行**这种差值读数 |

⚠️ `post.sql` 里原有一行标签写 `live_role2_alive`、`WHERE` 却用 `role_id=3` —— 那份读数（10）是 role 3 的，
role 2 重跑为 **28**。**标签和条件必须成对读**，否则校验的是脚本不是数据（已记入 19.31.8 与 EDIT_LOG §九）。
