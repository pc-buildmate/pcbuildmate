#!/bin/bash
# 驱动真机按 docs/defense/DEMO.md 的九步走一遍，并逐步截图。
#
# 用法:  bash qa/drive-demo.sh <run-label> [输出目录]
#   例:  bash qa/drive-demo.sh run1 qa/screenshots
#
# 前提: 一台已授权（adb devices 显示 device）的设备，应用已安装。
# 注意: 会 pm clear 应用数据，并清空本轮的截图文件。
set -u

RUN="${1:?usage: drive-demo.sh <run-label> [out-dir]}"
OUT="${2:-qa/screenshots}"
# Git Bash 会把 /sdcard/... 当成 Windows 路径改写，必须关掉路径转换。
case "$(uname -s)" in MINGW*|MSYS*) export MSYS_NO_PATHCONV=1;; esac
ADB="${ADB:-adb}"
PKG="${PKG:-io.github.pcbuildmate}"
# Windows 上 mktemp 给的是 MSYS 路径，node 读不到；用系统临时目录的绝对路径。
UI_XML="${TEMP:-/tmp}/pcbuildmate-ui-$RUN.xml"

mkdir -p "$OUT"

dump(){ "$ADB" shell uiautomator dump >/dev/null 2>&1; "$ADB" shell cat /sdcard/window_dump.xml > "$UI_XML" 2>/dev/null; }
shot(){ "$ADB" exec-out screencap -p > "$OUT/$RUN-$1.png"; }
# 按文本中心点击；找不到就报错退出（不允许静默跳过）
tap(){
  local xy
  xy=$(node -e '
    const fs=require("fs"), re=new RegExp(process.argv[1]);
    const x=fs.readFileSync(process.argv[2],"utf8");
    const m=[...x.matchAll(/<node[^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/g)]
      .find(n=>n[1]&&re.test(n[1]));
    console.log(m?Math.round((+m[2]+ +m[4])/2)+" "+Math.round((+m[3]+ +m[5])/2):"NOTFOUND");' "$1" "$UI_XML")
  if [ "$xy" = "NOTFOUND" ]; then echo "!! 找不到控件: $1" >&2; exit 1; fi
  "$ADB" shell input tap $xy
}
wait_text(){ # 等某个文本出现，最多 10 s
  for _ in $(seq 1 20); do dump; if node -e '
    const fs=require("fs"),re=new RegExp(process.argv[1]);const x=fs.readFileSync(process.argv[2],"utf8");
    process.exit([...x.matchAll(/<node[^>]*?text="([^"]*)"/g)].some(n=>n[1]&&re.test(n[1]))?0:1)' "$1" "$UI_XML"; then return 0; fi; sleep 0.5; done
  echo "!! 等待超时: $1" >&2; exit 1
}

"$ADB" shell am force-stop "$PKG"
"$ADB" shell pm clear "$PKG" >/dev/null
"$ADB" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
wait_text '^生成配置$'

# 1 Entry：推荐 → 返回 → 演示配置
tap '^生成配置$'; wait_text '¥7,900'; shot 01-entry-recommend
tap '^返回$'; wait_text '^载入演示配置'; tap '^载入演示配置'; wait_text '¥7,800'; shot 02-workspace

# 3 保存 A
tap '^保存当前配置$'; wait_text '已保存'

# 4 检查
tap '^检查兼容性$'; wait_text '显卡长度'; shot 03-diagnostics

# 5 候选
tap '^看看能换成什么$'; wait_text '替换候选'; shot 04-candidates

# 6 预览
tap '^预览$'; wait_text '新总价 ¥7,900'; shot 05-preview

# 7 应用 + 重检
tap '^应用并全量重检$'; wait_text '已应用并重检'; shot 06-applied
tap '^返回配置$'; wait_text '¥7,900'

# 8 保存 B → 回 Entry → 继续 → 比较
tap '^保存当前配置$'; wait_text '已保存'
tap '^返回$'; wait_text '^继续上次配置'; tap '^继续上次配置'; wait_text '^比较已保存'
tap '^比较已保存'; wait_text '^配置 B$'; shot 07-compare

# 9 回 Entry → 继续上次配置
tap '^返回$'; tap '^返回$'; wait_text '^继续上次配置'
tap '^继续上次配置'; wait_text '¥7,800'; shot 08-continue

rm -f "$UI_XML"
echo "OK: $RUN 完成，截图在 $OUT/$RUN-*.png"
