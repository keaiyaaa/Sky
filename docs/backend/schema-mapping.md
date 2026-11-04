# Android 本地数据与后端表映射

状态：首期映射草案。最新合并副本与 11.4 参考版存在巡检模块差异；此映射以目标副本当前代码为准，参考版用于识别变更。它不代表已创建服务器表或执行迁移。

## 1. 核心映射

| Android 数据来源 | 后端表/聚合 | 负责模块 | 映射与限制 |
|---|---|---|---|
| 登录账号与用户资料 | `users` | RowanYu（认证核心）、彭诗情（资料） | 全系统只保留一个账号实体。旧字段 `password` 改为语义明确的 BCrypt 哈希字段；密码原文不迁移。用户 ID 在 API 中作为字符串。 |
| 新增服务端会话 | `auth_sessions`（新增） | RowanYu | 保存令牌哈希、有效期、撤销时间、会话族和轮换关系；不对应 Android 本地明文密码。 |
| `peng_local_features.db` 设备记录 | `drones` | 彭诗情 | 仅映射用户明确保存的设备记录。客户端上报序列号不自动构成可信物理设备所有权或 DJI 连接状态。 |
| `route_planning.db` 的 `routes` | `flight_routes` | 冯梓鹏 | 本地 ID 是字符串，云端原设计 ID 为数字；增加稳定 `clientId` 映射，不直接转换或复用数字 ID。归属从认证上下文取得。 |
| `route_planning.db` 的 `waypoints` | `waypoints` | 冯梓鹏 | 以航线为事务聚合，保持顺序及稳定客户端标识。原 14 表设计缺少本地的高度、动作等字段，需先补字段映射再编写迁移。 |
| 本地巡检运行、航点快照、运行事件 | `mission_runs`、`mission_run_waypoints`、`mission_run_events`（新增） | 冯梓鹏 | 运行快照在首次保存后不可随航线编辑变化；事件以稳定序号幂等写入。`LOCAL_SIMULATION` 等来源必须显式保留。 |
| Room `inspection_db.tasks` | `inspection_tasks` | 许培扬 | 目标副本 `InspectionTask` 暴露本地数据库 `id: Long` 和 `date`，但 Mock/MSDK 来源的 `id=0`。API 使用稳定字符串 `clientId`；数据库主键不能作为跨设备 ID。状态为自由字符串，需另定映射。 |
| Room `inspection_db.analysis` | `inspection_records`、`inspection_record_details`；必要时关联 `ai_records` | 许培扬，冯梓鹏提供运行来源 | 目标副本新增 `confidence`、`tokenCost`、`totalTokens`、`analysisType`、`analyzedAt`。代码约定 `analyzedAt=0` 表示种子/预置数据，未经过真实分析；必须排除或保留演示标记，不能仅凭字段存在就认定是真实 AI。关联基数和类型字典需由负责人确认。 |
| `tianshu_prefs` 本地设置 | `user_settings` | 彭诗情 | 设置云端保存状态与设备实际下发状态分开表达；云端保存成功不代表无人机设置已应用。 |
| 本地或客户端业务事件 | `flight_logs` | 彭诗情与各事件生产者 | 运行事件通过只读视图聚合；其他连接、设置或指令事件按稳定事件 ID 去重，不复制整份运行事件。 |
| 客户端图片、视频、PDF | `media_files`（新增）及文件卷 | 许培扬 | 数据库只保存随机文件键、用户归属、类型、大小、校验和、状态等元数据。文件需鉴权下载，不公开本地路径。 |
| 同步操作与服务端变化 | `sync_receipts`、`sync_changes`（新增） | RowanYu 协调；各模块适配 | 收据、业务写入与变化游标同事务提交；变化按用户隔离并保留删除墓碑。 |

## 2. 设计输入与分期

已有《天枢巡检系统数据库字段设计_v2.md》包含 `users`、`drones`、`flight_routes`、`waypoints`、`inspection_tasks`、`inspection_records`、`inspection_record_details`、`ai_records`、`work_orders`、`vip_packages`、`token_transactions`、`orders`、`user_settings`、`system_configs` 等表。该设计是输入，不等同于可直接建库的迁移脚本。

首期优先处理账号、设备、航线、运行历史、巡检任务和设置；会话、媒体及同步表为需要补充的新结构。`ai_records`、工单、VIP、订单、Token 流水和系统发布配置按业务阶段进入，不为骨架预建全部业务表。

## 3. 映射规则

1. 每张用户数据表有服务端用户归属字段和适当索引；写入时从认证主体取所有者，不信任客户端传来的 `userId`。
2. API 的 `id` 一律是字符串。对现有长整型主键，序列化为十进制字符串；客户端本地标识通过独立稳定键映射。
3. 同步实体具有版本、更新时间和删除传播语义。无对应本地 outbox 的业务不得宣称已支持离线同步。
4. 任务、航线、航点、运行快照及运行事件按聚合边界使用事务，失败不得留下部分数据。
5. 时间统一存储和传输为 UTC；媒体二进制不进数据库，也不进入同步 JSON。
6. 只迁移用户选择归属且通过数据清理规则的本地记录。Mock 种子、空库演示数据和 `analyzedAt=0` 的预置分析默认排除。
7. 具体字段、唯一键、外键、删除策略和索引由对应负责人写入版本化迁移；RowanYu 协调迁移编号，不直接代替业务负责人定义字段。

## 4. 目标副本中发现的本地迁移风险

目标副本的 `AppDatabase` 从版本 1 升到版本 2，`AnalysisEntity` 增加了分析元数据字段；当前 builder 仍配置 `fallbackToDestructiveMigration()`，本轮未发现 1→2 的显式迁移。Room 对没有匹配迁移的已有数据库会走破坏式回退，可能清空已有任务与分析数据。此项属于 Android 本地数据库迁移，未在本次后端范围内修改；在向用户发布升级前应补充并验证保留数据的迁移。

## 5. 待业务负责人确认

- 航线与航点 API 字段如何覆盖本地 `altitude`、`action`、示例安全规则及原表结构缺项。
- 任务状态和日期如何映射到稳定任务 ID；Mock/MSDK 的 `id=0` 与演示任务如何识别、过滤。
- 巡检记录、任务、运行与分析结果之间的唯一关联及状态来源。
- `analysisType` 的允许值、置信度范围、token 计费含义及 `analyzedAt` 时间单位。
- 设备记录的可信来源、解绑与重复序列号处理。
- 设置字段清单、默认值和 `cloudSaved` / `deviceApplied` 两种状态。

在上述字段契约确认前，不创建业务迁移、不执行数据库变更，也不批量导入现有手机数据。
