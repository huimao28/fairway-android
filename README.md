# 灰猫球道（Huimao Fairway）

通用高尔夫球场应用：下场时用的 GPS 测距 + 电子记分卡。跟灰猫地图一个系列。

## v1 功能（就这四个）

- GPS 测距：到果岭前/中/后沿、到沙坑水障、上一杆打了多远
- 电子记分卡：每洞杆数、推杆、罚杆
- 差点：简化版（最近 5 轮总杆与标准杆差值的均值）
- 成绩历史与统计：开球上球道率、上果岭率、推杆数

## 设计与技术

- 设计：Material You 3 Expressive
- 技术：Kotlin + Compose；地图用 MapLibre + OSM 栅格底图（无 Key，洞线/果岭/Tee/沙坑/水障叠加显示）
- 开发按 Google 官方 android/skills（Compose 性能、权限安全、返回手势）

## 球场数据（通用可插拔）

球场数据是 OSM 导出的 JSON，一个文件一个球场，放在 `app/src/main/assets/courses/`。
加球场 = 加一个 JSON 文件，不用改代码。首批内置常德桃花源国际高尔夫俱乐部
（18 洞全有 par/handicap；Tee 72 个；果岭 15 个带完整多边形；1/4/5/9 号洞缺果岭，
先用洞线终点兜底）。

## 构建

```bash
export ANDROID_HOME=~/workspace/tools/android-sdk
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
