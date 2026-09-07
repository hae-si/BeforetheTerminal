# AGENTS.md — AI 协作者规则

本文件只放必须长期遵守的规则。历史、进度、考古一律不进此文件。

## 项目身份

- 本仓库 = 《终点站抵达之前》(Before the Terminal, **BTT**)：在原版 `agmass/NoellesRoles` 之上**替换式演进**的社交推理玩法，运行于官方 wathe 1.3.2 + HarpyModLoader 1.2.4（MC 1.21.1 / Fabric / Java 21）。
- git：`origin https://github.com/hae-si/terminal`，`main` 分支。命名空间 `noellesroles`；BTT 代码在 `org.agmas.noellesroles.btt.*`。
- 策划案原件：`docs/终点站抵达之前.docx`（只读）。

## 构建与运行

```powershell
.\gradlew.bat build        # 编译+打 jar（产物 build/libs/noellesroles-<版本>.jar）
.\gradlew.bat compileJava  # 快速编译反馈
.\gradlew.bat runServer    # 专用服冒烟（需 run/eula.txt=true）
.\gradlew.bat runClient    # 客户端
```

- Java 21 JDK：`C:\Program Files\Microsoft\jdk-21.0.7.6-hotspot\`。无测试框架/lint；correctness = `compileJava` + `build` + 实跑。测试方法见 `docs/TESTING.md`。

## 铁律

1. **代码与实跑结果是最高事实源**。文档与代码冲突时以代码为准，并回改文档；不确定就标记 TODO/UNKNOWN，不得编造。
2. **最小改动**：复用 > 修改 > 扩展 > 新建。凡改基线（`build.gradle`/`gradle.properties`/`src/**`/资源/`fabric.mod.json`/mixin json），先在 `docs/SYSTEM_SPEC.md` 的"基线改动登记"加一行。
3. `references/**` 只读；wathe 源码 LICENSE = All Rights Reserved，禁复制其代码/资产；禁按 references 里的 fork 项目（spark/SRE/NRS）猜测官方 wathe 1.3.2 行为。
4. **API 先验证**再写代码：读 `references/Wathe` 源码 / javap / runServer 探针（探针用完即删）。
5. **垂直切片**：每次交付必须"能完整开并结束一局"；策划特性缺席必须登记 `docs/ROADMAP.md` TODO（`BT-*`），禁止实现"看起来一样"的简化版。
6. **新增身份只加 `BttRoleDefs` def 条目，禁止 if-chain**；实装后同步 `BttRoles.IMPLEMENTED`。
7. 仅在被明确要求时 commit/push；提交前查 `git status`/`git diff`。
8. 历史日志、调试过程、工作指令不进正式设计文档；同一事实只有一个 canonical source。

## 事实源层级

代码 > `GAME_DESIGN`/`ROLE_DESIGN`（设计） > `SYSTEM_SPEC`（API/技术结论） > `ARCHITECTURE`（架构现状） > `ROADMAP`（计划）。状态标记：VERIFIED=已验证 / 待实测=代码已写未运行验证 / 【待作者】=设计未定 / GAP=设计已定未实现。

## API 契约速记（详见 docs/SYSTEM_SPEC.md）

- 角色 = `dev.doctor4t.wathe.api.Role(Identifier, color, isInnocent, canUseKiller, MoodType, maxSprintTime, canSeeTime)`；在 `Noellesroles.java` 静态块/`BttRoles` 静态初始化注册，必须早于 HML `SERVER_STARTED` 的 refreshRoles。
- 击杀一律走 `GameFunctions.killPlayer(victim, spawnBody, killer, deathReason)`，可被 `AllowPlayerDeath.EVENT` 否决；身份分配后派发 `ModdedRoleAssigned.EVENT`。
- 生命周期：`GameFunctions.startGame → initializeGame → tickServerGameLoop → setRoundEndData + stopGame → finalizeGame`；BTT GameMode = `noellesroles:before_the_terminal`。
- mixin 配置：`src/main/resources/noellesroles.mixins.json`、`src/client/resources/noellesroles.client.mixins.json`；新 mixin 必须加进对应 json。
- CCA 组件在 `NoellesRolesComponents` + `fabric.mod.json` `custom.cardinal-components` 声明；网络 C2S = `PayloadTypeRegistry.playC2S()` + `ServerPlayNetworking.registerGlobalReceiver`。
- **写进 wathe/NR CCA 存档的任何 index/枚举序号必须恒定有效**（HML 注册晚于读档，曾致越界崩溃）。
- BTT 局内玩家消息走 Action Bar（聊天框不可见）；mood/需求/体力/商店机制均为 wathe 原生，BTT 只门控不改值。

## 测试环境要点（详见 docs/TESTING.md）

- 用户测试客户端实例 = `D:\Minecraft\.minecraft\versions\1.21.1-Fabric 0.19.5\`（logs/saves 在实例目录，不查项目 `run/`）。
- **gugle-carpet-addition（假人驻留）与 BTT 玩家交互冲突（枪/刀右键失效）——测交互前必须移除**。

## 文档索引

| 文档 | 职责 |
|---|---|
| `README.md` | 项目是什么、怎么跑、当前状态快照、文档入口 |
| `docs/GAME_DESIGN.md` | 游戏规则（策划案技术化；"应该是什么"的唯一设计事实源） |
| `docs/ROLE_DESIGN.md` | 全部 70 身份的设计规格与身份目录 |
| `docs/ROLE_INTERACTIONS.md` | 身份/系统间交互、优先级、冲突矩阵 |
| `docs/SYSTEM_SPEC.md` | 技术规范、wathe/HML API 结论、基线改动登记 |
| `docs/ARCHITECTURE.md` | 当前实际架构（类/时序/同步/门控） |
| `docs/IMPLEMENTATION_STATUS.md` | **"现在做到哪"的唯一来源**：已实现/部分/未实现/已知问题 |
| `docs/ROADMAP.md` | **"接下来做什么"的唯一来源**：阶段、TODO、开放决策 |
| `docs/TESTING.md` | 测试方法、环境、陷阱、已验证清单 |
| `docs/RELEASE.md` | 构建产物、版本规则、发布检查 |
| `docs/ARCHAEOLOGY.md` | 谱系考古、参考项目、历史决策与文档沿革（唯一历史区） |
