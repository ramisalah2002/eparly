# Lance le batch a l'ouverture de session, sans privileges : tache planifiee si Windows l'accepte, sinon cle Run (HKCU).
$script = Join-Path $PSScriptRoot 'start-local-batch.ps1'
$arguments = "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$script`""
try {
    $action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arguments
    $trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
    $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
        -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew
    Register-ScheduledTask -TaskName 'EparlyCatalogBatch' -Action $action -Trigger $trigger -Settings $settings `
        -Force -ErrorAction Stop | Out-Null
    Write-Output 'INSTALLED=ScheduledTask:EparlyCatalogBatch'
} catch {
    Set-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Run' -Name 'EparlyCatalogBatch' `
        -Value "powershell.exe $arguments"
    Write-Output 'INSTALLED=HKCU-Run:EparlyCatalogBatch'
}
