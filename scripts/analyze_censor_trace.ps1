param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string[]] $Path
)

$ErrorActionPreference = 'Stop'

function Get-TraceTime([string] $line) {
    if ($line -match '^(\d\d-\d\d \d\d:\d\d:\d\d\.\d\d\d)') {
        return [datetime]::ParseExact(
            "2026-$($Matches[1])",
            'yyyy-MM-dd HH:mm:ss.fff',
            [Globalization.CultureInfo]::InvariantCulture)
    }
    if ($line -match '^\s+(\d{10}\.\d{3})\s') {
        $milliseconds = [long] ([double] $Matches[1] * 1000.0)
        return [DateTimeOffset]::FromUnixTimeMilliseconds($milliseconds).UtcDateTime
    }
    return $null
}

function Get-Distribution([object[]] $values) {
    $numbers = @($values | Where-Object { $null -ne $_ } |
            ForEach-Object { [double] $_ } | Sort-Object)
    if ($numbers.Count -eq 0) { return $null }
    function At-Percentile([double] $percentile) {
        if ($numbers.Count -eq 1) { return $numbers[0] }
        $position = ($numbers.Count - 1) * $percentile
        $lower = [math]::Floor($position)
        $upper = [math]::Ceiling($position)
        if ($lower -eq $upper) { return $numbers[$lower] }
        return $numbers[$lower] + (($numbers[$upper] - $numbers[$lower]) * ($position - $lower))
    }
    $sum = ($numbers | Measure-Object -Sum).Sum
    return [ordered]@{
        count = $numbers.Count
        min = [math]::Round($numbers[0], 2)
        p50 = [math]::Round((At-Percentile 0.50), 2)
        p90 = [math]::Round((At-Percentile 0.90), 2)
        p95 = [math]::Round((At-Percentile 0.95), 2)
        p99 = [math]::Round((At-Percentile 0.99), 2)
        max = [math]::Round($numbers[-1], 2)
        mean = [math]::Round(($sum / $numbers.Count), 2)
    }
}

function Get-GroupCounts([object[]] $items, [string] $property) {
    $result = [ordered]@{}
    foreach ($group in @($items | Group-Object -Property $property | Sort-Object Name)) {
        $result[$group.Name] = $group.Count
    }
    return $result
}

foreach ($requestedPath in $Path) {
    $resolved = (Resolve-Path -LiteralPath $requestedPath).Path
    $lines = @(Get-Content -LiteralPath $resolved)
    $firstTime = $null
    $lastTime = $null
    $publishes = [Collections.Generic.List[object]]::new()
    $quality = [Collections.Generic.List[object]]::new()
    $scrolls = [Collections.Generic.List[object]]::new()
    $textScans = [Collections.Generic.List[object]]::new()
    $textPublishes = [Collections.Generic.List[object]]::new()
    $textConfirms = [Collections.Generic.List[object]]::new()
    $fastPublishTimes = [Collections.Generic.List[datetime]]::new()
    $motionDraws = [Collections.Generic.List[object]]::new()
    $motionSettles = [Collections.Generic.List[object]]::new()

    foreach ($line in $lines) {
        $time = Get-TraceTime $line
        if ($null -ne $time) {
            if ($null -eq $firstTime) { $firstTime = $time }
            $lastTime = $time
        }

        if ($line -match 'OVERLAY_PUBLISH pass=(\S+) scrollId=(\d+) captureAgeMs=(\d+) inferenceMs=(\d+) preprocessMs=(\d+) runtimeMs=(\d+) postprocessMs=(\d+) afterMotionMs=(-?\d+) tracks=(\d+) rawVisual=(\d+) cachedQuality=(\d+) qualityOnly=(\d+)(?: identityRealtimeLinked=(\d+) identityQualityLinked=(\d+) identityFused=(\d+) identityCarriedQuality=(\d+) identityUnlinkedQuality=(\d+))? geometryMatched=(\d+) geometryChanged=(\d+) maxCenterDeltaPx=(\d+) maxSizeDeltaPx=(\d+) dropped=(\d+)(?: duplicatesSuppressed=(\d+))?(?: qualityOnlyTracks=(\d+) renderTracks=(\d+))?') {
            $item = [pscustomobject]@{
                time = $time
                pass = $Matches[1]
                scrollId = [int] $Matches[2]
                captureAge = [int] $Matches[3]
                inference = [int] $Matches[4]
                preprocess = [int] $Matches[5]
                runtime = [int] $Matches[6]
                postprocess = [int] $Matches[7]
                afterMotion = [int] $Matches[8]
                tracks = [int] $Matches[9]
                rawVisual = [int] $Matches[10]
                cachedQuality = [int] $Matches[11]
                qualityOnly = [int] $Matches[12]
                identityRealtimeLinked = if ($Matches[13]) { [int] $Matches[13] } else { 0 }
                identityQualityLinked = if ($Matches[14]) { [int] $Matches[14] } else { 0 }
                identityFused = if ($Matches[15]) { [int] $Matches[15] } else { 0 }
                identityCarriedQuality = if ($Matches[16]) { [int] $Matches[16] } else { 0 }
                identityUnlinkedQuality = if ($Matches[17]) { [int] $Matches[17] } else { 0 }
                geometryMatched = [int] $Matches[18]
                geometryChanged = [int] $Matches[19]
                maxCenterDelta = [int] $Matches[20]
                maxSizeDelta = [int] $Matches[21]
                dropped = [int] $Matches[22]
                duplicatesSuppressed = if ($Matches[23]) { [int] $Matches[23] } else { 0 }
                qualityOnlyTracks = if ($Matches[24]) { [int] $Matches[24] } else { 0 }
                renderTracks = if ($Matches[25]) { [int] $Matches[25] } else { [int] $Matches[9] }
            }
            $publishes.Add($item)
            if ($item.pass -eq 'fast' -and $null -ne $time) { $fastPublishTimes.Add($time) }
            continue
        }
        if ($line -match 'CensorMotion: DRAW .*inputToDrawMs=(\d+).*?(?:viewportLead=(-?\d+),(-?\d+))?$') {
            $leadX = if ($Matches[2]) { [int] $Matches[2] } else { 0 }
            $leadY = if ($Matches[3]) { [int] $Matches[3] } else { 0 }
            $motionDraws.Add([pscustomobject]@{
                time = $time
                inputToDraw = [int] $Matches[1]
                leadAbs = [math]::Abs($leadX) + [math]::Abs($leadY)
            })
            continue
        }
        if ($line -match 'CensorMotion: SETTLED .*inputToSettledMs=(\d+)') {
            $motionSettles.Add([pscustomobject]@{
                time = $time
                inputToSettled = [int] $Matches[1]
            })
            continue
        }
        if ($line -match 'QUALITY_CACHE scrollId=(\d+) captureAgeMs=(\d+) inferenceMs=(\d+) preprocessMs=(\d+) runtimeMs=(\d+) postprocessMs=(\d+) afterMotionMs=(-?\d+) rawVisual=(\d+) stableVisual=(\d+)(?: identityLinked=(\d+) identityUnlinked=(\d+))?(?: pendingVisual=(\d+) deferredUnlinked=(\d+) confirmationRequested=(true|false))?') {
            $quality.Add([pscustomobject]@{
                time = $time
                scrollId = [int] $Matches[1]
                captureAge = [int] $Matches[2]
                inference = [int] $Matches[3]
                preprocess = [int] $Matches[4]
                runtime = [int] $Matches[5]
                postprocess = [int] $Matches[6]
                afterMotion = [int] $Matches[7]
                rawVisual = [int] $Matches[8]
                stableVisual = [int] $Matches[9]
                identityLinked = if ($Matches[10]) { [int] $Matches[10] } else { 0 }
                identityUnlinked = if ($Matches[11]) { [int] $Matches[11] } else { 0 }
                pendingVisual = if ($Matches[12]) { [int] $Matches[12] } else { 0 }
                deferredUnlinked = if ($Matches[13]) { [int] $Matches[13] } else { 0 }
                confirmationRequested = $Matches[14] -eq 'true'
            })
            continue
        }
        if ($line -match 'SCROLL_EVENT id=(\d+) source=(\S+) gapMs=(\d+)(?: eventAgeMs=(\d+))? rawDx=(-?\d+) rawDy=(-?\d+) dx=(-?\d+) dy=(-?\d+)') {
            $rawX = [int] $Matches[5]
            $rawY = [int] $Matches[6]
            $appliedX = [int] $Matches[7]
            $appliedY = [int] $Matches[8]
            $scrolls.Add([pscustomobject]@{
                time = $time
                id = [int] $Matches[1]
                source = $Matches[2]
                gap = [int] $Matches[3]
                eventAge = if ($Matches[4]) { [int] $Matches[4] } else { $null }
                rawX = $rawX
                rawY = $rawY
                appliedX = $appliedX
                appliedY = $appliedY
                rawAbs = [math]::Abs($rawX) + [math]::Abs($rawY)
                appliedAbs = [math]::Abs($appliedX) + [math]::Abs($appliedY)
            })
            continue
        }
        if ($line -match 'TEXT_SCAN (accepted|discarded=\S+).*durationMs=(\d+)') {
            $status = $Matches[1]
            if ($status -like 'discarded=*') { $status = $status.Substring(10) }
            $textScans.Add([pscustomobject]@{
                time = $time
                status = $status
                duration = [int] $Matches[2]
                bestEffort = $line -match 'bestEffort=true'
                candidates = if ($line -match 'candidates=(\d+)') { [int] $Matches[1] } else { 0 }
            })
            continue
        }
        if ($line -match 'TEXT_CONFIRM targeted.*durationMs=(\d+)') {
            $textConfirms.Add([pscustomobject]@{ time = $time; duration = [int] $Matches[1] })
            continue
        }
        if ($line -match 'TEXT_PUBLISH source=(\S+) regions=(\d+)') {
            $textPublishes.Add([pscustomobject]@{
                time = $time
                source = $Matches[1]
                regions = [int] $Matches[2]
            })
        }
    }

    $fast = @($publishes | Where-Object pass -eq 'fast')
    $activeFast = @($fast | Where-Object { $_.scrollId -gt 0 -and $_.afterMotion -ge 0 -and $_.afterMotion -le 500 })
    $settledFast = @($fast | Where-Object { $_.scrollId -eq 0 -or $_.afterMotion -gt 500 })
    $intervals = for ($index = 1; $index -lt $fastPublishTimes.Count; $index++) {
        ($fastPublishTimes[$index] - $fastPublishTimes[$index - 1]).TotalMilliseconds
    }
    $rawAbs = ($scrolls | Measure-Object -Property rawAbs -Sum).Sum
    $appliedAbs = ($scrolls | Measure-Object -Property appliedAbs -Sum).Sum
    $rawNetX = ($scrolls | Measure-Object -Property rawX -Sum).Sum
    $rawNetY = ($scrolls | Measure-Object -Property rawY -Sum).Sum
    $appliedNetX = ($scrolls | Measure-Object -Property appliedX -Sum).Sum
    $appliedNetY = ($scrolls | Measure-Object -Property appliedY -Sum).Sum
    $amplified = @($scrolls | Where-Object { $_.appliedAbs -gt $_.rawAbs }).Count
    $amplifiedBySource = Get-GroupCounts @(
        $scrolls | Where-Object { $_.appliedAbs -gt $_.rawAbs }) 'source'
    $dropNonzero = @($fast | Where-Object dropped -gt 0).Count
    $geometryJumps = @($fast | Where-Object maxCenterDelta -ge 100).Count
    $durationSeconds = if ($null -ne $firstTime -and $null -ne $lastTime) {
        [math]::Round(($lastTime - $firstTime).TotalSeconds, 3)
    } else { 0 }

    $summary = [ordered]@{
        file = $resolved
        bytes = (Get-Item -LiteralPath $resolved).Length
        durationSeconds = $durationSeconds
        fast = [ordered]@{
            publishes = $fast.Count
            captureAgeMs = Get-Distribution @($fast.captureAge)
            inferenceMs = Get-Distribution @($fast.inference)
            preprocessMs = Get-Distribution @($fast.preprocess)
            runtimeMs = Get-Distribution @($fast.runtime)
            postprocessMs = Get-Distribution @($fast.postprocess)
            publishIntervalMs = Get-Distribution @($intervals)
            maxDropped = if ($fast.Count) { ($fast.dropped | Measure-Object -Maximum).Maximum } else { 0 }
            nonzeroDropPublishes = $dropNonzero
            geometryChangedPublishes = @($fast | Where-Object geometryChanged -gt 0).Count
            centerJumpsAtLeast100px = $geometryJumps
            duplicateSuppressions = ($fast | Measure-Object -Property duplicatesSuppressed -Sum).Sum
            duplicateSuppressionPublishes = @($fast | Where-Object duplicatesSuppressed -gt 0).Count
            qualityOnlyObservations = ($fast | Measure-Object -Property qualityOnly -Sum).Sum
            identityRealtimeLinked = ($fast | Measure-Object -Property identityRealtimeLinked -Sum).Sum
            identityQualityLinked = ($fast | Measure-Object -Property identityQualityLinked -Sum).Sum
            identityFused = ($fast | Measure-Object -Property identityFused -Sum).Sum
            identityCarriedQuality = ($fast | Measure-Object -Property identityCarriedQuality -Sum).Sum
            identityUnlinkedQuality = ($fast | Measure-Object -Property identityUnlinkedQuality -Sum).Sum
            qualityOnlyTracks = Get-Distribution @($fast.qualityOnlyTracks)
            renderTracks = Get-Distribution @($fast.renderTracks)
        }
        activeScrollFast = [ordered]@{
            publishes = $activeFast.Count
            captureAgeMs = Get-Distribution @($activeFast.captureAge)
            inferenceMs = Get-Distribution @($activeFast.inference)
            runtimeMs = Get-Distribution @($activeFast.runtime)
        }
        settledFast = [ordered]@{
            publishes = $settledFast.Count
            captureAgeMs = Get-Distribution @($settledFast.captureAge)
            inferenceMs = Get-Distribution @($settledFast.inference)
            runtimeMs = Get-Distribution @($settledFast.runtime)
        }
        quality = [ordered]@{
            caches = $quality.Count
            captureAgeMs = Get-Distribution @($quality.captureAge)
            inferenceMs = Get-Distribution @($quality.inference)
            runtimeMs = Get-Distribution @($quality.runtime)
            skipFastPending = @($lines | Where-Object { $_ -match 'QUALITY_SKIP reason=fast-pending|fast-arrived-' }).Count
            sourceFrameFallbacks = @($lines | Where-Object { $_ -match 'SOURCE_FRAME_PUBLISH' }).Count
            identityLinked = ($quality | Measure-Object -Property identityLinked -Sum).Sum
            identityUnlinked = ($quality | Measure-Object -Property identityUnlinked -Sum).Sum
            pendingVisualCandidates = ($quality | Measure-Object -Property pendingVisual -Sum).Sum
            deferredUnlinkedCoverage = ($quality | Measure-Object -Property deferredUnlinked -Sum).Sum
            confirmationRequests = @($quality | Where-Object confirmationRequested).Count
            confirmationRuns = @($lines | Where-Object {
                    $_ -match 'QUALITY_CONFIRMATION_RUN' }).Count
            cacheInvalidationsOnMotion = @($lines | Where-Object {
                    $_ -match 'QUALITY_CACHE_INVALIDATED reason=motion' }).Count
            cacheGenerationRejections = @($lines | Where-Object {
                    $_ -match 'QUALITY_CACHE_REJECTED reason=motion-generation' }).Count
        }
        scroll = [ordered]@{
            sessions = @($scrolls.id | Sort-Object -Unique).Count
            events = $scrolls.Count
            sources = Get-GroupCounts @($scrolls) 'source'
            eventAgeMs = Get-Distribution @($scrolls.eventAge)
            rawAbs = $rawAbs
            appliedAbs = $appliedAbs
            appliedToRawRatio = if ($rawAbs) { [math]::Round($appliedAbs / $rawAbs, 4) } else { 0 }
            rawNet = @($rawNetX, $rawNetY)
            appliedNet = @($appliedNetX, $appliedNetY)
            amplifiedEvents = $amplified
            amplifiedBySource = $amplifiedBySource
        }
        displayMotion = [ordered]@{
            draws = $motionDraws.Count
            inputToDrawMs = Get-Distribution @($motionDraws.inputToDraw)
            viewportLeadAbsPx = Get-Distribution @($motionDraws.leadAbs)
            settles = $motionSettles.Count
            inputToSettledMs = Get-Distribution @($motionSettles.inputToSettled)
        }
        text = [ordered]@{
            scans = $textScans.Count
            scanStatuses = Get-GroupCounts @($textScans) 'status'
            acceptedDurationMs = Get-Distribution @($textScans | Where-Object status -eq 'accepted' | Select-Object -ExpandProperty duration)
            bestEffortAccepted = @($textScans | Where-Object { $_.status -eq 'accepted' -and $_.bestEffort }).Count
            confirms = $textConfirms.Count
            confirmDurationMs = Get-Distribution @($textConfirms.duration)
            publishes = $textPublishes.Count
            publishSources = Get-GroupCounts @($textPublishes) 'source'
            zeroRegionPublishes = @($textPublishes | Where-Object regions -eq 0).Count
        }
    }
    $summary | ConvertTo-Json -Depth 8
}
