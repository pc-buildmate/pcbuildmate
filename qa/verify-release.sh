#!/bin/bash
# 校验一个发布 APK：sha256、签名、权限、ABI、版本。任何一项不符返回非零。
#
# 用法: bash qa/verify-release.sh <apk> [<sha256 文件或期望值>] [<期望 versionName>]
#   例: bash qa/verify-release.sh PC-Buildmate.apk PC-Buildmate.apk.sha256 1.0.0-defense
#   例: bash qa/verify-release.sh app.apk 750b2d76...
#
# 需要: build-tools 里的 apksigner / aapt，以及 JDK 17+（apksigner 需要 java）。
set -u

APK="${1:?usage: verify-release.sh <apk> [sha256-file-or-value] [expected-versionName]}"
EXPECT="${2:-}"
EXPECT_VERSION="${3:-}"
BT="${BUILD_TOOLS:-$(ls -d "${ANDROID_HOME:-$HOME/Android/Sdk}"/build-tools/*/ 2>/dev/null | tail -1)}"
[ -n "$BT" ] || { echo "找不到 build-tools，设置 BUILD_TOOLS 或 ANDROID_HOME" >&2; exit 2; }

# Windows 的 build-tools 只有 apksigner.bat
SIGNER="$BT/apksigner"; [ -x "$SIGNER" ] || SIGNER="$BT/apksigner.bat"
hash_of(){ sha256sum "$1" | awk '{print substr($1, length($1)-63)}'; }

fail=0
ok(){ echo "✅ $1"; }
bad(){ echo "❌ $1"; fail=1; }

# 1. sha256
if [ -n "$EXPECT" ]; then
  want=$( [ -f "$EXPECT" ] && awk '{print $1}' "$EXPECT" || echo "$EXPECT" )
  got=$(hash_of "$APK")
  [ "$want" = "$got" ] && ok "sha256 $got" || bad "sha256 不符：期望 $want，实际 $got"
else
  echo "… 未提供期望 sha256，跳过该项（实际 $(hash_of "$APK")）"
fi

# 2. 签名（必须是发布证书，不能是 debug）
certs=$("$SIGNER" verify --print-certs "$APK" 2>&1) || { bad "签名校验失败"; certs=""; }
if printf '%s' "$certs" | grep -q "CN=Android Debug"; then
  bad "用的是 debug 证书"
elif [ -n "$certs" ]; then
  ok "签名有效：$(printf '%s\n' "$certs" | grep -m1 'certificate DN' | sed 's/.*DN: //')"
fi

# 3. 权限黑名单
perms=$("$BT/aapt2" dump permissions "$APK" 2>/dev/null || true)
for p in android.permission.INTERNET android.permission.CAMERA \
         android.permission.ACCESS_FINE_LOCATION android.permission.POST_NOTIFICATIONS; do
  printf '%s' "$perms" | grep -q "$p" && bad "声明了不该有的权限：$p"
done
printf '%s' "$perms" | grep -q "android.permission.INTERNET" || ok "无 INTERNET 权限"

# 4. ABI
abis=$("$BT/aapt" dump badging "$APK" 2>/dev/null | grep -o "native-code:.*" || true)
printf '%s' "$abis" | grep -q "arm64-v8a" && ok "含 arm64-v8a（$abis）" || bad "缺 arm64-v8a：$abis"
printf '%s' "$abis" | grep -q "x86" && echo "… 另含 x86 ABI：$abis（确认是否有意）"

# 5. 版本与包名
pkg=$("$BT/aapt" dump badging "$APK" 2>/dev/null | grep -m1 -o "name='[^']*'" | head -1 | sed "s/name=//; s/'//g")
ver=$("$BT/aapt" dump badging "$APK" 2>/dev/null | grep -m1 -o "versionName='[^']*'" | sed "s/versionName=//; s/'//g")
[ "$pkg" = "io.github.pcbuildmate" ] && ok "包名 $pkg" || bad "包名异常：$pkg"

if [ -n "$EXPECT_VERSION" ]; then
  [ "$ver" = "$EXPECT_VERSION" ] && ok "versionName $ver" || bad "versionName 不符：期望 $EXPECT_VERSION，实际 $ver"
else
  echo "… versionName = $ver（未指定期望值，跳过比对）"
fi

[ $fail -eq 0 ] && echo "全部通过" || echo "有检查未通过"
exit $fail
