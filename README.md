# 终点站抵达之前 (Before the Terminal)

在一列驶向终点站的列车上进行的社交推理玩法（类《狼人杀》《鹅鸭杀》）。玩家只知道自己的身份，通过近距离语音、观察尸体与使用身份能力，完成各自阵营的目标。

- 本体：[agmass/NoellesRoles](https://github.com/agmass/NoellesRoles) 的替换式演进分支（命名空间仍为 `noellesroles`）
- 引擎：[Wathe](https://modrinth.com/mod/XoU7Lf7D) 1.3.2 + [HarpyModLoader](https://modrinth.com/mod/JRhdijgD) 1.2.4
- 平台：Minecraft 1.21.1 / Fabric / Java 21
- 当前版本：`0.3.0-alpha-h1.3`（alpha 内测中，暂未发布）

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

## 致谢与许可

- 玩法策划：《终点站抵达之前》策划案。
- 上游：[agmass/NoellesRoles](https://github.com/agmass/NoellesRoles)（原版 README 的角色清单与致谢随上游保留在 git 历史）。
- 引擎：doctor4t 的 Wathe（**All Rights Reserved**，源码仅作只读参考，禁止再分发）。
