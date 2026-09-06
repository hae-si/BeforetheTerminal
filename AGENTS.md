# AGENTS.md — 给 AI 协作者的工程备忘

本文件是任何在此仓库工作的 AI 协作者（你）的入口操作手册。先读它，再读 `docs/`。

## 1. 项目身份

- 目标：在原版 `agmas/NoellesRoles` 基础上**替换式演进**为策划案《终点站抵达之前》(Before the Terminal, 代号 BTT)。
- 基线：MC 1.21.1 / Fabric / Java 21 / 官方 `wathe-1.3.2-1.21.1.jar` / `harpymodloader-1.2.4-h1.3.jar` / 当前原版 NoellesRoles 演进（mod_version 自 BTT alpha 起为 `0.1.0-alpha-h1.3`，§0a C-013）。
- git：`origin https://github.com/hae-si/terminal`（用户仓库；演进自 `agmass/NoellesRoles`），`main` 分支。
- 谱系考古见 `docs/ARCHAEOLOGY.md`；策划案见 `docs/终点站抵达之前.docx`。

## 2. 构建与运行

```powershell
.\gradlew.bat build          # 编译+打 jar（产物 build/libs/noellesroles-0.1.0-alpha-h1.3.jar）
.\gradlew.bat runServer      # 起专用服（需 run/eula.txt=eula=true；loom 会建 run/）
.\gradlew.bat runClient      # 起客户端
.\gradlew.bat compileJava    # 仅编译 main，快速反馈
```

- 代理 `127.0.0.1:7897`（gradle 已全局走代理，见考古 §1）。
- 无测试框架、无 lint/typecheck 任务； correctness 靠 `compileJava` + `build` + runServer 实跑。
- Java 21 JDK：`C:\Program Files\Microsoft\jdk-21.0.7.6-hotspot\`（`javap` 在其 `bin\`）。

## 3. wathe/HML API 探查（源码已就位，仍以运行时为准）

- **wathe 官方源码**：`references/Wathe`（main @ `02470be` = tag `1.3.2-1.21.1`，与 `libs/wathe-1.3.2-1.21.1.jar` 同一 commit）。直接读源码验证行为，比 javap 反推可靠。
- ⚠️ wathe LICENSE = **All Rights Reserved**：源码**仅限本地阅读参考**，禁止复制其代码/资产进本 mod 或再分发。
- **不得**照 `references/NoellesRolesSpark`（依赖 wathe fork，API 不可用）或 `references/StarRailExpress`（替换式 DLC）猜测官方 wathe 1.3.2 行为；`references/**` 全部只读。
- 运行时验证：行为结论仍以 `libs/` 实际 jar 实跑为准（临时探针挂 `Noellesroles.onInitialize` + `SERVER_STARTED`，dump 后自停，用完即删）。
- HML 1.2.4 仍仅 jar（javap 探查）。

## 4. 文档地图（`docs/`，gitignored，本地私有）

**核心规范（按职责）**

| 文件 | 职责 |
|---|---|
| `ARCHAEOLOGY.md` | 项目谱系、依赖、版本、历史考古 |
| `ARCHITECTURE.md` | BTT 当前实际软件架构（类/时序/同步/门控） |
| `GAME_DESIGN.md` | 游戏规则（策划案技术化整理） |
| `ROLE_DESIGN.md` | 身份正式规格 |
| `ROLE_INTERACTIONS.md` | 身份/系统交互矩阵 |
| `SYSTEM_SPEC.md` | 技术实现规范 + §21 API 最终结论 + §22 借鉴合并结论 + §0a 改动登记 |


**计划与进度**

| 文件 | 职责 |
|---|---|
| `ROADMAP.md` | 阶段、优先级、延期事项（TODO 主清单） |


**其他**

| 文件 | 职责 |
|---|---|
| `终点站抵达之前.docx` | 策划案原件（不改） |

> SPEC_TRACEABILITY（并入 IMPLEMENTATION_STATUS §五）与 IMPLEMENTATION_PLAN（并入 ROADMAP §6.1）已于 2026-09-05 删除。
> 历史研究记录 `api_notes.md`（WATHE-API-001 证据）与 `OPTIMIZATION_REVIEW.md`（对照评审）已于 2026-09-04 删除，结论分别并入 SYSTEM_SPEC §21/§22 与 ROADMAP/IMPLEMENTATION_PLAN。

## 5. 核心铁律

1. **最小改动**：优先复用 > 修改 > 扩展 > 新建。凡改原版基线（`build.gradle`/`gradle.properties`/`src/**`/资源/`fabric.mod.json`/mixin 配置），**先在 `SYSTEM_SPEC.md §0a` 登记一行**并挂任务 ID。`references/**` 只读，禁改。
2. **垂直切片**：每阶段交付必须“能完整开并结束一局”；不一次铺开所有角色/系统。
3. **不偷简化**：策划特性缺席必须登记 `ROADMAP.md §7` 的 `BT-*` TODO，不得实现一个与策划不同的“看起来一样”的版本。
4. **API 先验证**：用 javap/runServer 确认 wathe/HML 真实行为再写代码；不按 spark/SRE 猜。
5. **不擅自提交**：仅在被明确要求时 commit/push；提交前看 `git status`/`git diff`。
6. **Phase 1 范围**：固定 6 人；身份集 = 教父/义警/医生/处子/列车长/小丑（见 ROADMAP §5）。不扩大。

## 6. 约定速记

- 命名空间 `noellesroles`（角色 id / CCA / 资源）；BTT 新代码建议入 `org.agmas.noellesroles.btt.*` 包。
- 角色 = `dev.doctor4t.wathe.api.Role(Identifier,color,isInnocent,canUseKiller,MoodType,maxSprintTime,canSeeTime)`，`WatheRoles.registerRole(...)` 在 `Noellesroles.java` 静态块注册（须早于 HML SERVER_STARTED 的 refreshRoles）。
- 身份分配后派发 `ModdedRoleAssigned.EVENT` 以复用 NR“发初始道具”链路；清理挂 `ResetPlayerEvent.EVENT` 与 `GameFunctions.resetPlayer` TAIL。
- 击杀一律走 `GameFunctions.killPlayer(victim,spawnBody,killer,deathReason)`；可被 `AllowPlayerDeath.EVENT` 否决。
- mixin 配置：`src/main/resources/noellesroles.mixins.json`（公共）、`src/client/resources/noellesroles.client.mixins.json`（客户端）；新增 mixin 须加入对应 json 的列表。
- client-only mixin（RoundTextRenderer/MoodRenderer 等客户端类）在专用服冒烟中永不加载，apply 错误只能由客户端启动暴露——新 client mixin 必须 static/实例与目标匹配并建议 runClient 自检。
- **测试环境（2026-09-05 定案）**：用户客户端实例=`D:\Minecraft\.minecraft\versions\1.21.1-Fabric 0.19.5\`（logs/saves 在实例目录内，**不查项目 run/**）。**gugle-carpet-addition（假人驻留）与 BTT 交互冲突——枪/刀对玩家右键完全失效，正是"枪刀四轮盲修无效"的根因；移除即恢复（BTT 代码无恙，commit 88b9181）。凡测交互，禁装该 mod；假人驻留需求另寻方案（chunk 保持加载即可驻留）。
- CCA 组件注册于 `NoellesRolesComponents`（`EntityComponentInitializer`/`WorldComponentInitializer`）+ `fabric.mod.json` 的 `custom.cardinal-components`。
- 网络：C2S payload 注册 `PayloadTypeRegistry.playC2S()` + `ServerPlayNetworking.registerGlobalReceiver`；S2C 用 `ServerPlayNetworking.send` 或 CCA `AutoSyncedComponent`。

## 7. 当前进度

- **Phase 0 已验收**：`build` 与 `runServer` 均通过。
- **WATHE-API-001 已验收**：六项 PASS（结论见 SYSTEM_SPEC §21）；基线改动 C-001（ratatouille 1.4.3）。
- **DEMO-001~012（除 DEMO-011）已实现并探针验证（2026-09-03）**：BTT GameMode+席位分配（教父/义警/小丑/医生/处子/列车长）+身份宣告+击杀口径+处子发光+医生验尸+教父查验+小丑疯魔+结局判定宣告+列车长钥匙+隔离收尾；生命周期/单元探针全 PASS（探针已删）。键名决策 D7=自然键名渐进替换（doc-小丑接管 `noellesroles:jester`，NR 旧行为 BTT 门控）。
- **仅剩 DEMO-011**：≥6 玩家多人完整一局实测（RM §5.3 DoD #13 + 全项回归）；代码侧 Phase 1 已就绪。
- **联调进展（2026-09-04）**：用户假人实测“运行正常”；两个 wathe 基线坑已修并登记 §0a C-011（全新客户端配置 NPE 垫片）与 C-012（结局文本直接替换 wathe 结束覆盖层，聊天广播移除，`btt_game.lastEnding` 同步）。`release/` 为完整部署包（含依赖）。
- 策划案已去除星号设定。下一步默认=多人实测收尾；除非被明确要求，不要自行开始 Phase 2。
- 全部基线改动登记于 SYSTEM_SPEC §0a（C-001~C-016）。
- **身份目录轮（2026-09-04，用户指令）**：70 身份全量注册（**62 新键 + 8 接管**——morphling=演员/mimic=卧底 后加入接管【审计校正 DC-05】），仅元数据零能力；HML disabled 隔离；登记 §0a C-018、RM D11。
- **处决/数值对齐（2026-09-04，策划修订）**：处决规则实装（C-017：理智−40/掉枪/误杀全员+100）；+1min 触发改“乘客死亡”（wathe 原生）；倒计时初始 8 分钟；狂气删队友+25；体力=原版直接用。**【2026-09-05 审计+用户裁定，DC-01 关闭】**"枪/弓冷却 30s"未实装且被裁定**推翻**——维持 wathe 原生 10s；义警击杀后 60s=处决特例（BttGunDropMixin）。
- **理智/需求/旗帜（2026-09-04，用户指令）**：mood 隔离回退（C-016）——理智/需求/体力回归 wathe 原生（数值不改）；mood HUD 旗帜映射：乘客/凶手=原版旗、中立=mood_ghost、外人=mood_jester（暂定）；“任务”二段式与 doc 阈值仍 TODO（BT-ECO-SAN）。
- **结局/键名二轮修订（2026-09-04，用户指令）**：结局文本改为 `BttEndTextMixin` 直接改写 wathe `getEndText`（SRE 式改内容不重画；自绘覆盖层 C-012 回退）；策划修订乘客胜利=两结局（执法落幕删除）；开局宣告=wathe 原版迎新屏（聊天行删除，中立/外人不展示）；键名接管第二轮：医生=coroner、处子=noisemaker（显示名 lang 更新）。登记 §0a C-014/C-015。
- **版本管理（2026-09-04）**：`971804a` = BTT Phase 1 提交；`mod_version` 切换为 `0.1.0-alpha-h1.3`（C-013）；tag `v0.1.0-alpha`（重指至版本切换提交）已连同 main 推送 origin。
- **P2G-001（2026-09-05，用户指令"做三星及以下"）**：19 身份有真实行为（P2-α 6 完整+4 部分；P2-β 演员/卧底/巫觋，酒保搁置——NR bartender 与 doc 灌酒不可复用；P2-γ 连环杀手/恶魔/侦探/卖糖人/绳艺师/失忆患者/窃贼+守夜人枪）；新通用机制=祭品池（scoreboard 深绿+辉光）/杀人历史/THIEF_WIN 独胜结局；新 mixin `BttSerialKillerKnifeMixin`（§0a C-019）；顺带修 NR 基线 `psychosis_items.json` JSON 损坏（§0a C-020）。
- **全面重审（2026-09-05，用户指令）**：代码为事实源对齐全套文档；修复司机×2 误挂 CONDUCTOR→DRIVER（DC-07，Phase 1 测试期列车长在场倒计时双倍速）；决策日志 RM §0.3（DC-01~07）；轮子清理清单 RM §8.7/IP CLEANUP 章；事实源层级 RM §0.0（代码>设计>规范>架构>路线>计划；NRS=REFERENCE ONLY）。- **BT-ARCH-001 已落地（2026-09-05）**：`BttRoleDef`（kit/onKill/onTick/use）+`BttRoleDefs` 声明表（20 defs）=身份行为唯一事实源；BttEvents/KillHookMixin/BttGameWorldComponent 全部改为派发/纯数据；build+runServer 冒烟 PASS（`62 identities, 20 defs`）。**新增身份只加 def 条目，禁止 if-chain**。
- **四项核查轮（2026-09-05，用户指令）**：狂气/理智/商店/假需求全数核实为 wathe 原生（乘客产币封 C-021；误杀 setMood(0) 自取消链路 VERIFIED）；结局换 NRS 式 per-role 单列职业网格（C-022：BttRoundEndRoleMixin+BttEndColumnsMixin，BttRoundEndMixin 删除）；wathe lang 确认可按键覆盖并补 zh_cn 12 键（assets/wathe/lang/zh_cn.json）；原生 backfire 未门控=开放决策 DC-08。
- **席位解锁+汽笛（2026-09-05，用户指令）**：assignSeats 改 doc 公式 6–18 人（IMPLEMENTED 25 身份优先/BARTENDER 排除/元数据降层补位）；人数门 6–18（btt.start_error.player_range）；宣告计数按席位 flag；汽笛→BTT（C-023 BttHornStartMixin，原硬编码 MURDER；AutoStart 不动）。D13 已登记 ROADMAP §0.1。**新增身份记得同步 BttRoles.IMPLEMENTED**。
- **BT-P2-UI 选人 UI（2026-09-05）**：预言家/刺客/小说家/魔术师/舞蛇人实装——BttGuessC2SPacket+BttGuessReceiver+BttPlayerWidget/BttRoleWidget+BttGuessScreenMixin/BttScreenDoNotClose（NR Guesser 同构克隆）；冷却=AbilityPlayerComponent；魔术师 instant（100 狂气/次）；小说家 NOVELIST_WIN 独胜；IMPLEMENTED=30。§0a C-024。**下一批=P2-α 补全（P2A-002）→ DEMO-011**。
- **P2A-002 P2-α 补全（2026-09-05）**：猎人=UI instant 一次性狙击（命中主犯杀/落空无效，"揭示"待作者确认）；清道夫=无声刀（BttCleanerSilentKnifeMixin，"删蓄力"原版本就无）；明星=死亡通知仅乘客（kill hook）；女仆=双倍取餐（BttMaidPlatterMixin，同款<2 可取）+赠予；邮差=双向赠礼。IMPLEMENTED=32。§0a C-025。**下一批=DEMO-011 多人实测**（P2-δ 需 BT-SYS-DRUNK/Spark 移植，随后）。
- **结局渲染二轮（2026-09-05）**：用户报显示问题并提供 fork 仓库 `github.com/XruiDD/TrainMurderMystery`（已克隆 references/TrainMurderMystery=NRS 依赖的 wathe fork 源码，只读）；按其 RoundTextRenderer 对齐：NONE 独胜可显示+胜负两组卡片网格+职业名 y+18 原尺寸；BttEndColumnsMixin 重写为 HEAD 全自绘（BttEndTextMixin 并入删除）；btt_game.winners 同步字段承载独胜赢家。§0a C-026。**NRS 结局协议从此以 fork 源码为准（不再反推）**。
- **测试工具轮（2026-09-05）**：修复 BttEndColumnsMixin 非 static 回调（client 专属目标，专用服冒烟盲区，用户 20:58 报告定位）；'
- **七项修复轮（2026-09-05，用户测试反馈）**：①无人生还结局 ✅（decide 全灭优先）；②③结局音效+胜负=服务端 winners csv 下发（didWin 同口径）；④BTT 局内玩家消息全部 Action Bar（聊天框不可见）；⑤技能去道具化——侦探/绳艺师/卖糖人=G 键开背包选人（BttAbilityKey 自建 G 键，NR wasPressed 竞态不可共用），女仆/邮差/失忆/窃贼保留实体交互；⑥商店隐藏+拒绝匕首/左轮（BttShopGate 反射索引 [0,1]）；⑦/forceRole 身份=子命令带 Tab。§0a C-027。
- **逐测修复轮一（2026-09-05）**：处子商店/卧底假刀=NR 行为泄漏→四个 NR 商店 mixin+MIMIC 发装备 BTT 门控（C-028）；邮差回退搁置四星（IMPLEMENTED=31）；教父查验角色名改 lang 直译（HML getRoleName 客户端空）；猎人狙击后 UI 变灰+初始 CD=0。
- **逐测修复轮二（2026-09-05，14 项）**：UseEntityCallback 全量回退（枪刀 bisect；失忆/窃贼尸体交互/女仆赠予待重做）；无人生还=按 doc（外人计数，主犯灭≠审判）；Action Bar 补扫多行 sendMessage（嵌套括号漏网）；刺客暴露只告知被猜者；魔术师免钱 CD2min；女仆取餐总量 2；forceRole <players> <role>；预开局隐藏头顶名；商店删便签；恶魔推迟；lang 阵营杀手→凶手（wathe 覆盖 8 键，身份名不动）。§0a C-029。
- **读档崩溃修复（2026-09-05）**：per-role 宣告 index 禁入世界存档（HML 注册晚于读档→越界）——结局职业迁 btt_game.endRoles + NBT clamp 防御（C-030）；GodfatherHud 拆 HEAD/TAIL 双 handler（TAIL cancel 无效曾崩）。**教训：写进 wathe CCA 存档的任何 index/枚举序号必须恒定有效，跨组件注册时机不可依赖。**
- **枪刀根因定案+checkpoint（2026-09-06）**：D0 旧版基线对照→根因=gugle-carpet-addition（C-032，环境冲突非代码）；checkpoint commit `88b9181`（P2G-001~逐测修复轮三全量 59 文件）。误摘四项（UseEntity 派发/女仆/清道夫/连环杀手）待回接验证；假人驻留需替代方案（或带冲突知情报上游）。
- **冻结后最终审计+文档清洗（2026-09-06）**：注册口径=**61 新键 + 9 接管 = 70 身份**、distinct defs 24、IMPLEMENTED=31（23 实装+8 原生接管）；BttRoleDef 死代码 use 钩子删除；清道夫静音/连环杀手 CD json 恢复注册（待实测）；女仆双倍取餐文件已删待重建（延后）；文档数字全面对齐（ROADMAP/ARCHITECTURE/STATUS/SYSTEM_SPEC）。**下一动作=你实测；无问题后提交新版本。**
- **二轮实测修复（2026-09-06，用户 9 项反馈）**：①结局 X 下移居中；②无人生还更名**鸣泣之时**（decide 重构：主犯/从犯分开计数——主犯死+从犯活=游戏继续，杀光乘客与外人=鸣泣之时；宣言不变）；③明星播报去名；④**59 身份注册色对齐策划案字体色**（docx 提取+两两配对继承；10 接管/NR 键不动；吟游诗人=NR awesome_binglus 待确认）；⑤处子/卧底商店=客户端 4 个 NR ShopMixin 未门控+wathe 商店 canUseKillerFeatures 未按 BTT 阵营过滤 → 双门控（仅 PRINCIPAL/ACCOMPLICE 可见）；⑥小丑本能绿=BttInstinctColorMixin（BTT 中立目标一律绿）；⑦70 身份 goals 中英全量+15 中立/外人 win 宣言+phantom 显示名→偷渡客+小说家 win 文案；⑧侦探=任何人（文档）；⑨开局强制清背包防御。
- **三轮实测修复（2026-09-06，用户 3+7 项反馈）**：①结局 X 贴头像底部；②无人生还→**鸣泣之时**（decide 主犯/从犯分计：主犯死+从犯活=继续，杀光乘客与外人=鸣泣之时）；③明星播报去名；④**59 新键色对齐策划案**（docx N【名字】段锚定提取+两两继承；接管键/NR 不动；水手→**吟游诗人 minstrel** 新键替换）；⑤处子/卧底商店=wathe canUseKillerFeatures BTT 下仅主犯/从犯可见；⑥小丑本能绿=BttInstinctColorMixin（BTT 中立一律绿）；⑦**70 goals 中英全量+docx 斜体宣言提取**（win=docx 为准、尾声预存 announcement.epilogue.*、黑死病/异端分子特殊结局宣言存 btt.special.*、舞蛇人/异教领袖/酒鬼/疯子无 win 键删除、phantom→偷渡客）；⑧侦探=任何人（文档）；⑨开局双清背包。§0a C-033（吟游诗人替换水手）。**小说家 win=docx 原句（与用户给定一致）。**
- **个人开发轮（2026-09-06，用户指令"自己试做简单身份"）**：①异端分子=胜负翻转（decide 后 flip：PASSENGERS/TIME↔KILLERS；即使已死亡；**REVIEW**：异端本人胜负按乘客阵营字面口径=翻转到乘客侧胜时获胜——GAME_DESIGN 未决点#5 待作者确认）；②女仔回接（C-032 定案后恢复）：双倍取餐 mixin 重建（总量 2 上限）+赠予 UseEntityCallback（C-032 后可安全使用）；③从犯杀手 kit=刀。IMPLEMENTED=33。**均待用户实测。**
- **forceRole 兼容（2026-09-06，用户指令"重写 NR 加的 forceRole"）**：BttWatheForceRoleMixin 双写 /wathe:forceRole killer|vigilante——原记分板写入照跑（旧局兼容）+BTT 强制表登记（killer→杀手/从犯、vigilante→义警，下一局消费）。/forceRole <player> <role>（HML 原生命令，双写 BTT） 仍为全 70 身份入口。§0a C-035。
- **失忆患者 G 键修复（2026-09-06，用户自改红叉+要求 G 键回接）**：根因=NR targetBody 射线挂在 wathe RoleNameRenderer.renderHud 的**黑暗早退之后**（方块光<3 且天光<10 → return，车厢内普遍昏暗 → targetBody 永远 null）+活人距离仅 2 格。修复=失忆 G 键自带尸体射线（ProjectileUtil 4 格、无视黑暗），BttCorpseActionC2SPacket 恢复（action=1），UseEntity 右键版删除。用户自改的结局红叉（y+8f 不除 2）已保留。§0a C-040。
- **彻底去 btt 化（2026-09-06，用户指令）**：①lang 键 `btt.ending/inspect/start_error/special.*` → **`noellesroles.*`**（16 键×2 文件）；②键位 `key.btt.ability`→**`key.noellesroles.select`**（zh=选择目标）、`category.btt.keybinds`→`category.noellesroles`；③packet id `btt_guess`→**`select`**、`btt_corpse_action`→**`corpse_action`**（避 NR guess 冲突）；④CCA `noellesroles:btt_game`→**`noellesroles:game_state`**（fabric.mod.json 同步；旧世界 btt_game 数据弃置，下一局重建）；⑤队伍 `btt_sacrifice`→`sacrifice`。**保留：Java 包 `...btt.*`/类名 `Btt*`/mixin json 条目（纯内部命名，零用户可见，改名=发布前无收益搅动）。**§0a C-042。**注意：zh_cn.json 含用户加的 // 注释（Gson lenient 可载），脚本处理需先剥注释。**
- **键名/docx 同步轮（2026-09-06，用户 4 项指令）**：①lang 清理：btt.ending.quote.* 12 键删（结局引语改 wathe winText/loseText 口径=docx 胜利宣言）；announcement.title/welcome.noellesroles.* 零消费方全删；btt.inspect.hud 补回 %s（教父真根因）；②**卖糖人→药剂师 pharmacist**（docx 改名，能力=<喂药>解毒/回满理智，键名+常量+lang 同步）；③**下划线键名**：serialkiller→**serial_killer**、railwaypolice→**railway_police**（常量 SERIAL_KILLER/RAILWAY_POLICE）；④**刺客接管 guesser**：Noellesroles.java 恢复 GUESSER_ROLE（assassin 键删；GUESSER modifier 赋予=NR 识破 UI/packet 原生；HML 池禁入；错猜无惩罚=NR 默认，doc 暴露口径 REVIEW）；⑤**goals 70 键 docx 全量再生**（你是X。+docx 能力原文；docx 大改文本同步：杀手[剑]飞剑 GAP、酒保灌酒/药剂师喂药=BT-SYS-DRUNK、纵火犯雷达、小丑护盾2+误杀、失忆仅限一次+阵营、莽夫入阵营）；⑥stowaway 残键清理。§0a C-043。
- **策划案大改登记（2026-09-06，C-037，文档轮）**：外人并入中立（三分类：独行/外人中立/狂人中立——异端/失忆/莽夫/舞蛇人/异教领袖/酒鬼/疯子归狂人中立=乘客阵营）；席位公式=执法=凶手=N//6、中立=N//6、同色互斥；外人中立无体力；结局含外人条款；身价新文本（失忆仅限一次/莽夫入阵营/纵火犯雷达/小丑护盾2+误杀/酒鬼疯子永久醉酒）。GAME_DESIGN 顶部已插修订权威块；代码 GAP 登记未动（席位/互斥/体力/结局外人条件）。**测试客户端实例=D:\Minecraft\.minecraft\versions\1.21.1-Fabric 0.19.5\（logs 在实例内）。**
- **策划口径澄清（2026-09-06，用户三裁定）**：①黑死病/异端分子=**狂人中立**（docx 分节读错更正——外人中立仅魔女/救世主/饕餮/花匠）；②**审判落幕**=到站前杀光凶手和外人中立（docx 写漏用户补；lang trial=审判落幕；decide 签名改 aliveOutsiderNeutrals 仅数 OUTSIDER 阵营——独行/狂人不阻塞结局）；③独行中立被杀加钱（BttKillHook +100 狂气同乘客口径）、活着不影响凶手胜利。LONE_NEUTRALS={小说家,小丑,窃贼,纵火犯}。§0a C-038。
- **逐测修复轮三（2026-09-05）**：商店改开局移除/终局恢复 SHOP_ENTRIES（旧"隐藏+拒索引"破坏商店，C-031）；女仆/清道夫/连环杀手 mixin 摘除（枪刀 bisect 继续）；小说家文案固定+猜错不播报；失忆/窃贼=G 键+注视尸体（复用秃鹫交互基建 BttCorpseActionC2SPacket）；偷渡客=接管 phantom（stowaway 键删）；lang 阵营杀手→凶手；文档收敛（IP/TRACE 删除并入 ROADMAP/STATUS）。§0a C-031。**枪刀右键仍未定案——复测后继续 bisect（GunDrop→Horn）。**
    '自建 /forceRole <path> <players>（wathe forceRole 只写原版记分板选人、BTT 从不消费——BttIdentity.FORCED 优先占用公式配额开局消费）+ clear；'
    '新增 docs/IMPLEMENTATION_STATUS.md（70 身份三档状态+逐个测试要点，用户逐个测试用）。§0a C-025 附注+IP P2A-002 轮。
- **BT-ARCH-001 已落地（2026-09-05）**：`BttRoleDef`（kit/onKill/onTick/use）+`BttRoleDefs` 声明表（20 defs）=身份行为唯一事实源；BttEvents/KillHookMixin/BttGameWorldComponent 全部改为派发/纯数据；build+runServer 冒烟 PASS（`62 identities, 20 defs`）。**新增身份只加 def 条目，禁止 if-chain**。
**下一批=选人 UI 五身份（BT-P2-UI）→ P2-α 补全 → DEMO-011**。
- **P2G-001（2026-09-05，用户指令"做三星及以下"）**：19 身份有真实行为（P2-α 6 完整+4 部分；P2-β 演员/卧底/巫觋，酒保搁置——NR bartender 与 doc 灌酒不可复用；P2-γ 连环杀手/恶魔/侦探/卖糖人/绳艺师/失忆患者/窃贼+守夜人枪）；新通用机制=祭品池（scoreboard 深绿+辉光）/杀人历史/THIEF_WIN 独胜结局；新 mixin `BttSerialKillerKnifeMixin`（§0a C-019）；顺带修 NR 基线 `psychosis_items.json` JSON 损坏（§0a C-020）。
- **全面重审（2026-09-05，用户指令）**：代码为事实源对齐全套文档；修复司机×2 误挂 CONDUCTOR→DRIVER（DC-07，Phase 1 测试期列车长在场倒计时双倍速）；决策日志 RM §0.3（DC-01~07）；轮子清理清单 RM §8.7/IP CLEANUP 章；事实源层级 RM §0.0（代码>设计>规范>架构>路线>计划；NRS=REFERENCE ONLY）。- **BT-ARCH-001 已落地（2026-09-05）**：`BttRoleDef`（kit/onKill/onTick/use）+`BttRoleDefs` 声明表（20 defs）=身份行为唯一事实源；BttEvents/KillHookMixin/BttGameWorldComponent 全部改为派发/纯数据；build+runServer 冒烟 PASS（`62 identities, 20 defs`）。**新增身份只加 def 条目，禁止 if-chain**。
- **BT-ARCH-001 已落地（2026-09-05）**：`BttRoleDef`（kit/onKill/onTick/use）+`BttRoleDefs` 声明表（20 defs）=身份行为唯一事实源；BttEvents/KillHookMixin/BttGameWorldComponent 全部改为派发/纯数据；build+runServer 冒烟 PASS（`62 identities, 20 defs`）。**新增身份只加 def 条目，禁止 if-chain**。
**下一批=选人 UI 五身份（BT-P2-UI）→ P2-α 补全 → DEMO-011**。
