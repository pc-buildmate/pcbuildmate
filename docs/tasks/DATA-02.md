# DATA-02 · Room 持久化

> 状态：**backlog**｜建议 owner：@LIU132578（可换）｜预估：2 天｜依赖：D1 / D2

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

## 第一步

先写 `BuildStorePortContract`，再让 Room 实现通过；不要先写 DAO。
