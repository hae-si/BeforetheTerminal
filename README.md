# 终点站抵达之前 (Before the Terminal / BTT)

在一列驶向终点站的列车上进行的社交推理玩法。

- 本体：`agmass/NoellesRoles` 的替换式演进 fork（命名空间仍为 `noellesroles`）
- 引擎：[wathe](https://modrinth.com/mod/XoU7Lf7D) 1.3.2 + [HarpyModLoader](https://modrinth.com/mod/JRhdijgD) 1.2.4
- 平台：Minecraft 1.21.1 / Fabric / Java 21
- 当前版本：`0.2.0-alpha-h1.3`（版本规则见 `docs/RELEASE.md`）

## 如何运行

```powershell
.\gradlew.bat build        # 编译 + 打 jar（产物 build/libs/noellesroles-<版本>.jar）
.\gradlew.bat runServer    # 专用服冒烟（需 run/eula.txt=true）
.\gradlew.bat runClient    # 客户端
```

- 依赖 jar（`libs/`，不入库）：`wathe-1.3.2-1.21.1.jar`、`harpymodloader-1.2.4-h1.3.jar`；ratatouille 必须 1.4.3（见 `gradle.properties`）。
- 开局：拉汽笛，或 `/wathe:start noellesroles:before_the_terminal wathe:harpy_express_night`（6–18 人）。
- `release/` 为完整部署包（本 mod + wathe + HML + ratatouille + Simple Voice Chat + 配置垫片说明）。
- 详细测试方法与环境陷阱见 `docs/TESTING.md`。

## 当前状态（快照）

- 核心闭环可玩：开局 → 席位分配 → 迎新 → 对局（击杀/处决/商店/理智）→ 三主结局判定 → 结局覆盖层 → 清理再开。
- 70 身份全量注册（57 新键 + 13 接管 NR 旧键）；**33 身份有真实行为**，其余仅注册元数据；醉酒/尾声/本能透视豁免等系统未做。
- 2026-09-06 策划案大改（中立三分类、席位公式、同色互斥等）**尚未落入代码**，已登记 GAP。
- 精确进度见 `docs/IMPLEMENTATION_STATUS.md`；下一步见 `docs/ROADMAP.md`。

## 文档地图

| 文档                              | 职责                           |
| ------------------------------- | ---------------------------- |
| `docs/GAME_DESIGN.md`           | 游戏规则（"应该是什么"的设计事实源）          |
| `docs/ROLE_DESIGN.md`           | 全部 70 身份的设计规格与身份目录           |
| `docs/ROLE_INTERACTIONS.md`     | 身份/系统间交互、冲突矩阵                |
| `docs/SYSTEM_SPEC.md`           | 技术规范、wathe/HML API 结论、基线改动登记 |
| `docs/ARCHITECTURE.md`          | 当前实际架构（类/时序/同步/门控）           |
| `docs/IMPLEMENTATION_STATUS.md` | 现在做到哪（唯一来源）                  |
| `docs/ROADMAP.md`               | 接下来做什么（唯一来源）                 |
| `docs/TESTING.md`               | 测试方法、环境、陷阱、已验证清单             |
| `docs/RELEASE.md`               | 构建产物、版本规则、发布检查               |
| `docs/ARCHAEOLOGY.md`           | 谱系考古、参考项目、历史决策（唯一历史区）        |
| `docs/终点站抵达之前.docx`             | 策划案原件（只读）                    |

AI 协作者请先读 `AGENTS.md`。

## 致谢

- 玩法策划：《终点站抵达之前》策划案（见 docs/）
- 上游：[agmass/NoellesRoles](https://github.com/agmass/NoellesRoles)（原 README 的 NR 角色清单与致谢随上游保留在 git 历史；本 fork 当前状态以本文档为准）
- 引擎：doctor4t 的 Wathe（All Rights Reserved，源码仅作只读参考）
