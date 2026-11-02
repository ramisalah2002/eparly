# Arret propre : depose le fichier surveille par le backend et le lanceur, attend la fin normale.
# Stop-Process seulement en dernier recours, si le backend ne s'arrete pas dans le delai.
param([int]$TimeoutSeconds = 90)
$flag = Join-Path $env:LOCALAPPDATA 'Eparly\stop.flag'
New-Item -ItemType File -Force $flag | Out-Null
function Backend { Get-CimInstance Win32_Process -Filter "Name like 'java%'" | Where-Object { $_.CommandLine -like '*Eparly\api.jar*' } }
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
while ((Backend) -and (Get-Date) -lt $deadline) { Start-Sleep -Seconds 2 }
$remaining = Backend
if ($remaining) {
    Write-Output 'STOP=FORCED'
    $remaining | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
} else {
    Write-Output 'STOP=GRACEFUL'
}
