# 终点站抵达之前 (Before the Terminal)

在一列驶向终点站的列车上进行的社交推理玩法（类《狼人杀》《鹅鸭杀》）。玩家只知道自己的身份，通过近距离语音、观察尸体与使用身份能力，完成各自阵营的目标。

- 本体：[agmass/NoellesRoles](https://github.com/agmass/NoellesRoles) 的替换式演进分支（命名空间仍为 `noellesroles`）
- 引擎：[Wathe](https://modrinth.com/mod/XoU7Lf7D) 1.3.2 + [HarpyModLoader](https://modrinth.com/mod/JRhdijgD) 1.2.4
- 平台：Minecraft 1.21.1 / Fabric / Java 21
- 当前版本：`0.2.0-alpha-h1.3`（**代码进度已含 0.3.0-alpha 内容**；作者 2026-09-13 定：**alpha 测试期间不发布**——版本号/产物/tag 留到发版时统一处理）

## 玩法概览

- **阵营**：乘客、凶手、中立（独行 / 外人 / 狂人）。乘客与凶手互相残杀，独行中立按各自条件独胜，外人中立自成阵营并拥有"尾声"。
- **人数**：6–24 人一局（基准 18）；席位按人数公式分配（执法 = 凶手 = 中立 = 人数//6，平民 = 余数；关系对数 = 人数//8）。
- **系统**：理智/需求、狂气（凶手货币）与随身商店、处决与误杀、醉酒、尾声、关系（恋人/宿敌/双子）等。
- **通讯**：局内不可发文字，仅附近语音；注视玩家可见名字。

## 构建

```powershell
.\gradlew.bat build        # 编译 + 打 jar（产物 build/libs/noellesroles-<版本>.jar）
.\gradlew.bat runServer    # 专用服冒烟（需 run/eula.txt=true）
.\gradlew.bat runClient    # 客户端
```

- 依赖 jar 放在 `libs/`（不入库）：`wathe-1.3.2-1.21.1.jar`、`harpymodloader-1.2.4-h1.3.jar`。
- ratatouille 必须 1.4.3（见 `gradle.properties`）。

## 运行一局

- 开局：拉汽笛，或 `/wathe:start noellesroles:before_the_terminal wathe:harpy_express_night`（6–24 人）。
- 强制身份：`/forceRole <玩家> <身份>`（下一局生效）。
- 技能键 `G`；凶手商店 = 随身背包屏（E）。
- `release/` 为完整部署包（本 mod + Wathe + HML + ratatouille + Simple Voice Chat + 配置垫片）。

## 当前状态（2026-09-13 快照）

- **身份 70/70 均已实装**（`BttRoles.IMPLEMENTED` = 全目录：58 新键 + 12 接管）；未完成项只剩【待作者】口径与**多人/客户端实测积压**（见 `docs/IMPLEMENTATION_STATUS.md` §三、`docs/ROADMAP.md` §1.5）。
- 系统层：席位/关系/尾声/结局/醉酒/商店/对讲机/音效键/发光透视/理智归零崩溃 均已落地；音效文件仍是静音占位。
- 构建与冒烟：`gradlew build --offline` + `gradlew runServer --offline` 通过（`58 identities, 55 defs`）。

## 文档索引

> `docs/` 与 `references/` 为**本地文档区**（`.gitignore` 忽略，不入库）；下表为入口。

| 文档 | 职责 |
|---|---|
| `docs/GAME_DESIGN.md` | 游戏规则（设计唯一事实源） |
| `docs/ROLE_DESIGN.md` | 70 身份规格与身份目录 |
| `docs/ROLE_INTERACTIONS.md` | 身份/系统间交互与冲突矩阵 |
| `docs/SYSTEM_SPEC.md` | 技术规范、wathe/HML API 结论、基线改动登记 |
| `docs/ARCHITECTURE.md` | 当前实际架构（类/时序/同步/门控） |
| `docs/IMPLEMENTATION_STATUS.md` | "现在做到哪"的唯一来源 |
| `docs/ROADMAP.md` | "接下来做什么"（§1.5 = 0.3.0 alpha 收口清单） |
| `docs/TESTING.md` | 测试方法、环境、陷阱、逐批验证记录 |
| `docs/RELEASE.md` | 构建产物、版本规则、发布检查单 |
| `docs/ARCHAEOLOGY.md` | 谱系考古、参考项目、历史决策（唯一历史区） |

## 致谢与许可

- 玩法策划：《终点站抵达之前》策划案。
- 上游：[agmass/NoellesRoles](https://github.com/agmass/NoellesRoles)（原版 README 的角色清单与致谢随上游保留在 git 历史）。
- 引擎：doctor4t 的 Wathe（**All Rights Reserved**，源码仅作只读参考，禁止再分发）。
