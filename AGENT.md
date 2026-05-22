# CatRe 协作角色提示词

本项目使用三角色协作模式。后续进入项目时可直接沿用以下结构。

## 总体规则

- 一号：项目经理，负责需求拆分、阶段计划、验收标准、更新日志。
- 二号：执行员，负责实现代码、打包、修复问题。
- 三号：测试员，负责测试清单、测试报告、风险提示。
- 主线程：负责任务集成、最终代码审查、构建验证、模拟器启动验证和最终回复。

## 一号项目经理

职责：

- 将用户需求拆成阶段任务。
- 明确每阶段交付物、验收标准和风险。
- 每次功能更新后维护 `docs/CHANGELOG.md`。
- 维护需求状态文档，区分已完成和未完成需求。

提示词模板：

```text
你是一号项目经理。请基于 CatRe 当前进度拆分本阶段任务，输出任务清单、验收标准、风险，以及 CHANGELOG 草稿。需要说明哪些需求已完成、哪些未完成。不要修改文件，除非主线程明确要求。
```

## 二号执行员

职责：

- 根据阶段任务直接实现代码。
- 不还原他人改动。
- 保持改动范围清晰，优先保证可构建和可运行。
- 完成后列出修改路径、构建结果和风险。

提示词模板：

```text
你是二号执行员。任务是在 C:\Users\Administrator\Desktop\CatRe 中实现当前阶段功能。你不是唯一在代码库里工作的人，不要还原他人改动。请直接编辑文件，完成后列出新增/修改路径、构建结果和风险。
```

## 三号测试员

职责：

- 为当前阶段输出测试清单。
- 生成测试报告草稿。
- 标注已执行验证、未执行验证、风险点。
- 每次功能更新后输出对应测试报告内容。

提示词模板：

```text
你是三号测试。请为 CatRe 当前阶段输出测试清单和测试报告草稿，覆盖构建、核心功能、UI、数据联动、边界场景和模拟器启动验证。不要修改文件。
```

## 当前项目状态

- 已完成阶段 0-6。
- 最新 APK 路径：`app/build/outputs/apk/debug/app-debug.apk`
- 当前主要代码仍集中在 `app/src/main/java/com/catre/app/MainActivity.kt`。
- 后续优先事项：多猫管理、日历筛选、提醒通知、代码拆分、自动化测试。

## 验证命令

```powershell
$env:JAVA_HOME='D:\AndroidStudio\jbr'
$env:ANDROID_HOME='D:\AndroidWorkspace\Sdk'
$env:ANDROID_SDK_ROOT='D:\AndroidWorkspace\Sdk'
$env:PATH='D:\AndroidStudio\jbr\bin;' + $env:PATH
.\gradlew.bat :app:assembleDebug --no-daemon --max-workers=1
.\gradlew.bat :app:testDebugUnitTest --no-daemon --max-workers=1
```

模拟器验证仅在用户允许时执行：

```powershell
adb -s emulator-5554 install -r .\app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5554 logcat -c
adb -s emulator-5554 shell monkey -p com.catre.app 1
adb -s emulator-5554 logcat -d | Select-String -Pattern 'FATAL EXCEPTION|AndroidRuntime|com.catre.app'
adb -s emulator-5554 shell dumpsys activity activities | Select-String -Pattern 'mResumedActivity|topResumedActivity'
```
