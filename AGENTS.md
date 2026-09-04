# AGENTS.md — 给 AI 协作者的工程备忘

本文件是任何在此仓库工作的 AI 协作者（你）的入口操作手册。先读它，再读 `docs/`。

## 1. 项目身份

- 目标：在原版 `agmas/NoellesRoles` 基础上**替换式演进**为策划案《终点站抵达之前》(Before the Terminal, 代号 BTT)。
- 基线：MC 1.21.1 / Fabric / Java 21 / 官方 `wathe-1.3.2-1.21.1.jar` / `harpymodloader-1.2.4-h1.3.jar` / 当前原版 NoellesRoles（`mod_version=1.7-h1.3`）。
- git：`origin https://github.com/hae-si/terminal`（用户仓库；演进自 `agmass/NoellesRoles`），`main` 分支。
- 谱系考古见 `docs/ARCHAEOLOGY.md`；策划案见 `docs/终点站抵达之前.docx`。

## 2. 构建与运行

```powershell
.\gradlew.bat build          # 编译+打 jar（产物 build/libs/noellesroles-1.7-h1.3.jar）
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
| `SPEC_TRACEABILITY.md` | 策划案→规范→任务→代码→验证 痕迹链 |

**计划与进度**

| 文件 | 职责 |
|---|---|
| `ROADMAP.md` | 阶段、优先级、延期事项（TODO 主清单） |
| `IMPLEMENTATION_PLAN.md` | 具体施工任务（含状态与验证记录） |

**其他**

| 文件 | 职责 |
|---|---|
| `终点站抵达之前.docx` | 策划案原件（不改） |

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
- CCA 组件注册于 `NoellesRolesComponents`（`EntityComponentInitializer`/`WorldComponentInitializer`）+ `fabric.mod.json` 的 `custom.cardinal-components`。
- 网络：C2S payload 注册 `PayloadTypeRegistry.playC2S()` + `ServerPlayNetworking.registerGlobalReceiver`；S2C 用 `ServerPlayNetworking.send` 或 CCA `AutoSyncedComponent`。

## 7. 当前进度

- **Phase 0 已验收**：`build` 与 `runServer` 均通过。
- **WATHE-API-001 已验收**：六项 PASS（结论见 SYSTEM_SPEC §21）；基线改动 C-001（ratatouille 1.4.3）。
- **DEMO-001~012（除 DEMO-011）已实现并探针验证（2026-09-03）**：BTT GameMode+席位分配（教父/义警/小丑/医生/处子/列车长）+身份宣告+击杀口径+处子发光+医生验尸+教父查验+小丑疯魔+结局判定宣告+列车长钥匙+隔离收尾；生命周期/单元探针全 PASS（探针已删）。键名决策 D7=自然键名渐进替换（doc-小丑接管 `noellesroles:jester`，NR 旧行为 BTT 门控）。
- **仅剩 DEMO-011**：≥6 玩家多人完整一局实测（RM §5.3 DoD #13 + 全项回归）；代码侧 Phase 1 已就绪。
- **联调进展（2026-09-04）**：用户假人实测“运行正常”；两个 wathe 基线坑已修并登记 §0a C-011（全新客户端配置 NPE 垫片）与 C-012（结局文本直接替换 wathe 结束覆盖层，聊天广播移除，`btt_game.lastEnding` 同步）。`release/` 为完整部署包（含依赖）。
- 策划案已去除星号设定。下一步默认=多人实测收尾；除非被明确要求，不要自行开始 Phase 2。
- 全部基线改动登记于 SYSTEM_SPEC §0a（C-001~C-012）。
