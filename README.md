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

## 在线球场库（第 4 个 tab“球场”）

国内球场**离线搜**（不用联网），搜不到时自动走在线搜索（国外球场用）。

- 国内名单：`assets/poi/china-courses.json`，来自 OpenStreetMap（2026-11-17 提取，
  CN + TW + MO 三个 area 合并，362 个球场，含港澳台），已去掉练习场/训练场/学院/
  迷你等打不了的条目；OSM 上只有英文名的球场（如桃花源）补了已核实的中文别名，
  `aliases` 字段只放已核实的，不编造。刷新名单：重新跑 Overpass area 查询并按
  同一规则清洗后替换该文件，同时替换 `src/test/resources/poi/` 里的同一份拷贝
  （`ChinaCoursePoiTest` 会校验条数、桃花源别名与屏蔽词）。
- 搜索结果每行显示“国家 · 城市”（如“中国 · 常德”）：`city`/`country` 字段是
  提取时用 Nominatim 逆地理编码一次算好、随名单打包的，运行时不联网；
  在线搜到的球场没有这两个字段，只显示距离
- 按名搜索：先查离线名单（中文名/别名优先、英文名次之，全等 > 前缀 > 包含），
  没有再走 Nominatim 在线搜
- 附近球场：先查离线名单 50 公里内按距离排序，没有再走 Overpass 在线找
- 下载：先拉球场轮廓算 bbox，再拉洞/Tee/果岭/沙坑/水障，**端上配对**成 CoursePack
  （配对逻辑与当初 Python 提取桃花源的脚本一致：Tee 配洞线起点段、果岭配终点段，
  有 `CoursePackBuilderTest` 用同一份原始数据回归校验）；下载时该行显示
  LinearProgressIndicator 阶段进度条（拉轮廓 → 拉洞数据 → 配对 → 保存，
  只在阶段切换时更新，不假装连续）
- 下过的球场存在应用内部存储 `files/courses/osm_<wayId>.json`，与内置包同一格式，
  下次直接用；可以在球场库里删除
- 限制：只支持标准 18 洞（ref 1..18）；OSM 上大多数球场只有轮廓、没有洞级数据，
  那样的球场下载时会提示打不了；下载的球场没有 rating/slope，WHS 差点暂不显示

## 构建

```bash
export ANDROID_HOME=~/workspace/tools/android-sdk
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
