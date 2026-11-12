# Musicmod media bridge for Windows.
# Reads the "now playing" session from Windows Media Controls (the same info the
# volume flyout shows) and prints one JSON line per update to stdout.
# Prefers Spotify, falls back to whatever session Windows marks as current.
# Commands (playpause / next / previous) are read from the file given in -CommandFile.
param(
    [int]$ParentPid = 0,
    [string]$CommandFile = '',
    [string]$CoverDir = ''
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
$null = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
$streamType = [Windows.Storage.Streams.IRandomAccessStreamWithContentType]

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

# Album cover: saved to a file whenever the track changes. The player often
# publishes the picture a moment after the title, so a few polls may be needed.
$coverKey = ''
$coverFile = ''
$coverTries = 0
$coverIndex = 0

function Write-Log($text) {
    [Console]::Out.WriteLine((@{ log = [string]$text } | ConvertTo-Json -Compress))
    [Console]::Out.Flush()
}

function Save-Cover($props) {
    if ($CoverDir -eq '' -or $props -eq $null) { return '' }
    $thumb = $props.Thumbnail
    if ($thumb -eq $null) { return '' }
    $script:coverIndex = ($script:coverIndex + 1) % 4
    $file = Join-Path $CoverDir ("cover_" + $script:coverIndex + ".img")
    $ras = $null
    try {
        $ras = Await ($thumb.OpenReadAsync()) $streamType
    } catch {
        Write-Log ("cover: open failed: " + $_.Exception.Message)
        return ''
    }
    if ($ras -eq $null) { return '' }
    # First way: wrap the WinRT stream as a .NET stream.
    try {
        $net = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($ras)
        $fs = [System.IO.File]::Create($file)
        $net.CopyTo($fs)
        $fs.Close()
        $net.Close()
        if ((Get-Item $file).Length -gt 0) { return $file }
    } catch {
        Write-Log ("cover: stream copy failed: " + $_.Exception.Message)
    }
    # Second way: read the bytes with a DataReader.
    try {
        $null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]
        $size = [uint32]$ras.Size
        $reader = New-Object Windows.Storage.Streams.DataReader($ras.GetInputStreamAt(0))
        $null = Await ($reader.LoadAsync($size)) ([uint32])
        $bytes = New-Object byte[] $size
        $reader.ReadBytes($bytes)
        [System.IO.File]::WriteAllBytes($file, $bytes)
        if ($size -gt 0) { return $file }
    } catch {
        Write-Log ("cover: data reader failed: " + $_.Exception.Message)
    }
    return ''
}

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

    $key = "$artist|$title"
    if ($key -ne $coverKey) {
        $coverKey = $key
        $coverFile = ''
        $coverTries = 0
    }
    if ($coverFile -eq '' -and $coverTries -lt 8 -and $title -ne '') {
        $coverTries++
        $coverFile = Save-Cover $lastProps
    }

    $data = [ordered]@{
        active     = $true
        app        = [string]$app
        title      = $title
        artist     = $artist
        album      = $album
        cover      = [string]$coverFile
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
