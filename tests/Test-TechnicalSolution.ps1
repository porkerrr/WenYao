$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$documentPath = Join-Path $repoRoot 'TECHNICAL_SOLUTION.md'
$productDesignPath = Join-Path $repoRoot 'AI 六爻 Android App MVP 产品设计方案.md'
$professionalSolutionPath = Join-Path $repoRoot 'docs\PROFESSIONAL_PAIPAN_SOLUTION.md'
$experimentalPlanPath = Join-Path $repoRoot 'docs\EXPERIMENTAL_PROFESSIONAL_CHART_PLAN.md'
$rulesetPath = Join-Path $repoRoot 'docs\ruleset\RULESET.md'
$testPlanPath = Join-Path $repoRoot 'docs\ruleset\TESTPLAN.md'
$sourceRegistryPath = Join-Path $repoRoot 'docs\ruleset\SOURCE_REGISTRY.md'
$aiIntegrationPlanPath = Join-Path $repoRoot 'docs\AI_ANALYSIS_INTEGRATION_PLAN.md'

if (-not (Test-Path -LiteralPath $documentPath -PathType Leaf)) {
    throw 'TECHNICAL_SOLUTION.md does not exist.'
}

if (-not (Test-Path -LiteralPath $productDesignPath -PathType Leaf)) {
    throw 'The product design source document does not exist in the repository.'
}

if (-not (Test-Path -LiteralPath $professionalSolutionPath -PathType Leaf)) {
    throw 'The professional paipan solution does not exist.'
}

if (-not (Test-Path -LiteralPath $experimentalPlanPath -PathType Leaf)) {
    throw 'The experimental professional chart plan does not exist.'
}

if (-not (Test-Path -LiteralPath $aiIntegrationPlanPath -PathType Leaf)) {
    throw 'The AI analysis integration plan does not exist.'
}

$aiIntegrationPlan = Get-Content -LiteralPath $aiIntegrationPlanPath -Raw -Encoding UTF8
$requiredAiIntegrationStatements = @(
    '## 2. 已核实事实、推理与风险',
    'BYOK（Bring Your Own Key，用户自备 API Key）',
    'Android 直接调用供应商官方 API',
    'ProviderAuth.OAUTH_PKCE',
    '不得使用 WebView 抓 Cookie',
    'Android Keystore',
    'AES-256-GCM',
    'noBackupFilesDir',
    'basis_refs',
    'limitations_note',
    'FastAPI 不部署',
    '## 11. 产品负责人必须先选择',
    '## 13. Definition of Done'
)

foreach ($statement in $requiredAiIntegrationStatements) {
    if (-not $aiIntegrationPlan.Contains($statement)) {
        throw "AI analysis integration plan is missing required statement: $statement"
    }
}

if ($aiIntegrationPlan -match '(?im)^\s*(api[_ -]?key|token|password)\s*[:=]\s*[^<\s]') {
    throw 'The AI integration plan appears to contain a literal secret.'
}

$experimentalPlan = Get-Content -LiteralPath $experimentalPlanPath -Raw -Encoding UTF8
$requiredExperimentalStatements = @(
    '实验实现门',
    '正式发布门',
    'SOURCE_REGISTRY.md',
    'RULESET.md',
    'TESTPLAN.md',
    'Instant + ZoneId + CalendarConvention',
    '64 × 6 = 384',
    '专家黄金案例 >= 20',
    'UNEXPLAINED_DIVERGENCE = 0',
    '禁止多数投票'
)

foreach ($statement in $requiredExperimentalStatements) {
    if (-not $experimentalPlan.Contains($statement)) {
        throw "Experimental professional chart plan is missing required statement: $statement"
    }
}

foreach ($rulesetDocument in @($rulesetPath, $testPlanPath, $sourceRegistryPath)) {
    if (-not (Test-Path -LiteralPath $rulesetDocument -PathType Leaf)) {
        throw "Missing ruleset document: $rulesetDocument"
    }
}

$ruleset = Get-Content -LiteralPath $rulesetPath -Raw -Encoding UTF8
$testPlan = Get-Content -LiteralPath $testPlanPath -Raw -Encoding UTF8
$sourceRegistry = Get-Content -LiteralPath $sourceRegistryPath -Raw -Encoding UTF8

foreach ($statement in @('verification_status: EXPERIMENTAL', 'R-PALACE-001', 'R-NAJIA-001', 'R-CALENDAR-001', 'getDayInGanZhiExact2', 'TBD')) {
    if (-not $ruleset.Contains($statement)) {
        throw "Ruleset is missing required statement: $statement"
    }
}

foreach ($statement in @('T-PALACE-001', 'T-NAJIA-002', 'T-LIUSHEN-001', 'T-XUNKONG-001', '禁止多数投票', '专家黄金案例 >= 20')) {
    if (-not $testPlan.Contains($statement)) {
        throw "Test plan is missing required statement: $statement"
    }
}

foreach ($statement in @('1a5a78b513c0e7b46dfb29f74043bcf978c32a3e', '9cf119169d7eb8e48febc05274aebf3f7106d647', '0194eb4574f33ab056fe7cac62a9d8bf24272478', 'REJECTED_FOR_COPYING')) {
    if (-not $sourceRegistry.Contains($statement)) {
        throw "Source registry is missing required statement: $statement"
    }
}

$professionalSolution = Get-Content -LiteralPath $professionalSolutionPath -Raw -Encoding UTF8
$requiredProfessionalStatements = @(
    'VerificationStatus',
    'NOT_AVAILABLE',
    'EXPERIMENTAL',
    'VERIFIED',
    'RULESET.md 已批准',
    '专家黄金案例 >= 20',
    'UNEXPLAINED_DIVERGENCE = 0',
    '所有未确认决定保持 `TBD`'
)

foreach ($statement in $requiredProfessionalStatements) {
    if (-not $professionalSolution.Contains($statement)) {
        throw "Professional paipan solution is missing required statement: $statement"
    }
}

$productDesign = Get-Content -LiteralPath $productDesignPath -Raw -Encoding UTF8
$requiredProductSections = @(
    '# AI 六爻 Android App MVP 产品设计方案',
    '# 2. MVP 核心目标',
    '# 29. MVP 验收标准'
)

foreach ($section in $requiredProductSections) {
    if (-not $productDesign.Contains($section)) {
        throw "Product design document is missing required section: $section"
    }
}

$content = Get-Content -LiteralPath $documentPath -Raw -Encoding UTF8
$lines = Get-Content -LiteralPath $documentPath -Encoding UTF8

for ($lineNumber = 0; $lineNumber -lt $lines.Count; $lineNumber++) {
    if ($lines[$lineNumber] -match '[ \t]+$') {
        throw "Trailing whitespace at TECHNICAL_SOLUTION.md:$($lineNumber + 1)."
    }
}

if ($content -match '(\r?\n){2}$') {
    throw 'TECHNICAL_SOLUTION.md has more than one newline at EOF.'
}

$requiredSections = @(
    '## 1. 结论先行',
    '## 3. 总体架构',
    '## 5. 六爻引擎',
    '## 6. AI 服务',
    '## 7. 安全、隐私与合规',
    '## 10. 迭代计划与工作量',
    '## 12. 测试与验收',
    '## 14. 待确认决策',
    '## 16. Definition of Done'
)

foreach ($section in $requiredSections) {
    if (-not $content.Contains($section)) {
        throw "Missing required section: $section"
    }
}

$requiredSafetyStatements = @(
    'AI 不计算本卦、变卦、纳甲、六亲、世应或历法',
    'App 只访问自有 HTTPS API，不内置平台主密钥',
    '结果仅供传统文化研究与娱乐参考',
    '领域顾问黄金用例 100% 通过'
)

foreach ($statement in $requiredSafetyStatements) {
    if (-not $content.Contains($statement)) {
        throw "Missing required safety or acceptance statement: $statement"
    }
}

if ($content -match '(?im)^\s*(api[_ -]?key|token|password)\s*[:=]\s*[^<\s]') {
    throw 'The technical solution appears to contain a literal secret.'
}

$officialLinks = [regex]::Matches($content, 'https://(?:developer\.android\.com|platform\.openai\.com|fastapi\.tiangolo\.com)/')
if ($officialLinks.Count -lt 6) {
    throw "Expected at least 6 official technical source links, found $($officialLinks.Count)."
}

Write-Host 'Technical solution validation passed.'
