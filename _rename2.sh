#!/bin/bash
set -e
cd "D:/开发项目/dsh-android"
echo "== 1. package + path content replace =="
mapfile -t F < <(grep -rl --exclude-dir=.git --include=*.kt --include=*.kts --include=*.xml --include=*.gradle --include=*.properties --include=*.pro --include=*.json "ai.openclaw" . 2>/dev/null)
for f in "${F[@]}"; do
  sed -i 's|ai\.openclaw\.app|ai.deepseek.harness|g; s|ai\.openclaw\.wear|ai.deepseek.wear|g; s|ai/openclaw/app|ai/deepseek/harness|g; s|ai/openclaw/wear|ai/deepseek/wear|g' "$f"
done
echo "  pkg files: ${#F[@]}"
echo "== 2. OpenClaw identifier -> DeepSeekHarness (kt/kts/xml/gradle) =="
mapfile -t G < <(grep -rl --exclude-dir=.git --include=*.kt --include=*.kts --include=*.xml --include=*.gradle "OpenClaw" . 2>/dev/null)
for f in "${G[@]}"; do
  sed -i 's|OpenClaw|DeepSeekHarness|g' "$f"
done
echo "  ident files: ${#G[@]}"
echo "== 3. build property openclawBuild -> deepseekHarnessBuild =="
grep -rl --exclude-dir=.git "openclawBuild" --include=*.kts --include=*.properties . 2>/dev/null | while read f; do
  sed -i 's|openclawBuild|deepseekHarnessBuild|g' "$f"
done
echo "== 4. ai_openclaw_app raw refs -> ai_deepseek_harness =="
grep -rl --exclude-dir=.git "ai_openclaw_app" --include=*.xml . 2>/dev/null | while read f; do
  sed -i 's|ai_openclaw_app|ai_deepseek_harness|g' "$f"
done
echo "== 5. move source dirs (app-style) =="
for d in $(find . -path ./.git -prune -o -type d -path "*/ai/openclaw/app" -print); do
  gp=$(dirname "$(dirname "$d")"); mkdir -p "$gp/deepseek"; git mv "$d" "$gp/deepseek/harness"
done
echo "== 6. move source dirs (wear-style) =="
for d in $(find . -path ./.git -prune -o -type d -path "*/ai/openclaw/wear" -print); do
  gp=$(dirname "$(dirname "$d")"); mkdir -p "$gp/deepseek"; git mv "$d" "$gp/deepseek/wear"
done
echo "== 7. schemas + THIRD_PARTY_LICENSES dirs =="
for d in app/schemas/ai.openclaw.app.chat.*; do [ -d "$d" ] && git mv "$d" "${d/ai.openclaw.app/ai.deepseek.harness}"; done
[ -d THIRD_PARTY_LICENSES/openclaw ] && git mv THIRD_PARTY_LICENSES/openclaw THIRD_PARTY_LICENSES/deepseek
echo "== 8. rename OpenClaw*.kt files =="
find . -path ./.git -prune -o -type f -name "OpenClaw*.kt" -print | while read f; do
  nf="${f%/*}/DeepSeekHarness${f##*/OpenClaw}"; git mv "$f" "$nf"
done
echo "== 9. rename ai_openclaw_app_*.xml raw files =="
find . -path ./.git -prune -o -type f -name "ai_openclaw_app_*" -print | while read f; do
  nf="${f/ai_openclaw_app/ai_deepseek_harness}"; git mv "$f" "$nf"
done
echo "== 10. display strings: DeepSeekHarness -> DeepSeek Harness in strings.xml & md =="
find . -path ./.git -prune -o -name strings.xml -print | while read f; do sed -i 's|DeepSeekHarness|DeepSeek Harness|g' "$f"; done
find . -path ./.git -prune -o -name "*.md" -print | while read f; do sed -i 's|OpenClaw|DeepSeek Harness|g; s|openclaw|DeepSeek Harness|g' "$f"; done
echo "== done =="
