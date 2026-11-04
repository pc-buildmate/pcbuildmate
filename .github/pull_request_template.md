## 这个 PR 做了什么

<!-- 一句话；对应任务包 docs/tasks/<id>.md -->

- 任务包：
- 模块：
- 关联 Issue：

## 改了哪些路径

<!-- 必须与任务包 allowed paths 一致；多一个都要解释 -->

```
```

## 门禁输出

<!-- 贴最后 10 行，不要只写「通过」 -->

```text
$ node tools/check-docs.mjs
...

$ ./gradlew :domain:test :data:test
...
```

## UI 证据（改到 ui/ 时必填）

<!-- 截图或 10 秒录屏；空态/错误态/深色模式各一张更佳 -->

## 契约 / 范围检查

- [ ] 没有修改 `contracts/**`、`assets/fixtures/**`、`docs/01`–`04`
- [ ] 没有新增 `INTERNET` / 相机 / 推送 / 定位权限
- [ ] 没有出现范围外标识符（FPS、PerformanceBand 等）
- [ ] 没有 TODO / FIXME / println 进入 `src/main`
- [ ] diff ≤ 400 行（不含生成文件）
- [ ] 没有顺手格式化 / 重排 import / 升级依赖

## AI 使用声明

- 模型：
- 任务 ID：
- 人工复核人：
- 人工做了什么（跑命令 / 看截图 / 装 APK）：

## 还没做 / 已知风险

<!-- 明确列出；没有就写「无」 -->
