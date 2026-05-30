# deploy.ps1 - Automated deploy of marketplace to Rancher Desktop
#
# Usage (can be run from anywhere — paths resolve via $PSScriptRoot):
#   .\infra\rancher\deploy.ps1              - full cycle: build + deploy
#   .\infra\rancher\deploy.ps1 -BuildOnly   - build and load image into VM only
#   .\infra\rancher\deploy.ps1 -DeployOnly  - apply manifests only (image already loaded)
#   .\infra\rancher\deploy.ps1 -Reset       - delete namespace and redeploy from scratch
#   .\infra\rancher\deploy.ps1 -Token       - show Kubernetes Dashboard token

param(
    [switch]$BuildOnly,
    [switch]$DeployOnly,
    [switch]$Reset,
    [switch]$Token
)

$ImageName    = "marketplace-app"
$ImageTag     = "1.0.0"
$FullImage    = "${ImageName}:${ImageTag}"
$TarPath      = "$env:TEMP\${ImageName}.tar"
$LinuxTarPath = "/mnt/c/Users/$env:USERNAME/AppData/Local/Temp/${ImageName}.tar"
$Namespace    = "marketplace"
# Пути относительно расположения скрипта (infra/rancher) — не зависят от CWD.
$ProjectRoot  = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path  # корень проекта (где Dockerfile)
$K8sDir       = Join-Path $PSScriptRoot "k8s"

function Write-Step($msg) {
    Write-Host ""
    Write-Host "== $msg" -ForegroundColor Cyan
}

function Write-Ok($msg) {
    Write-Host "  OK: $msg" -ForegroundColor Green
}

function Write-Fail($msg) {
    Write-Host "  FAIL: $msg" -ForegroundColor Red
    exit 1
}

# --token: show Dashboard login token
if ($Token) {
    Write-Step "Kubernetes Dashboard token"
    $b64 = kubectl -n kubernetes-dashboard get secret admin-user-token -o jsonpath='{.data.token}' 2>$null
    if (-not $b64) {
        Write-Host "  Dashboard not deployed yet. Run deploy first." -ForegroundColor Yellow
        exit 0
    }
    $decoded = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($b64))
    Write-Host ""
    Write-Host $decoded -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  Open: https://localhost:30443  -> select Token -> paste." -ForegroundColor Gray
    exit 0
}

# --reset: delete namespaces first, then do full deploy
if ($Reset) {
    Write-Step "Reset: deleting namespace $Namespace"
    kubectl delete namespace $Namespace --ignore-not-found
    kubectl delete namespace kubernetes-dashboard --ignore-not-found
    kubectl delete clusterrolebinding admin-user --ignore-not-found 2>$null
    Write-Ok "Namespaces deleted. Continuing with fresh deploy..."
    $BuildOnly  = $false
    $DeployOnly = $false
}

# STAGE 1: Build and load image
if (-not $DeployOnly) {
    Write-Step "Building Docker image: $FullImage"
    docker build --provenance=false -t $FullImage $ProjectRoot
    if ($LASTEXITCODE -ne 0) { Write-Fail "docker build failed" }
    Write-Ok "Image built"

    Write-Step "Saving image to file: $TarPath"
    docker save $FullImage -o $TarPath
    if ($LASTEXITCODE -ne 0) { Write-Fail "docker save failed" }
    Write-Ok "Saved: $TarPath"

    Write-Step "Loading image into Rancher Desktop VM"
    # Use -i flag instead of stdin redirection (< is a PS operator)
    rdctl shell -- docker load -i $LinuxTarPath
    if ($LASTEXITCODE -ne 0) { Write-Fail "rdctl docker load failed" }
    Write-Ok "Image loaded into VM"

    Write-Host "  Verifying image in VM:" -ForegroundColor Gray
    rdctl shell -- docker images $ImageName
}

if ($BuildOnly) {
    Write-Host ""
    Write-Host "  Done: image loaded. To deploy run: .\infra\rancher\deploy.ps1 --deploy-only" -ForegroundColor Green
    exit 0
}

# STAGE 2: Apply manifests
# Dashboard has immutable fields (selector, roleRef) - delete namespace first to avoid conflicts
Write-Step "Recreating kubernetes-dashboard namespace (avoids immutable field conflicts)"
kubectl delete namespace kubernetes-dashboard --ignore-not-found
kubectl delete clusterrolebinding kubernetes-dashboard --ignore-not-found 2>$null
kubectl delete clusterrolebinding admin-user --ignore-not-found 2>$null

Write-Step "Applying K8s manifests: $K8sDir"
kubectl apply -f $K8sDir
if ($LASTEXITCODE -ne 0) { Write-Fail "kubectl apply failed" }
Write-Ok "Manifests applied"

# STAGE 3: Wait for rollouts
Write-Step "Waiting for PostgreSQL (120s)"
kubectl rollout status deployment/postgres -n $Namespace --timeout=120s
if ($LASTEXITCODE -ne 0) { Write-Fail "PostgreSQL did not become ready" }
Write-Ok "PostgreSQL ready"

Write-Step "Waiting for Loki (120s)"
kubectl rollout status deployment/loki -n $Namespace --timeout=120s
if ($LASTEXITCODE -ne 0) { Write-Fail "Loki did not become ready" }
Write-Ok "Loki ready"

Write-Step "Waiting for marketplace-app (3 min)"
kubectl rollout status deployment/marketplace-app -n $Namespace --timeout=180s
if ($LASTEXITCODE -ne 0) { Write-Fail "marketplace-app did not become ready" }
Write-Ok "Spring Boot ready"

Write-Step "Waiting for Kubernetes Dashboard (120s)"
kubectl rollout status deployment/kubernetes-dashboard -n kubernetes-dashboard --timeout=120s
if ($LASTEXITCODE -ne 0) {
    Write-Host "  WARNING: Dashboard not ready (non-critical)" -ForegroundColor Yellow
} else {
    Write-Ok "Dashboard ready"
}

Write-Step "Waiting for OpenSearch Dashboards (180s)"
kubectl rollout status deployment/opensearch-dashboards -n $Namespace --timeout=180s
if ($LASTEXITCODE -ne 0) {
    Write-Host "  WARNING: OpenSearch Dashboards not ready (non-critical)" -ForegroundColor Yellow
} else {
    Write-Ok "OpenSearch Dashboards ready"
}

# Summary
Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  DEPLOY COMPLETE" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "  App:                   http://localhost:30667/login.html" -ForegroundColor White
Write-Host "  OpenSearch Dashboards: http://localhost:30601" -ForegroundColor Cyan
Write-Host "  Grafana:               http://localhost:30300  (admin / admin)" -ForegroundColor White
Write-Host "  Prometheus:            http://localhost:30900" -ForegroundColor White
Write-Host "  K8s Dashboard:         https://localhost:30443" -ForegroundColor White
Write-Host ""
Write-Host "  Dashboard token: .\infra\rancher\deploy.ps1 --token" -ForegroundColor Gray
Write-Host "  Pod status:      kubectl get pods -n marketplace" -ForegroundColor Gray
Write-Host ""
