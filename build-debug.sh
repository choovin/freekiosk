#!/bin/bash

# FreeKiosk Android APK 快速构建脚本（仅调试版）
# 用于快速开发和测试

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  FreeKiosk 调试版快速构建${NC}"
echo -e "${GREEN}========================================${NC}"

cd "$PROJECT_ROOT"

# 安装依赖
echo -e "\n${GREEN}[1/3] 安装 NPM 依赖...${NC}"
npm install

# 清理
echo -e "\n${GREEN}[2/3] 清理...${NC}"
cd android && ./gradlew clean

# 构建调试版
echo -e "\n${GREEN}[3/3] 构建调试版 APK...${NC}"
./gradlew assembleDebug

DEBUG_APK="$PROJECT_ROOT/android/app/build/outputs/apk/debug/app-debug.apk"

echo -e "\n${GREEN}✓ 构建完成！${NC}"
echo -e "  APK: $DEBUG_APK"
if [ -f "$DEBUG_APK" ]; then
    echo -e "  大小：$(du -h "$DEBUG_APK" | cut -f1)"
fi
