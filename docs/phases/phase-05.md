# Phase 05 执行计划：测试、验收与交付

## A. 阶段基本信息

| 项 | 内容 |
|---|---|
| 阶段名称 | 测试、验收与交付（Phase 05） |
| 目标 | 全量回归与验收标准核销；信息安全风险 S-01~S-07 处置；release 签名/图标/版本正式化；交付文档包（含限制说明） |
| 对应总体计划章节 | `docs/02-总体开发计划.md` §5 Phase 05 行、§6 里程碑 M5、§10 测试策略、§11 验收标准 |
| 依赖的前置阶段 | Phase 04（真机核销完成或明确记录未核销项及原因） |
| 当前输入条件 | P04 交接清单；验收标准清单（总体计划 §11）；安全风险清单（审计报告 §9） |
| 阶段边界 | 不再新增功能；仅修缺陷、补配置、写文档；未核销项如实进入交付说明的"已知限制" |

## B. Skill 调用声明

| Skill | 调用时机 | 目的 | 输入 | 预期输出 | 验证方法 |
|---|---|---|---|---|---|
| using-superpowers | 会话开始 | 纪律入口 | — | 声明 | 对话记录 |
| verification-before-completion | **本阶段主 Skill**：验收标准逐条"命令+输出"核销 | 证据先于结论 | 验收清单 | 核销表 | 每条附输出 |
| requesting-code-review | release 前整分支终审（最完整一轮） | 独立审查 | 全程 SHA 范围 | findings | Critical/Important 清零 |
| receiving-code-review | 收到反馈时 | 技术严谨处理 | findings | 处理记录 | 闭环 |
| systematic-debugging | 回归失败时 | 根因 | 失败输出 | 根因报告 | 证据链 |
| test-driven-development | 修复时 | 铁律 | — | 红绿记录 | 输出 |
| finishing-a-development-branch | 全部核销后 | 分支收尾决策（合并/打 tag）——**合并与打 tag 需用户明确指令** | 核销结果 | 收尾方案 | 用户确认 |
| executing-plans | 任务执行 | 内联纪律 | 本计划 | 台账 | task-done |

## C. 原子任务拆分

### Task 01：回归基线固化

| 项 | 内容 |
|---|---|
| 编号/名称 | 01 / 全量回归套件执行 |
| 目标/前置条件 | 确认 P01-P04 全部常驻测试绿；`feature/release-prep` 分支 |
| 涉及文件 | 无（执行） |
| 具体操作 | `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`；模拟器冒烟脚本（首页/规划/任务/飞控四页路径） |
| 应调用 Skill | verification-before-completion |
| 测试/验证命令 | 上述命令退出码 0；lint 无 Error 级（Warning 清单归档） |
| 完成标准/回滚 | 全绿记录；异常项回流 systematic-debugging |

### Task 02：验收标准逐条核销

| 项 | 内容 |
|---|---|
| 编号/名称 | 02 / 总体验收核销表 |
| 目标 | 总体计划 §11 六条逐条核销，产出 `docs/phases/checklists/acceptance-report.md`（**建议新建路径**） |
| 前置条件 | Task 01 |
| 涉及文件 | 核销报告（新建） |
| 具体操作 | 每条：验证方法 → 执行 → 输出 → 结论（通过/受限通过/未通过+原因）；未通过项按严重度决定"修复"或"进入已知限制" |
| 应调用 Skill | verification-before-completion、systematic-debugging |
| 测试/验证命令 | 核销表内逐条命令 |
| 完成标准/回滚 | 核销表 100% 有结论；无未声明缺口 |

### Task 03：信息安全风险处置（S-01~S-07）

| 项 | 内容 |
|---|---|
| 编号/名称 | 03 / 安全清单核销 |
| 目标 | 逐项处置：S-01 DJI Key（保留但确认政策 + 文档说明）/ S-02 高德 Key（按 P03 决策清理）/ S-03 cleartext 收紧为域名白名单 / S-04 Vosk 模型随包分发（assets）或哈希校验 + zip-slip 防护 / S-05 Key 配置化（P04 已做，复核）/ S-06 假身份信息加"演示"标识 / S-07 release 签名+混淆 |
| 前置条件 | Task 01 |
| 涉及文件 | `res/xml/network_security_config.xml`、`aerial/VoskHelper.kt`（zip-slip 防护与校验）、`res/assets/`（模型分发，**体积决策点：42MB 进包需用户批准**）、`AccountFragment.kt`、`app/build.gradle.kts`、`proguard-rules.pro` |
| 具体操作 | 每项 TDD（可测部分：zip-slip 防护、白名单匹配）+ 冒烟 |
| 应调用 Skill | subagent-driven-development、test-driven-development、security-audit（辅助复查） |
| 测试 | zip-slip 边界单测、白名单单测 |
| 验证命令 | 全量绿 + lint；release 包安装冒烟 |
| 完成标准 | S 清单逐项结论记录 |

### Task 04：release 正式化

| 项 | 内容 |
|---|---|
| 编号/名称 | 04 / 签名、图标、版本 |
| 目标 | release 变体可出正式签名包；App 图标替换占位 `btn_star`；versionCode/Name 策略确定 |
| 前置条件 | Task 03；**外部条件**：签名密钥（.jks）由用户提供——未提供则生成"待签名"交付说明并挂起签名项 |
| 涉及文件 | `app/build.gradle.kts`（signingConfigs，密码走 local.properties 不入库）、`res/mipmap*/`（图标资产由用户提供或临时生成占位说明）、版本号 |
| 具体操作 | signing 配置 + `assembleRelease` + 真机安装冒烟 + 图标替换 |
| 应调用 Skill | subagent-driven-development、verification-before-completion |
| 测试/验证命令 | `./gradlew :app:assembleRelease` 退出码 0；`apksigner verify` 通过；安装启动正常 |
| 完成标准 | 签名包产出（或待签名记录） |

### Task 05：交付文档包

| 项 | 内容 |
|---|---|
| 编号/名称 | 05 / 交付文档 |
| 目标 | 更新 `docs/00~05` 至交付态；新增《交付说明》：已知限制（含未核销真机项、AI 为规则文案等）、部署要求（App Key/后端/Key 配置）、backlog（真实 AI、支付、离线地图、包名迁移） |
| 前置条件 | Task 02-04 |
| 涉及文件 | `docs/`、新建 `docs/交付说明.md`（**建议新建路径**） |
| 具体操作 | 文档一致性同步（本次交叉审查同流程）；README.md 补建（构建/测试/目录说明） |
| 应调用 Skill | verification-before-completion（文档事实核对） |
| 验证命令 | 交叉审查记录（同本次接手审查方法） |
| 完成标准 | 文档与代码零矛盾 |

### Task 06：终审与收尾

| 项 | 内容 |
|---|---|
| 编号/名称 | 06 / 最终整分支审查 + 分支收尾 |
| 目标 | 终审通过；`finishing-a-development-branch` 给出收尾方案（合并 master、打 tag `v0.2.0-phase05`），**执行合并/打 tag 前停止等待用户指令** |
| 前置条件 | Task 05 |
| 具体操作 | requesting-code-review（BASE=P04 合并点）→ 修复 → 核销 → 收尾方案呈报 |
| 应调用 Skill | requesting-code-review、receiving-code-review、finishing-a-development-branch |
| 完成标准 | 终审记录 + 收尾方案；本阶段到此停止 |

## D. 阶段完成标准

1. 总体验收核销表 100% 有结论，无未声明缺口；
2. S-01~S-07 全部处置或明确记录接受理由；
3. release 签名包产出（或"待签名"状态明确记录）；
4. 交付文档包齐备且与代码一致；
5. 终审 Critical/Important 清零；
6. 收尾方案已呈报，等待用户合并指令（本阶段不自动合并）。

## E. 阻塞与升级处理

| 阻塞情形 | 处理 |
|---|---|
| 签名密钥未提供 | Task 04 签名项挂起，产出"待签名"交付说明；不影响其他任务 |
| Vosk 模型进包体积未获批 | 降级为"下载 + 哈希校验"方案，Ruling 记录 |
| 真机未核销项（来自 P04 阻塞） | 如实写入已知限制；验收结论为"受限通过" |
| 终审发现 P0 级回归 | 回流对应 Phase 修复（升级用户），交付顺延 |

## F. 下一阶段交接（项目层）

- 代码：master 合并态 + tag；发布物（签名 APK / 待签名说明）；
- 文档：终版 docs/00~05 + 交付说明 + 全部核销表与审查记录；
- 测试：常驻回归套件 + 冒烟脚本 + 真机记录表（存档）；
- Backlog（下轮立项候选）：真实 AI 识别服务、会员支付、离线地图包、包名与应用市场迁移、多机型适配、iOS/Web 端。
