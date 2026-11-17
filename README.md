# 灰猫球道（Huimao Fairway）

通用高尔夫球场应用：下场时用的 GPS 测距 + 电子记分卡。跟灰猫地图一个系列。

## v1 功能（就这四个）

- GPS 测距：到果岭前/中/后沿、到沙坑水障、上一杆打了多远
- 电子记分卡：每洞杆数、推杆、罚杆
- 差点：简化版（最近 5 轮总杆与标准杆差值的均值）
- 成绩历史与统计：开球上球道率、上果岭率、推杆数

## v2 功能

- 多球场：打球页可切换球场（assets/courses/ 里有几个文件就有几个球场）
- 击球轨迹：每洞点“标记击球点”记下每杆落点，地图上画轨迹，每杆距离都列出来
- 天气：Open-Meteo 实时天气（气温、风速/风向），并按当前洞方向提示顺风/逆风/侧风
- 完整 WHS 差点：差点指数（最优微分 × 0.96，含软/硬上限）与球场差点；
  每洞按净双柏忌调整；rating/slope 随球场数据文件提供（桃花源四色台数据来自 TheGrint）
- 果岭读线：离洞口 100 米内，地图上画球到洞口的推杆线（绿色虚线）；
  OSM 没有果岭坡度数据，线为直线参考，实际 break 需现场判断

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
