# Phase 01 执行计划：基线巩固与缺陷修复

## A. 阶段基本信息

| 项 | 内容 |
|---|---|
| 阶段名称 | 基线巩固与缺陷修复（Phase 01） |
| 目标 | 修复审计确认的确定性缺陷（D-02/D-06/D-07/D-09/D-10），隔离死代码（D-15），固化可重复的构建/测试基线；不引入新功能，不动 DJI 调用时序 |
| 对应总体计划章节 | `docs/02-总体开发计划.md` §5 表 Phase 01 行、§6 里程碑 M1 |
| 依赖的前置阶段 | 无（本阶段是起点） |
| 当前输入条件 | 基线 commit `30772d3`，构建/测试绿色（6/6）；审计报告 D 编号问题清单 |
| 阶段边界 | 不修改任何 DJI 注册/调用时序；不删除文件（死代码仅隔离：从 Manifest 与导航中摘除并加 `@Deprecated` 注释，物理删除放 Phase 02 复审后）；不触碰后端；不改数据库 schema |

## B. Skill 调用声明

以下 Skill 均已本地核验存在（`E:\WorkBuddy_International\skill\<name>\SKILL.md`）。**调用方式：会话内读取对应 SKILL.md 全文并按其流程执行**（本环境无独立 Skill 运行器，读取即加载，遵循其检查表）。

| Skill | 完整路径 | 调用时机 | 目的 | 输入 | 预期输出 | 验证方法 |
|---|---|---|---|---|---|---|
| using-superpowers | `E:\WorkBuddy_International\skill\using-superpowers\SKILL.md` | 会话开始第一动作 | 建立 Skill 发现与选择纪律 | 任务描述 | 已声明的 Skill 使用计划 | 对话中明确 "Using [skill] to [purpose]" |
| brainstorming（bounded 路径） | `...\brainstorming\SKILL.md` | 每个 Task 动代码前 | 确认小步改动的方案与边界（本阶段全部为 bounded 改动，短设计在对话中呈现并获用户批准） | 任务编号 + 证据路径 | 2-4 句设计 + 用户 yes | 用户明确批准记录 |
| writing-plans | `...\writing-plans\SKILL.md` | 阶段开工时（本文件已承担计划职能，执行时按其"任务简报"粒度读取对应 Task 节） | 任务级步骤展开（RED-GREEN 步骤化） | 本文件对应 Task | 步骤清单（写失败测试→跑→实现→跑→提交） | 步骤均有 Expected 行 |
| executing-plans | `...\executing-plans\SKILL.md` | 计划批准后逐任务执行 | 内联执行纪律（ledger、Ruling 记录、不停顿询问） | 本计划文件 | `.superpowers/sdd/` 工作区与 progress 台账 | 台账中每任务有 complete 行 |
| test-driven-development | `...\test-driven-development\SKILL.md` | Task 1-5 每个修复 | 先失败测试后修复 | 目标缺陷 | 红绿记录 | 测试先 FAIL 后 PASS 的输出 |
| systematic-debugging | `...\systematic-debugging\SKILL.md` | 任何修复未按预期通过时 | 根因调查，禁止症状补丁 | 失败输出 | 根因结论（Phase 1 证据） | 调查记录先于修复 |
| verification-before-completion | `...\verification-before-completion\SKILL.md` | 每任务收尾与阶段收尾 | 完成声明必须有新跑的验证证据 | 验证命令 | 命令+退出码+计数 | 台账/汇报中含输出摘要 |
| requesting-code-review | `...\requesting-code-review\SKILL.md` | 阶段全部分支完成、合并前 | 派发审查子代理（模板 `requesting-code-review\code-reviewer.md`） | BASE/HEAD SHA + 计划路径 | 分级 findings | Critical/Important 修复后复审 |
| using-git-worktrees | `...\using-git-worktrees\SKILL.md` | 开工建工作区 | 隔离工作区 `feature/phase-01` | — | worktree 或用户同意就地分支 | `git branch --show-current` |

## C. 原子任务拆分

> 所有"建议新建路径"均已标注；其余为现存文件。每任务回滚方案统一：`git revert <task-commit>`（分支上独立提交保证可回滚）。

### Task 01：工作区与基线确认

| 项 | 内容 |
|---|---|
| 编号/名称 | 01 / 建立隔离工作区并复验基线 |
| 目标 | 在 `feature/phase-01` 分支获得绿色起点 |
| 前置条件 | 用户批准开始 Phase 01 |
| 涉及文件 | 无业务文件；新建分支 |
| 具体操作 | ① `using-git-worktrees` 检测隔离（无原生工具则 `git worktree add` 或经同意就地建分支）② `./gradlew :app:assembleDebug :app:testDebugUnitTest` 复验 ③ 记录基线 SHA |
| 应调用 Skill | using-git-worktrees、verification-before-completion |
| 需要编写/执行的测试 | 既有 6 个单测全部通过 |
| 验证命令 | `git branch --show-current` → feature/phase-01；`./gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL |
| 预期结果 | 分支就绪，测试 6/6 |
| 完成标准 | 两条命令输出符合预期并记录 |
| 回滚/恢复 | 删除分支/worktree 即可，无代码变更 |

### Task 02：修复任务中心点击索引错位（D-02，P0）

| 项 | 内容 |
|---|---|
| 编号/名称 | 02 / 修复 asuka 任务点击索引错位 |
| 目标 | 任意日期组的任务点击后详情页正确打开 |
| 前置条件 | Task 01 完成；用户批准修复方案（bounded） |
| 涉及文件 | 修改 `app/src/main/java/com/example/msdksample/asuka/MainActivity.kt:87-89,148-149`；新建测试 `app/src/test/java/com/example/msdksample/asuka/TaskIndexResolverTest.kt`（**建议新建路径**） |
| 具体操作 | ① 提取纯函数 `resolveTaskIndex(groupTaskCount, listPosition, groupStartPos): Int?`（组内下标换算）并 TDD：第二组唯一任务 → 0 ② MainActivity 点击回调改传组内下标 ③ MissionDetailActivity 接收侧核对 `getTask(date, taskIndex)` 语义（`InspectionRepository.kt:117`） |
| 应调用 Skill | brainstorming(bounded)、test-driven-development |
| 测试 | 纯 JVM 测试：组内换算含边界（首组/末组/越界返 null） |
| 验证命令 | `./gradlew :app:testDebugUnitTest --tests "com.example.msdksample.asuka.*"` → PASS；全量 `testDebugUnitTest` → 全绿 |
| 预期结果 | 模拟器手工点检：2026-04-10 组任务可进详情（进模拟器冒烟时复核） |
| 完成标准 | 单测红绿记录 + 全量绿 + 提交 |
| 回滚/恢复 | `git revert`；详情页回退到"空白"现状（已知缺陷，无数据损坏风险） |

### Task 03：修复 LLM 围栏剥离损坏（D-07 代码部分，P1）

| 项 | 内容 |
|---|---|
| 编号/名称 | 03 / 修复 LLMService JSON 围栏剥离 |
| 目标 | 模型返回 ```json 围栏时正确剥离（Key 仍为占位符，在线链路打通属 Phase 04，本任务只修解析） |
| 前置条件 | Task 01 完成 |
| 涉及文件 | 修改 `aerial/LLMService.kt:93-94`；新建 `app/src/test/java/com/example/msdksample/aerial/JsonFenceStripperTest.kt`（**建议新建路径**）；建议新建 `aerial/JsonFenceStripper.kt` 承载纯函数 |
| 具体操作 | ① 抽取 `stripJsonFence(raw: String): String` 纯函数，TDD 用例：```` ```json{...}``` ````、裸 JSON、带前后空白、损坏围栏 ② `parseIntent` 改调用之 ③ 断言不再抛 `JsonSyntaxException` |
| 应调用 Skill | test-driven-development |
| 测试 | 4 组围栏用例 |
| 验证命令 | `./gradlew :app:testDebugUnitTest --tests "com.example.msdksample.aerial.*"` |
| 预期结果 | PASS；全量绿 |
| 完成标准 | 红绿记录 + 提交 |
| 回滚/恢复 | `git revert`（现状：解析必失败→降级离线，回滚无增量风险） |

### Task 04：修复语音分析态竞态（D-09，P1）

| 项 | 内容 |
|---|---|
| 编号/名称 | 04 / 修复 Aerial_photography 回调误清除导致语音永久失效 |
| 目标 | `onFinalResult/onError/onTimeout/stopVoiceInput` 不再误清 LLM 超时器 |
| 前置条件 | Task 01 完成 |
| 涉及文件 | `aerial/Aerial_photography.kt:605,616,623,637` |
| 具体操作 | ① `removeCallbacksAndMessages(null)` 改为按 token 精确移除（`removeCallbacks(llmTimeoutRunnable)` 与语音停止各自的 Runnable 分离）② 为可测性抽出"回调清理策略"最小对象并加 JVM 单测（**建议新建** `app/src/test/java/com/example/msdksample/aerial/CallbackTokenTest.kt`）；若抽取改动过大，允许以对话记录 Ruling 改为最小 diff（仅替换 4 处调用）+ 手工验证脚本 |
| 应调用 Skill | brainstorming(bounded)（先批准抽取 vs 最小 diff）、test-driven-development / systematic-debugging |
| 测试 | 回调对象隔离单测（若采用最小 diff 则以模拟器冒烟替代并记录 Ruling） |
| 验证命令 | `./gradlew :app:testDebugUnitTest`；冒烟：模拟器进入航拍页连续 5 次语音起停后仍能发起分析 |
| 预期结果 | 连续语音会话不再静默失效 |
| 完成标准 | 测试或冒烟证据 + 提交 |
| 回滚/恢复 | `git revert` |

### Task 05：修复相机模式失败仍拍摄（D-06，P1）

| 项 | 内容 |
|---|---|
| 编号/名称 | 05 / 拍照/录像前模式切换失败即中止 |
| 目标 | `setCameraModeToPhoto` 失败不再继续 `startShootPhoto` |
| 前置条件 | Task 01 完成 |
| 涉及文件 | `aerial/Aerial_photography.kt:695-746`（onFailure 分支） |
| 具体操作 | ① 失败分支改为 Toast 错误并 return（录像/拍照两处）② 逻辑提取到 `AerialDroneManager.captureFlowGuarded()` 层面可选（Ruling 决定） |
| 应调用 Skill | brainstorming(bounded)、test-driven-development（对 `AerialDroneManager` 抽出的判定逻辑） |
| 测试 | 抽取部分加 JVM 单测（失败→不调用拍摄 action 的桩验证） |
| 验证命令 | `./gradlew :app:testDebugUnitTest`；模拟器冒烟（Debug 包 enableSimulator 后拍照路径不崩） |
| 预期结果 | 模式切换失败时明确报错且不拍摄 |
| 完成标准 | 测试/冒烟证据 + 提交 |
| 回滚/恢复 | `git revert` |

### Task 06：消除 kami 主线程 I/O（D-10，P1）

| 项 | 内容 |
|---|---|
| 编号/名称 | 06 / 航点写库与 KMZ 生成移出主线程 |
| 目标 | 拖拽航点不再每帧触发全表重写事务 |
| 前置条件 | Task 01 完成 |
| 涉及文件 | `kami/RoutePlanningActivity.kt:367,390,464-468` |
| 具体操作 | ① 拖拽结束（ACTION_UP/CANCEL）才落库，拖拽过程仅更新内存与地图 ② 内存列表成为单一事实源，`saveCurrentRoute` 改协程 `Dispatchers.IO` ③ `prepareMissionFile` 包 `Dispatchers.IO` |
| 应调用 Skill | brainstorming(bounded)、test-driven-development（对"落库时机"决策抽出的纯逻辑：`shouldPersist(dragState)`） |
| 测试 | 落库时机判定单测；既有 kami 相关行为不回归 |
| 验证命令 | `./gradlew :app:testDebugUnitTest`；模拟器冒烟：规划页拖拽 10 个航点流畅、退出重进数据完好 |
| 预期结果 | 无主线程 DB 写；数据一致性保持 |
| 完成标准 | 单测 + 冒烟 + 提交 |
| 回滚/恢复 | `git revert`（回到拖拽即写库现状） |

### Task 07：死代码隔离（D-15，P2）

| 项 | 内容 |
|---|---|
| 编号/名称 | 07 / 隔离死代码（不物理删除） |
| 目标 | 降低误用面：死 Activity 从 Manifest 摘除、死类标注废弃；KamiMainActivity 收回 exported |
| 前置条件 | Task 01 完成；**用户明确批准本清单**（涉及 Manifest 与类文件标注） |
| 涉及文件 | `AndroidManifest.xml:99-102,116-120,137-141`；`aerial/MyApplication.kt`、`kami/KamiApplication.kt`、`asuka/InspectionApplication.kt`、`asuka/DjiConnectionManager.kt`、`asuka/FilterMenuFragment.kt`、`asuka/VipModalFragment.kt`、`aerial/MainActivity.kt`、`kami/KamiMainActivity.kt`（均为标注，不删除）；死布局 6 个移入 `res/layout-deprecated/` 不可行（资源目录无此机制）→ 改为加注释头标记 |
| 具体操作 | ① Manifest 摘除 4 个主壳死 Activity 与 KamiMainActivity 注册（保留 aerial.MainActivity，待用户决定）② 上述类文件头加 `@Deprecated("死代码，Phase 02 复审后删除，见 docs/00 §10")` ③ 逐一 grep 确认无引用后操作 |
| 应调用 Skill | brainstorming(bounded)（清单审批）、verification-before-completion |
| 测试 | 全量构建 + 单测（无行为变化） |
| 验证命令 | `./gradlew :app:assembleDebug :app:testDebugUnitTest`；`aapt dump badging app-debug.apk | grep activity` 复核注册面收缩 |
| 预期结果 | 构建绿、注册面收缩、APK 行为不变 |
| 完成标准 | 构建绿 + Manifest diff 与批准清单一致 |
| 回滚/恢复 | `git revert`（Manifest 恢复注册） |

### Task 08：阶段收尾（审查与合并）

| 项 | 内容 |
|---|---|
| 编号/名称 | 08 / 整分支审查与合并准备 |
| 目标 | 分支通过审查，等待用户合并指令 |
| 前置条件 | Task 02-07 完成 |
| 涉及文件 | 无 |
| 具体操作 | ① `verification-before-completion` 全量复跑 ② 按 `requesting-code-review` + `code-reviewer.md` 模板派审查子代理（BASE=30772d3, HEAD=分支头）③ Critical/Important 修复（各自走 TDD）④ 汇报 Rulings 与 Deferred minors，停止等待合并指令 |
| 应调用 Skill | verification-before-completion、requesting-code-review、receiving-code-review |
| 测试 | 全量构建+测试；审查代理 findings 处理记录 |
| 验证命令 | `./gradlew :app:assembleDebug :app:testDebugUnitTest`；审查报告归档 `docs/phases/reviews/phase-01-review.md`（**建议新建路径**） |
| 预期结果 | 审查通过或问题全修复 |
| 完成标准 | 审查记录 + 绿色构建 |
| 回滚/恢复 | 不合并即无影响 |

## D. 阶段完成标准

1. D-02、D-06、D-07（代码部分）、D-09、D-10 在审计报告矩阵中状态改为"已修复"并附提交号；
2. 死代码隔离完成且 Manifest 注册面与批准清单一致；
3. `assembleDebug` 与 `testDebugUnitTest` 全绿，新增单测 ≥3 个文件且均有红绿记录；
4. 代码审查（子代理）Critical/Important 清零；
5. 模拟器冒烟通过（Task 04-06 的冒烟项）；
6. 台账与 Rulings 记录完整，等待用户合并/继续指令。

## E. 阻塞与升级处理

| 阻塞情形 | 处理 |
|---|---|
| 模拟器/AVD 不可用 | Task 04-06 冒烟降级为 JVM 测试 + 代码走查，Ruling 记录"运行验证延至 Phase 02/04"；不阻塞其余任务 |
| 用户对 Task 07 清单有异议 | 按批准的子集执行，未批准项移交 Phase 02 |
| 修复中发现缺陷根因涉及 DJI 时序 | 停止该任务，升级为 Ruling + 用户报告，转 Phase 03/04 立项，不在本阶段扩大改动面 |
| 构建环境损坏（SDK/缓存） | `systematic-debugging` 根因排查；仅允许恢复性操作（重新生成 local.properties 等），不得升级依赖 |

## F. 下一阶段交接

- 代码：`feature/phase-01` 分支（合并后为 P02 基线），提交历史含每任务独立 commit；
- 文档：审计报告问题矩阵已更新（D 编号状态）；本阶段 review 记录；
- 测试：新增单测进入常驻回归（P02 门禁继承）；
- 技术决策：Task 04/05 的 Ruling（抽取 vs 最小 diff）结论及理由，供 P02 拆分巨型类时参考；
- 未决事项：死代码物理删除清单、aerial.MainActivity 去留——移交 Phase 02 首任务。
