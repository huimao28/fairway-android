# 灰猫球道 v1.0.0-pre.1

## 新增内容

- 手表镜像（Wear OS，Pixel Watch 3）：手表上看当前洞、到果岭前/中/后沿距离，
  转表冠调杆数，下一洞按钮；手机算距离、手表显示 + 记分，Data Layer 同步
- 手表高尔夫运动记录（Health Services，ExerciseType.GOLF）：心率、时长、步数、
  卡路里，打完汇总存进手机那一轮的成绩里
- Pre-release：手机 + 手表两个 debug APK 当附件

## 已知问题

- 手表须连手机（蓝牙），手机不在身边时手表只显示最后同步的状态
- 手表无独立 GPS：距离由手机算好推过来
- 运动记录无 GPS 轨迹
- GolfShotEvent（自动识别挥杆/击球）未接
- 本次是 debug 包（release 构建无签名装不上）；先卸旧版再装
