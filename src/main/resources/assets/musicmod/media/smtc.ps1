# Musicmod media bridge for Windows.
# Reads the "now playing" session from Windows Media Controls (the same info the
# volume flyout shows) and prints one JSON line per update to stdout.
# Prefers Spotify, falls back to whatever session Windows marks as current.
# Commands (playpause / next / previous) are read from the file given in -CommandFile.
param(
    [int]$ParentPid = 0,
    [string]$CommandFile = ''
)

$ErrorActionPreference = 'SilentlyContinue'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false

Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]

function Await($op, [Type]$type) {
    if ($op -eq $null) { return $null }
    $task = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op))
    if ($task.Wait(3000)) { return $task.Result }
    return $null
}

$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
$propsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]

$manager = Await ($managerType::RequestAsync()) $managerType
if ($manager -eq $null) {
    [Console]::Out.WriteLine('{"error":"smtc-unavailable"}')
    [Console]::Out.Flush()
    exit 1
}

function Pick-Session {
    $sessions = $manager.GetSessions()
    foreach ($s in $sessions) {
        if ($s.SourceAppUserModelId -like '*Spotify*') { return $s }
    }
    return $manager.GetCurrentSession()
}

$lastProps = $null
$lastPropsKey = ''
$propsCheckedAt = [DateTime]::MinValue

while ($true) {
    if ($ParentPid -gt 0 -and -not (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue)) { exit 0 }

    $session = Pick-Session

    if ($CommandFile -ne '' -and (Test-Path $CommandFile)) {
        $cmd = (Get-Content $CommandFile -Raw).Trim()
        Remove-Item $CommandFile -Force
        if ($session -ne $null) {
            switch ($cmd) {
                'playpause' { $null = Await ($session.TryTogglePlayPauseAsync()) ([bool]) }
                'next'      { $null = Await ($session.TrySkipNextAsync()) ([bool]) }
                'previous'  { $null = Await ($session.TrySkipPreviousAsync()) ([bool]) }
            }
            Start-Sleep -Milliseconds 150
        }
    }

    if ($session -eq $null) {
        [Console]::Out.WriteLine('{"active":false}')
        [Console]::Out.Flush()
        Start-Sleep -Milliseconds 1000
        continue
    }

    $timeline = $session.GetTimelineProperties()
    $playback = $session.GetPlaybackInfo()
    $app = $session.SourceAppUserModelId

    # Media properties are slow to query: refresh at most every 750 ms.
    $now = [DateTime]::UtcNow
    if ($lastProps -eq $null -or ($now - $propsCheckedAt).TotalMilliseconds -ge 750) {
        $p = Await ($session.TryGetMediaPropertiesAsync()) $propsType
        if ($p -ne $null) { $lastProps = $p }
        $propsCheckedAt = $now
    }

    $title = ''; $artist = ''; $album = ''
    if ($lastProps -ne $null) {
        $title = [string]$lastProps.Title
        $artist = [string]$lastProps.Artist
        $album = [string]$lastProps.AlbumTitle
    }

    $data = [ordered]@{
        active     = $true
        app        = [string]$app
        title      = $title
        artist     = $artist
        album      = $album
        positionMs = [long]$timeline.Position.TotalMilliseconds
        durationMs = [long]($timeline.EndTime - $timeline.StartTime).TotalMilliseconds
        updatedMs  = [long]$timeline.LastUpdatedTime.ToUnixTimeMilliseconds()
        nowMs      = [long][DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        playing    = ([string]$playback.PlaybackStatus -eq 'Playing')
    }
    [Console]::Out.WriteLine(($data | ConvertTo-Json -Compress))
    [Console]::Out.Flush()
    Start-Sleep -Milliseconds 300
}
