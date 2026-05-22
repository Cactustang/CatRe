# 阶段 2 测试报告：首页一键打卡与统计

## 范围

本次验证覆盖首页默认行为卡片的一键打卡、`CheckInRecord` 写入、上次打卡、距今天数、近 30/90/180 天次数和今日已打卡状态。

## 实现规则

- 每次点击打卡都会新增一条记录。
- 同一天允许重复打卡。
- 今日已打卡状态表示当天至少存在一条该行为记录。
- 统计窗口使用当前设备本地时间计算，包含当前时刻往前 30/90/180 天内的记录。

## 已执行验证

### 构建验证

命令：

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --max-workers=1
```

结果：通过。

产物：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 单元测试任务

命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --max-workers=1
```

结果：通过，当前无测试源码，任务结果为 `NO-SOURCE`。

### Schema 验证

Room schema 已存在：

```text
app/schemas/com.catre.app.core.database.CatReDatabase/1.json
```

## 未执行验证

- 未执行 `adb install`。
- 未执行模拟器或真机手工点击打卡。
- 未执行数据库 sqlite 人工核验。
- 未执行跨天、跨时区、重复快速点击压力测试。

## 建议手工验证步骤

1. 在 Android Studio 打开 `C:\Users\Administrator\Desktop\CatRe`。
2. 启动模拟器并运行 App。
3. 清除 App 数据后首次启动。
4. 输入猫名并创建第一只猫。
5. 确认首页展示默认行为列表。
6. 点击任意行为的“打卡”按钮。
7. 确认该行为显示“今天已打卡”。
8. 确认上次打卡日期变为今天，距今天数为 `0`。
9. 确认近 30/90/180 天次数同步增加。
10. 关闭并重启 App，确认统计仍存在。

## 风险

- 当前统计聚合在 Repository 内存中完成，数据量变大后应评估性能。
- 当前允许同一天重复打卡，若后续产品决定每天只允许一次，需要增加幂等约束。
- 当前没有自动化测试覆盖日期窗口边界，需要在下一阶段补齐。
