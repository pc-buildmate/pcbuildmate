#!/usr/bin/env bash
# 事件驱动的静态审核：只读 GitHub 元数据 + diff 文件名，不执行 PR 代码、不合并。
# 用法（本地演练）：
#   MODE=pr NUMBER=85 REPO=pc-buildmate/pcbuildmate GH_TOKEN=$(gh auth token) DRY_RUN=1 bash ci/static-review.sh
set -u

MODE="${MODE:?MODE=pr|issue}"
NUMBER="${NUMBER:?NUMBER}"
REPO="${REPO:?REPO=owner/name}"
GH_TOKEN="${GH_TOKEN:?GH_TOKEN}"
DRY_RUN="${DRY_RUN:-0}"

export GH_TOKEN

fail() { echo "static-review: $*" >&2; exit 2; }
api() { gh api -H "Accept: application/vnd.github+json" "$@"; }

# ---------------------------------------------------------------- PR
if [ "$MODE" = "pr" ]; then
  data=$(gh pr view "$NUMBER" --repo "$REPO" \
    --json number,title,author,additions,deletions,changedFiles,isDraft,mergeStateStatus,statusCheckRollup,comments,headRefOid,baseRefName 2>/dev/null) \
    || fail "读不到 PR #$NUMBER"
  author=$(jq -r '.author.login' <<<"$data")
  [ "$author" = "MoeField" ] && { echo "own PR, skip"; exit 0; }

  sha=$(jq -r '.headRefOid' <<<"$data")
  marker="<!-- auto-review:pr=$NUMBER:sha=$sha -->"
  if jq -r '.comments[].body' <<<"$data" | grep -qF "$marker"; then
    echo "already reviewed $sha, skip"; exit 0
  fi

  files=$(gh pr diff "$NUMBER" --repo "$REPO" --name-only 2>/dev/null || true)
  nfiles=$(grep -c . <<<"$files" || true)
  add=$(jq -r '.additions' <<<"$data")
  del=$(jq -r '.deletions' <<<"$data")
  draft=$(jq -r '.isDraft' <<<"$data")
  body=$(gh pr view "$NUMBER" --repo "$REPO" --json body --jq '.body // ""' 2>/dev/null || true)

  frozen=$(grep -E '^(contracts/|assets/rules/|assets/fixtures/|docs/0[1-4]-)' <<<"$files" || true)
  tests=$(grep -E 'src/(test|androidTest)/' <<<"$files" || true)

  checks=$(gh pr checks "$NUMBER" --repo "$REPO" 2>/dev/null || true)
  nok=$(grep -c 'pass' <<<"$checks" || true)
  nfail=$(grep -c 'fail' <<<"$checks" || true)
  npending=$(grep -cE 'pending|queued|in progress' <<<"$checks" || true)

  identity="个人账号"
  [ "$author" = "pc-buildmate" ] && identity="⚠️ 共享账号（请用个人账号重开）"
  reviewer=""
  if ! grep -qE '任务包|Task|allowed paths' <<<"$body"; then reviewer+="- PR 模板缺少“任务包 / allowed paths”说明"$'\n'; fi
  if ! grep -qE '门禁|check-docs|gradlew' <<<"$body"; then reviewer+="- PR 模板缺少门禁输出"$'\n'; fi
  [ -n "$frozen" ] && reviewer+="- **命中冻结区**：$(tr '\n' ' ' <<<"$frozen")"$'\n'
  [ $((add + del)) -gt 400 ] && reviewer+="- diff 较大：+$add/-$del（建议拆分）"$'\n'
  [ -z "$tests" ] && reviewer+="- 没有测试改动（如果是纯文档可忽略）"$'\n'
  [ "$nfail" -gt 0 ] && reviewer+="- CI 有失败：$nfail 项"$'\n'

  verdict="LGTM（静态检查）"
  [ -n "$reviewer" ] && verdict="需要人工看一眼"

  comment=$(cat <<MSG
$marker
## 自动静态审核（非阻塞）

- 作者：@$author（$identity）
- 范围：$nfiles 个文件，+$add/-$del；draft=$draft
- 冻结区：$( [ -n "$frozen" ] && echo "⚠️ 命中" || echo "无" )
- CI：pass=$nok / fail=$nfail / pending=$npending
- 测试改动：$( [ -n "$tests" ] && echo "有" || echo "无" )
- 结论：**$verdict**

$reviewer
> 只做静态元数据检查，不执行 PR 代码、不 approve、不合并；深度评审由定时审核 agent 负责。
MSG
)

  if [ "$DRY_RUN" = "1" ]; then printf '%s\n' "$comment"; exit 0; fi
  gh pr comment "$NUMBER" --repo "$REPO" --body "$comment" >/dev/null && echo "commented on PR #$NUMBER"
  exit 0
fi

# -------------------------------------------------------------- issue
if [ "$MODE" = "issue" ]; then
  data=$(gh issue view "$NUMBER" --repo "$REPO" \
    --json number,title,author,body,comments,updatedAt,state 2>/dev/null) || fail "读不到 issue #$NUMBER"
  author=$(jq -r '.author.login' <<<"$data")
  [ "$author" = "MoeField" ] && { echo "own issue, skip"; exit 0; }
  updated=$(jq -r '.updatedAt' <<<"$data")
  marker="<!-- auto-review:issue=$NUMBER:updated=$updated -->"
  if jq -r '.comments[].body' <<<"$data" | grep -qF "$marker"; then
    echo "already reviewed $updated, skip"; exit 0
  fi

  body=$(jq -r '.body // ""' <<<"$data")
  nbody=${#body}
  identity="个人账号"
  [ "$author" = "pc-buildmate" ] && identity="⚠️ 共享账号（请用个人账号重开）"

  owner="待定"
  grep -qiE 'cooler|散热' <<<"$body" && owner="陈思言（数据）/ 刘喆（data）"
  grep -qiE '3d|预览|preview' <<<"$body" && owner="阮立坤（UI）"
  grep -qiE 'release|workflow|ci|tag' <<<"$body" && owner="杨壹硕（QA/CI）"
  grep -qiE 'lint|icon|manifest' <<<"$body" && owner="阮立坤（UI）"

  checklist=""
  grep -qE '验收|acceptance|完成|done' <<<"$body" || checklist+="- 缺少验收标准（建议补“怎么算完成”）"$'\n'
  [ "$nbody" -lt 80 ] && checklist+="- 正文较短，可能缺复现/影响范围"$'\n'
  [ -n "$checklist" ] || checklist="- 需求描述包含验收/范围，按 issue 处理即可"$'\n'

  comment=$(cat <<MSG
$marker
## 自动 triage（非阻塞）

- 作者：@$author（$identity）
- 正文长度：$nbody 字
- 建议 owner：$owner

$checklist
> 自动 triage 只读 issue 文本；不关闭、不改标签。
MSG
)

  if [ "$DRY_RUN" = "1" ]; then printf '%s\n' "$comment"; exit 0; fi
  gh issue comment "$NUMBER" --repo "$REPO" --body "$comment" >/dev/null && echo "commented on issue #$NUMBER"
  exit 0
fi

fail "未知 MODE=$MODE"
