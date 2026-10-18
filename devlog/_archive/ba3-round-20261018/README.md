# BA3 轮临时件归档（2026-10-18）

本轮**没有产品代码改动**，这几件都是"为了确认登录面为什么不通"而生的一次性读取件。台账对应：**docs/10 D85 + 变更记录 BA3 行**、**docs/12 §19.35.6**、**devlog/EDIT_LOG.md 阶段 BA3 章**。

| 文件 | 用途 | 口径 |
|---|---|---|
| `tmp-phones.sql` | 数"还有哪些演示账号绑了手机号"（想换账号绕开日配额） | 只读 `db_user.sys_user`；结果 **6 个**（admin + 901~905）。⚠️ 第一次写成 `db_user.user` ⇒ `ERROR 1146`，表名取自 `information_schema.columns` |
| `tmp-roles.sql` | 看这 6 个账号挂的什么角色 | 只读 `sys_user` × `sys_user_role` × `sys_role`；结果 901~905 全挂 **role 3「普通用户」** ⇒ 换账号跑写面判据会引入 403 语义污染，**此路不通**。⚠️ 第一次写 `r.code` ⇒ `ERROR 1054`，真名 `role_code` |
| `tmp-probe-login.sh` | 判"8087 是被回收还是自己崩"用的存活探针：`/v3/api-docs` 拉全量再数端口 | 只走免登面；读数 **200 / 14,031 B**，跨 8 分钟仍 LISTENING |
| `8087-startup.snapshot.log` | WMI 分离式启动那次的启动日志（**cp 快照**，不是原件） | 原件 `devlog/ba3-8087.log` **在跑着的 JVM 句柄里**（`mv` 报 `Device or resource busy`，只能 `cp`）。⚠️ **这份快照同样不在 git 里** —— `.gitignore:59` 排除 `*.log`，我试着改名成 `.log.data` 时被上面的句柄挡住。**结论：启动日志只在本地留证，台账里引的是字节数与尾部行内容，不是"已入库的文件"** |

**为什么这几件能进 git**：里面只有表名、列名、账号名和端口读数，**没有口令、没有 token、没有连接串**。`tmp-*.sql` 用 `docker exec … MYSQL_PWD="$MYSQL_ROOT_PASSWORD"` 的既有安全路径执行，口令不落盘也不回显。

**没进 git 的本轮证据**：`devlog/ba3-8087.log` / `.err`（`*.log` 排除，且 JVM 句柄占用期间不能动）。

**留在 `devlog/_ay/` 未归档的两件**（它们是复用型运维件，不是临时件）：`start-8087.bat`、`launch-8087-detached.ps1`。
