# 阶段 3 测试报告：行为管理与首页视觉升级

## 范围

本次验证覆盖行为管理基础、频率规则基础、固定底部导航、首页猫咪展示区、logo/背景图资源接入。

## 已实现内容

- 固定底部导航：首页、日历、行为、我的。
- 首页头部展示生成的 CatRe logo 和首页背景图。
- 首页展示猫咪名称、行为数量、累计记录数。
- 行为页支持新增自定义行为。
- 行为页支持编辑行为名称、图标 key、颜色、首页展示开关和频率规则。
- 首页只展示 `showOnHome = true` 的行为。
- 首页行为卡展示频率状态。

## 资源验证

新增资源：

```text
app/src/main/res/drawable-nodpi/catre_logo.png
app/src/main/res/drawable-nodpi/catre_home_header.png
```

生成方式：使用内置 image generation 工具生成项目位图资源，并复制到 Android 资源目录。

## 构建验证

命令：

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --max-workers=1
```

结果：通过。

APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 单元测试任务

命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --max-workers=1
```

结果：通过，当前无测试源码，任务结果为 `NO-SOURCE`。

## 模拟器验证

设备：

```text
emulator-5554
```

执行项：

- 安装 Debug APK：通过。
- 启动 `com.catre.app`：通过。
- 检查前台 Activity：通过。
- 检查 Logcat 启动崩溃：未发现 `FATAL EXCEPTION`。

前台 Activity：

```text
com.catre.app/.MainActivity
```

## 待手工验证

- 底部导航在首页滚动时是否始终固定在底部。
- 首页 logo、背景图、文字可读性和小屏适配。
- 行为页新增行为后，首页是否按 `showOnHome` 联动显示。
- 编辑行为名称、颜色、频率后，首页是否同步刷新。
- 关闭首页展示开关后，行为是否从首页隐藏且历史记录不丢失。
- 重启 App 后行为配置是否保持。

## 风险

- 当前行为管理和首页仍集中在 `MainActivity.kt`，后续应拆分到 feature 模块。
- 频率状态已有基础展示，但未接入通知提醒。
- 行为删除、补录、历史详情仍未实现。
- 自动化测试仍缺失，日期窗口和频率边界需要后续补测。
