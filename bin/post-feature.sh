#!/bin/bash
# post-feature.sh — 功能完成后的标准化工作流
#
# 功能：
#   1. 生成知识库文档（knowledge/）
#   2. 更新 CHANGELOG.md
#   3. 增加版本号
#   4. 创建符合规范的 git commit
#   5. 可选：创建 git tag
#
# 使用前提：
#   - 当前代码已完成开发和测试
#   - 工作区干净（无未提交更改）
#
# 使用方式：
#   cd freekiosk（或 freekiosk-hub）
#   bash ../bin/post-feature.sh
#
# 环境要求：
#   - date 命令（GNU coreutils）
#   - node / npm（用于 npm version）
#   - git

set -e

# ═══════════════════════════════════════════════════════════════
# 配置
# ═══════════════════════════════════════════════════════════════

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KNOWLEDGE_DIR="$REPO_ROOT/knowledge"
CHANGELOG="$REPO_ROOT/CHANGELOG.md"
PACKAGE_JSON="$REPO_ROOT/package.json"

# 项目名（从当前目录推断）
PROJECT_NAME="$(basename "$REPO_ROOT")"

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# ═══════════════════════════════════════════════════════════════
# 工具函数
# ═══════════════════════════════════════════════════════════════

info()    { echo -e "${BLUE}[ℹ️  INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[✅ SUCCESS]${NC}  $*"; }
warn()    { echo -e "${YELLOW}[⚠️  WARN]${NC}  $*"; }
error()   { echo -e "${RED}[❌ ERROR]${NC}  $*"; }
section() { echo ""; echo -e "${BOLD}${CYAN}══ $1 ══${NC}"; }

# 检查 git 状态
check_git() {
    if [ ! -d ".git" ]; then
        error "不是 git 仓库：$(pwd)"
        exit 1
    fi
}

# 检查工作区是否干净
check_clean() {
    if [ -n "$(git status --porcelain)" ]; then
        error "工作区不干净，请先 commit 所有更改"
        git status --short
        exit 1
    fi
}

# 获取当前分支
get_branch() {
    git branch --show-current
}

# ═══════════════════════════════════════════════════════════════
# 版本号管理
# ═══════════════════════════════════════════════════════════════

# 从 package.json 读取版本
get_version() {
    if [ -f "$PACKAGE_JSON" ]; then
        node -e "console.log(require('./package.json').version)"
    else
        grep -m1 '## \[' "$CHANGELOG" | sed -E 's/.*\[([^]]+)\].*/\1/'
    fi
}

# 递增版本号（默认 patch）
bump_version() {
    local bump_type="${1:-patch}"
    local current_version
    current_version=$(get_version)
    info "当前版本: $current_version"

    if [ -f "$PACKAGE_JSON" ]; then
        # 使用 npm version 自动递增并创建 tag
        npm version "$bump_type" --no-git-tag-version 2>/dev/null
        local new_version
        new_version=$(node -e "console.log(require('./package.json').version)")
        success "版本已更新: $current_version → $new_version"
        echo "$new_version"
    else
        # 手动递增
        local major minor patch
        IFS='.' read -r major minor patch <<< "$current_version"
        case "$bump_type" in
            major) major=$((major + 1)); minor=0; patch=0 ;;
            minor) minor=$((minor + 1)); patch=0 ;;
            patch) patch=$((patch + 1)) ;;
        esac
        local new_version="$major.$minor.$patch"
        success "版本已更新: $current_version → $new_version (手动)"
        echo "$new_version"
    fi
}

# ═══════════════════════════════════════════════════════════════
# 文档生成
# ═══════════════════════════════════════════════════════════════

# 生成文件名
generate_filename() {
    local title="$1"
    local timestamp
    timestamp=$(date '+%Y%m%d-%H%M%S')
    # 标题转拼音或英文单词（简单处理）
    local safe_title
    safe_title=$(echo "$title" | sed 's/[^a-zA-Z0-9\u4e00-\u9fa5]/-/g' | tr 'A-Z' 'a-z' | sed 's/--*/-/g' | sed 's/^-\|-\$//g' | cut -c1-50)
    echo "${timestamp}-${safe_title}.md"
}

# 获取当前分支类型
get_change_type() {
    local branch
    branch=$(get_branch)
    if echo "$branch" | grep -qE "^feat/|/feat/"; then echo "feat"
    elif echo "$branch" | grep -qE "^fix/|/fix/"; then echo "fix"
    elif echo "$branch" | grep -qE "^docs?/|/docs?"; then echo "docs"
    elif echo "$branch" | grep -qE "^refactor/|/refactor/"; then echo "refactor"
    elif echo "$branch" | grep -qE "^ui/|/ui/"; then echo "ui"
    elif echo "$branch" | grep -qE "^api/|/api/"; then echo "api"
    elif echo "$branch" | grep -qE "^build/|/build/"; then echo "build"
    elif echo "$branch" | grep -qE "^db/|/db/"; then echo "db"
    else echo "feat"
    fi
}

# 生成知识库文档
generate_doc() {
    local feature_title="$1"
    local feature_desc="$2"
    local impact="$3"
    local type="$4"
    local filename
    filename=$(generate_filename "$feature_title")
    local today
    today=$(date '+%Y-%m-%d')
    local timestamp
    timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    local author
    author=$(git config user.name || echo "Unknown")

    info "生成文档: $KNOWLEDGE_DIR/$filename"

    cat > "$KNOWLEDGE_DIR/$filename" << EOF
# ${feature_title}

**日期:** ${today}
**类型:** ${type}
**影响范围:** ${impact}
**负责人:** ${author}

---

## 概述

${feature_desc}

---

## 变更详情

### 修改的文件

\`\`\`
$(git diff --name-only HEAD~1..HEAD 2>/dev/null | sed 's/^/  /')
\`\`\`

### 新增的功能

-

### 修改的内容

-

### 移除的内容

-

---

## 技术实现

### 核心逻辑

\`\`\`
// 关键代码片段
\`\`\`

### API 变更（如有）

| 接口 | 方法 | 说明 |
|------|------|------|
| | | |

### 数据库变更（如有）

\`\`\`sql
--
\`\`\`

---

## 测试验证

### 测试场景

- [ ] 功能测试：
- [ ] 边界测试：
- [ ] 回归测试：

### 测试结果

- 单元测试：✅ / ❌
- 集成测试：✅ / ❌
- 手动测试：✅ / ❌

---

## 部署注意事项

-
-

---

## 相关文档

-

---

*本文档由 post-feature.sh 自动生成于 ${timestamp}*
EOF

    success "文档已生成: $filename"
    echo "$filename"
}

# ═══════════════════════════════════════════════════════════════
# Changelog 更新
# ═══════════════════════════════════════════════════════════════

# 更新 changelog
update_changelog() {
    local version="$1"
    local type="$2"
    local feature_title="$3"
    local feature_desc="$4"

    info "更新 CHANGELOG.md"

    # 构建变更条目
    local changelog_entry="## [${version}] - $(date '+%Y-%m-%d')

### ${type^}

- **${feature_title}** ${feature_desc}"

    # 检查是否是 Unreleased 部分
    if grep -q "## \[Unreleased\]" "$CHANGELOG"; then
        # 在 Unreleased 之后插入新版本
        local temp_file
        temp_file=$(mktemp)
        local marker_found=false
        while IFS= read -r line; do
            echo "$line" >> "$temp_file"
            if [ "$marker_found" = false ] && echo "$line" | grep -q "## \[Unreleased\]"; then
                marker_found=true
                # 跳过 "## [Unreleased]" 之后的分隔线
                IFS= read -r next_line
                echo "$next_line" >> "$temp_file"
                echo "" >> "$temp_file"
                echo "$changelog_entry" >> "$temp_file"
            fi
        done < "$CHANGELOG"
        mv "$temp_file" "$CHANGELOG"
    else
        # 直接在顶部插入
        local temp_file
        temp_file=$(mktemp)
        echo "$changelog_entry" >> "$temp_file"
        echo "" >> "$temp_file"
        cat "$CHANGELOG" >> "$temp_file"
        mv "$temp_file" "$CHANGELOG"
    fi

    success "CHANGELOG.md 已更新"
}

# ═══════════════════════════════════════════════════════════════
# Git Commit
# ═══════════════════════════════════════════════════════════════

# 创建 commit
create_commit() {
    local type="$1"
    local scope="$2"
    local description="$3"
    local files="$4"
    local doc_file="$5"

    local scope_part=""
    if [ -n "$scope" ]; then
        scope_part="($scope)"
    fi

    local commit_msg="${type}${scope_part}: ${description}"

    info "Git commit: $commit_msg"

    if [ -n "$files" ]; then
        git add "$files" "$doc_file"
    else
        git add -A
    fi

    git commit -m "$commit_msg" -m "
Co-Authored-By: $(git config user.name) <$(git config user.email)>

自动生成 by post-feature.sh"
}

# ═══════════════════════════════════════════════════════════════
# 主流程
# ═══════════════════════════════════════════════════════════════

main() {
    echo ""
    echo -e "${BOLD}${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BOLD}${CYAN}║          FreeKiosk 功能发布工作流                          ║${NC}"
    echo -e "${BOLD}${CYAN}║          Feature Release Workflow                           ║${NC}"
    echo -e "${BOLD}${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
    echo ""

    # 前置检查
    check_git
    check_clean

    local branch
    branch=$(get_branch)
    info "当前分支: $branch"
    info "当前版本: $(get_version)"
    echo ""

    # ═══════════════════════════════════════════════════════════
    # Step 1: 基本信息
    # ═══════════════════════════════════════════════════════════
    section "Step 1/5 — 功能基本信息"

    echo -n -e "${BOLD}功能标题${NC} (简短描述，如：添加手动设备码输入): "
    read -r feature_title
    if [ -z "$feature_title" ]; then
        error "标题不能为空"
        exit 1
    fi

    echo -n -e "${BOLD}功能描述${NC} (详细说明变更内容): "
    read -r feature_desc
    if [ -z "$feature_desc" ]; then
        warn "描述为空，将使用标题作为描述"
        feature_desc="$feature_title"
    fi

    echo -n -e "${BOLD}影响范围${NC} (如：android / hub / 全部): "
    read -r impact
    if [ -z "$impact" ]; then
        impact="全部"
    fi

    echo ""

    # ═══════════════════════════════════════════════════════════
    # Step 2: 变更类型
    # ═══════════════════════════════════════════════════════════
    section "Step 2/5 — 变更类型"

    local default_type
    default_type=$(get_change_type)
    echo "请选择变更类型 (直接回车使用检测到的: ${default_type}):"
    echo "  1) feat     新功能"
    echo "  2) fix      Bug 修复"
    echo "  3) docs     文档更新"
    echo "  4) style    代码格式"
    echo "  5) refactor 重构"
    echo "  6) perf     性能优化"
    echo "  7) ui       UI/样式变更"
    echo "  8) api      API 变更"
    echo "  9) db       数据库变更"
    echo "  10) build   依赖/构建"
    echo "  11) ci      CI 配置"
    echo "  12) test    测试相关"
    echo -n "选择 [1-12, 默认${default_type}]: "
    read -r type_choice

    local type_map="1:feat:2:fix:3:docs:4:style:5:refactor:6:perf:7:ui:8:api:9:db:10:build:11:ci:12:test"
    local change_type="$default_type"

    case "$type_choice" in
        1) change_type="feat" ;;
        2) change_type="fix" ;;
        3) change_type="docs" ;;
        4) change_type="style" ;;
        5) change_type="refactor" ;;
        6) change_type="perf" ;;
        7) change_type="ui" ;;
        8) change_type="api" ;;
        9) change_type="db" ;;
        10) change_type="build" ;;
        11) change_type="ci" ;;
        12) change_type="test" ;;
    esac

    echo -e "${GREEN}变更类型: $change_type${NC}"
    echo ""

    # ═══════════════════════════════════════════════════════════
    # Step 3: 范围（可选）
    # ═══════════════════════════════════════════════════════════
    section "Step 3/5 — 变更范围 (可选)"

    echo -n -e "${BOLD}范围${NC} (如: android, hub, ui, api 等，直接回车跳过): "
    read -r scope
    if [ -z "$scope" ]; then
        scope=""
        info "跳过范围"
    else
        info "范围: $scope"
    fi
    echo ""

    # ═══════════════════════════════════════════════════════════
    # Step 4: 版本号递增
    # ═══════════════════════════════════════════════════════════
    section "Step 4/5 — 版本号递增"

    local current_version
    current_version=$(get_version)
    echo "当前版本: $current_version"
    echo "请选择版本递增类型:"
    echo "  1) patch  补丁版本 (bug修复, $current_version → $(echo "$current_version" | awk -F. '{print $1"."$2"."$3+1}'))"
    echo "  2) minor  次版本 (新功能, $current_version → $(echo "$current_version" | awk -F. '{print $1"."$2+1".0"}'))"
    echo "  3) major  主版本 (破坏性变更)"
    echo -n "选择 [1-3, 默认1(patch)]: "
    read -r version_choice

    local bump_type="patch"
    case "$version_choice" in
        2) bump_type="minor" ;;
        3) bump_type="major" ;;
    esac

    local new_version
    new_version=$(bump_version "$bump_type")
    echo ""

    # ═══════════════════════════════════════════════════════════
    # Step 5: 生成文档
    # ═══════════════════════════════════════════════════════════
    section "Step 5/5 — 生成文档"

    local doc_file
    doc_file=$(generate_doc "$feature_title" "$feature_desc" "$impact" "$change_type")
    echo ""

    # ═══════════════════════════════════════════════════════════
    # 更新 Changelog
    # ═══════════════════════════════════════════════════════════
    section "更新 Changelog"

    update_changelog "$new_version" "$change_type" "$feature_title" "$feature_desc"
    echo ""

    # ═══════════════════════════════════════════════════════════
    # Git Commit
    # ═══════════════════════════════════════════════════════════
    section "Git Commit"

    local scope_part=""
    [ -n "$scope" ] && scope_part="($scope)"

    create_commit "$change_type" "$scope" "$feature_title" "$doc_file"
    echo ""

    # ═══════════════════════════════════════════════════════════
    # 完成
    # ═══════════════════════════════════════════════════════════
    section "完成"

    echo -e "${GREEN}✅ 版本: $new_version${NC}"
    echo -e "${GREEN}✅ 文档: docs/knowledge/$doc_file${NC}"
    echo -e "${GREEN}✅ Commit: $change_type${scope_part}: $feature_title${NC}"
    echo ""
    echo -e "${YELLOW}下一步操作:${NC}"
    echo "  1. 运行测试: npm test"
    echo "  2. 推送代码: git push"
    echo "  3. 创建 tag: git tag v$new_version && git push origin v$new_version"
    echo ""
    echo -e "${CYAN}如需打 tag，直接运行: git tag v$new_version && git push origin v$new_version${NC}"
    echo ""
}

# ═══════════════════════════════════════════════════════════════
# 直接 commit 模式（跳过文档）
# ═══════════════════════════════════════════════════════════════

quick_commit() {
    local commit_msg="$1"

    check_git
    check_clean

    if [ -z "$commit_msg" ]; then
        error "请提供 commit message"
        exit 1
    fi

    git add -A
    git commit -m "$commit_msg" -m "Co-Authored-By: $(git config user.name) <$(git config user.email)>"
    success "已提交: $commit_msg"
}

# ═══════════════════════════════════════════════════════════════
# CLI 入口
# ═══════════════════════════════════════════════════════════════

case "${1:-}" in
    -h|--help|help)
        echo "FreeKiosk Feature Release Workflow"
        echo ""
        echo "Usage:"
        echo "  post-feature.sh          # 交互式完整工作流"
        echo "  post-feature.sh quick     # 快速 commit（需提供 -m 参数）"
        echo "  post-feature.sh -m MSG    # 直接 commit"
        echo "  post-feature.sh -h       # 显示帮助"
        echo ""
        echo "Examples:"
        echo "  post-feature.sh"
        echo "  post-feature.sh -m \"feat(android): 添加手动设备码输入\""
        ;;
    -m|--message)
        shift
        quick_commit "$1"
        ;;
    quick)
        echo "Quick mode: 请使用 -m 参数提供 commit message"
        ;;
    *)
        main
        ;;
esac
