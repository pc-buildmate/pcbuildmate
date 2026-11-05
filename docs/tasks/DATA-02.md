# DATA-02 · Room 持久化

> 状态：**done**（2026-11-05）｜认领：@MoeField｜预估：2 天｜依赖：D1 / D2

## 目标

把当前内存里的 saved builds / drafts 换成可跨进程存活的数据层，同时保持 `BuildStorePort` / `DraftStorePort` 契约不变。

## 现状

`WorkspaceViewModel` 用 `MutableList` 保存 A/B 快照；杀进程后丢失。`contracts/Ports.kt` 已有两个端口，`contracts-test` 没有对应契约测试基类。

## 需要写

- `data/src/main/kotlin/.../store/RoomBuildStore.kt`
- `RoomDraftStore`
- Entity / DAO / Database（版本 1，`exportSchema=true`）
- 内存实现 `InMemoryBuildStore`（测试/降级用）
- `BuildStorePortContract` / `DraftStorePortContract` 测试基类（放 `contracts-test` 或 `:testing`）

## 验收

```bash
./gradlew :data:testDebugUnitTest
```

- 保存后杀进程/重建 DB 能读回同一 revision；
- 同一 build 的 revision 列表按 id 升序；
- 删除 build 后 revisions 一并清理；
- 契约测试在 Room 与内存实现上都过；
- 不新增网络权限。

## 不要做

- 不改 `contracts/` 字段；
- 不做云同步/账号；
- 不做价格历史。

## 结果

- `contracts-test` 增加 `BuildStorePortContract` / `DraftStorePortContract`；
- `InMemoryBuildStore` / `InMemoryDraftStore` 通过契约测试；
- `RoomBuildStore` / `RoomDraftStore` / `PcbuildmateDatabase`（Room 2.8.5 + KSP）；
- App 启动时 hydrate，保存时写 Room；杀进程后 Entry 出现“继续上次配置 · 已保存 2 份”；
- 真机验证：保存 A/B → force-stop → 重开 → 可恢复。

## 注意

- 端口是同步的，Room 用 `allowMainThreadQueries()`；演示规模小，后续可换 suspend + Dispatchers.IO；
- 诊断结果不持久化，恢复后重新检查；这是有意的（诊断绑定 revision）；
- Room 的 JVM 单测未加 Robolectric，Room 路径由真机验证；契约测试覆盖两种内存实现。
