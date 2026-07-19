param(
    [string]$PythonCommand = "python",
    [string]$OverpassEndpoint = "https://overpass-api.de/api/interpreter",
    [string]$ApiBaseUrl = "http://localhost:8080",
    [switch]$SkipOverture,
    [switch]$Import
)

$ErrorActionPreference = "Stop"

$workspacePath = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$dataPath = [System.IO.Path]::GetFullPath((Join-Path $workspacePath "data"))
$toolPath = [System.IO.Path]::GetFullPath((Join-Path $workspacePath ".tools\overture"))

if (-not $dataPath.StartsWith($workspacePath, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Resolved data path is outside the workspace: $dataPath"
}

New-Item -ItemType Directory -Path $dataPath -Force | Out-Null
New-Item -ItemType Directory -Path (Split-Path $toolPath -Parent) -Force | Out-Null

$overtureFile = Join-Path $dataPath "overture-hanoi.geojson"
$osmFile = Join-Path $dataPath "osm-hanoi.json"
$overtureTemp = Join-Path $dataPath ("overture-hanoi.{0}.tmp.geojson" -f [guid]::NewGuid())
$osmTemp = Join-Path $dataPath ("osm-hanoi.{0}.tmp.json" -f [guid]::NewGuid())
$overtureState = "$overtureTemp.state"
$venvPython = Join-Path $toolPath "Scripts\python.exe"
$overtureCommand = Join-Path $toolPath "Scripts\overturemaps.exe"

try {
    if (-not $SkipOverture) {
        if (-not (Test-Path -LiteralPath $venvPython)) {
            & $PythonCommand -m venv $toolPath
            if ($LASTEXITCODE -ne 0) {
                throw "Could not create the Overture Python environment"
            }
        }

        if (-not (Test-Path -LiteralPath $overtureCommand)) {
            & $venvPython -m pip install --disable-pip-version-check overturemaps
            if ($LASTEXITCODE -ne 0) {
                throw "Could not install the official overturemaps client"
            }
        }

        # Overture bbox format: west,south,east,north.
        & $overtureCommand download `
            --bbox=105.60,20.85,106.00,21.18 `
            -t place `
            -f geojson `
            -o $overtureTemp
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $overtureTemp)) {
            throw "Overture download failed"
        }
        Move-Item -LiteralPath $overtureTemp -Destination $overtureFile -Force
        Write-Host "Downloaded Overture data to $overtureFile"
    }

    # Overpass bbox format: south,west,north,east. Only PRD categories are requested.
    $overpassQuery = @"
[out:json][timeout:240];
(
  nwr["amenity"~"^(restaurant|cafe|fast_food|food_court|bar|pub|biergarten|cinema|theatre|arts_centre|nightclub|music_venue|casino)$"](20.85,105.60,21.18,106.00);
  nwr["shop"](20.85,105.60,21.18,106.00);
  nwr["tourism"~"^(attraction|museum|gallery|theme_park|zoo|aquarium)$"](20.85,105.60,21.18,106.00);
  nwr["leisure"~"^(amusement_arcade|bowling_alley|water_park|sports_centre|stadium)$"](20.85,105.60,21.18,106.00);
);
out center tags;
"@

    Invoke-WebRequest `
        -Uri $OverpassEndpoint `
        -Method Post `
        -Headers @{ Accept = "application/json"; "User-Agent" = "HadilaoBackend/1.0 open-data-sync" } `
        -ContentType "application/x-www-form-urlencoded" `
        -Body @{ data = $overpassQuery } `
        -OutFile $osmTemp

    if (-not (Test-Path -LiteralPath $osmTemp)) {
        throw "OSM download failed"
    }

    Move-Item -LiteralPath $osmTemp -Destination $osmFile -Force

    Write-Host "Downloaded OSM data to $osmFile"

    if ($Import) {
        $importPath = if ($SkipOverture) { "/api/v1/places/import/osm" } else { "/api/v1/places/import/open-data" }
        $result = Invoke-RestMethod `
            -Uri "$ApiBaseUrl$importPath" `
            -Method Post
        $result | ConvertTo-Json -Depth 10
    }
} finally {
    if (Test-Path -LiteralPath $overtureTemp) {
        Remove-Item -LiteralPath $overtureTemp -Force
    }
    if (Test-Path -LiteralPath $osmTemp) {
        Remove-Item -LiteralPath $osmTemp -Force
    }
    if (Test-Path -LiteralPath $overtureState) {
        Remove-Item -LiteralPath $overtureState -Force
    }
}
