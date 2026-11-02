# Demarre Docker/PostgreSQL puis le backend (profil local) depuis un JAR autonome ; le scheduler vit dans ce backend.
# Une seule instance : mutex de session et controle du port. Usage : .\ops\start-local-batch.ps1 [-Rebuild]
param([switch]$Rebuild)
$ErrorActionPreference = 'Continue'
$apiDir = Split-Path $PSScriptRoot -Parent
$repoDir = Split-Path (Split-Path $apiDir -Parent) -Parent
$home_ = Join-Path $env:LOCALAPPDATA 'Eparly'
$logDir = Join-Path $home_ 'logs'
$jar = Join-Path $home_ 'api.jar'
$stopFlag = Join-Path $home_ 'stop.flag'
New-Item -ItemType Directory -Force $logDir | Out-Null
function Log($message) { "$(Get-Date -Format s) $message" | Add-Content (Join-Path $logDir 'launcher.log') }

$mutex = New-Object System.Threading.Mutex($false, 'Local\EparlyCatalogBatch')
if (-not $mutex.WaitOne(0)) { Log 'Instance deja lancee : sortie.'; exit 0 }
Remove-Item $stopFlag -ErrorAction SilentlyContinue
Set-Location $apiDir

# Docker Desktop peut mettre plusieurs minutes a demarrer apres l'ouverture de session.
$desktop = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
docker info *> $null
if ($LASTEXITCODE -ne 0 -and (Test-Path $desktop)) { Start-Process $desktop }
for ($i = 0; $i -lt 60; $i++) {
    docker info *> $null
    if ($LASTEXITCODE -eq 0) { break }
    Start-Sleep -Seconds 10
}
docker compose -f (Join-Path $repoDir 'infra\compose.yml') --env-file (Join-Path $repoDir 'infra\.env') --profile local up -d --wait postgres *>> (Join-Path $logDir 'launcher.log')

if ($Rebuild -or -not (Test-Path $jar)) {
    & .\mvnw.cmd -B -q -DskipTests package *>> (Join-Path $logDir 'launcher.log')
    $built = Get-ChildItem target\api-*.jar | Where-Object Name -NotLike '*original*' | Select-Object -First 1
    if ($built) { Copy-Item $built.FullName $jar -Force }
}
if (-not (Test-Path $jar)) { Log 'JAR introuvable : arret.'; exit 1 }

while (-not (Test-Path $stopFlag)) {
    if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
        Log 'Port 8080 deja utilise : aucune seconde instance, nouvel essai dans 60 s.'
        Start-Sleep -Seconds 60
        continue
    }
    Log 'Demarrage du backend.'
    $log = Join-Path $logDir ("api-{0}.log" -f (Get-Date -Format yyyy-MM-dd))
    # Le repertoire courant reste services/api : application-local lit ../../infra/.env et ../../infra/keys.
    & java -jar $jar --spring.profiles.active=local "--eparly.local.shutdown-file=$stopFlag" *>> $log
    Log "Backend arrete (code $LASTEXITCODE)."
    if (-not (Test-Path $stopFlag)) { Start-Sleep -Seconds 30 }
}
Remove-Item $stopFlag -ErrorAction SilentlyContinue
$mutex.ReleaseMutex()
