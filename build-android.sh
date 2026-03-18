#!/bin/bash

# FreeKiosk Android APK 构建脚本
# 此脚本用于构建 FreeKiosk Android 应用的发布版 APK

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 项目根目录
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$PROJECT_ROOT/android"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  FreeKiosk Android APK 构建脚本${NC}"
echo -e "${GREEN}========================================${NC}"

# 检查 Node.js
if ! command -v node &> /dev/null; then
    echo -e "${RED}错误：未找到 Node.js${NC}"
    exit 1
fi
echo -e "${YELLOW}✓ Node.js 版本：$(node -v)${NC}"

# 检查 Java
if ! command -v java &> /dev/null; then
    echo -e "${RED}错误：未找到 Java${NC}"
    exit 1
fi
echo -e "${YELLOW}✓ Java 版本：$(java -version 2>&1 | head -n 1)${NC}"

# 检查 Android SDK
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    echo -e "${RED}错误：未设置 ANDROID_HOME 或 ANDROID_SDK_ROOT${NC}"
    exit 1
fi
echo -e "${YELLOW}✓ Android SDK: ${ANDROID_HOME:-$ANDROID_SDK_ROOT}${NC}"

# 进入项目目录
cd "$PROJECT_ROOT"

# 安装依赖
echo -e "\n${GREEN}[1/4] 安装 NPM 依赖...${NC}"
npm install

# 进入 Android 目录
cd "$ANDROID_DIR"

# 清理之前的构建
echo -e "\n${GREEN}[2/4] 清理之前的构建...${NC}"
./gradlew clean

# 构建调试版 APK
echo -e "\n${GREEN}[3/4] 构建调试版 APK...${NC}"
./gradlew assembleDebug

# 构建发布版 APK
echo -e "\n${GREEN}[4/4] 构建发布版 APK...${NC}"
./gradlew assembleRelease

# 输出 APK 路径
DEBUG_APK="$ANDROID_DIR/app/build/outputs/apk/debug/app-debug.apk"
RELEASE_APK="$ANDROID_DIR/app/build/outputs/apk/release/app-release.apk"

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}  构建完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "\n${YELLOW}调试版 APK:${NC}"
echo -e "  $DEBUG_APK"
echo -e "\n${YELLOW}发布版 APK:${NC}"
echo -e "  $RELEASE_APK"
echo ""

# 显示文件大小
if [ -f "$DEBUG_APK" ]; then
    echo -e "${YELLOW}调试版大小：$(du -h "$DEBUG_APK" | cut -f1)${NC}"
fi
if [ -f "$RELEASE_APK" ]; then
    echo -e "${YELLOW}发布版大小：$(du -h "$RELEASE_APK" | cut -f1)${NC}"
fi
