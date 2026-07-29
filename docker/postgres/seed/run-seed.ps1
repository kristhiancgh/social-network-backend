# =============================================================================
#  run-seed.ps1 - equivalente de run-seed.sh para PowerShell (Windows)
# =============================================================================
#  Carga el conjunto de datos de prueba en las cuatro bases.
#
#  Existe porque run-seed.sh necesita bash: desde PowerShell o CMD no se puede
#  ejecutar. Quien use Git Bash o WSL puede seguir con el .sh, que es idéntico.
#
#  Los scripts NO se colocan en /docker-entrypoint-initdb.d a propósito: initdb
#  corre antes de que ningún servicio haya arrancado, así que las tablas que crea
#  Flyway todavía no existirían. Ejecuta esto solo con los servicios ya levantados.
#
#  Uso:
#     .\run-seed.ps1
#     .\run-seed.ps1 -PgHost 192.168.1.10 -PgPort 5433
#
#  El mismo sembrado ocurre solo al arrancar cada servicio cuando
#  APP_SEEDER_ENABLED=true. Este script sirve para recargar los datos sin
#  reiniciar los contenedores.
# =============================================================================

[CmdletBinding()]
param(
    [string]$PgHost = $(if ($env:PGHOST) { $env:PGHOST } else { 'localhost' }),
    [int]   $PgPort = $(if ($env:PGPORT) { [int]$env:PGPORT } else { 5432 })
)

# Detiene el script en el primer error en lugar de seguir con las bases restantes
# y dejar el conjunto de datos a medias.
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

function Invoke-Seed {
    param(
        [string]$Database,
        [string]$User,
        [string]$Password,
        [string]$File
    )

    Write-Host ''
    Write-Host "=== sembrando $Database como $User ===" -ForegroundColor Cyan

    $path = Join-Path $scriptDir $File
    if (-not (Test-Path $path)) {
        throw "No se encuentra $path"
    }

    # psql lee la contraseña de PGPASSWORD, lo que evita el prompt interactivo
    # y que la credencial quede en el historial de la consola.
    $env:PGPASSWORD = $Password
    try {
        # ON_ERROR_STOP=1 hace que psql devuelva un código distinto de cero ante
        # el primer fallo; sin él termina en 0 aunque el SQL haya reventado.
        & psql --host=$PgHost --port=$PgPort --username=$User --dbname=$Database `
               --set=ON_ERROR_STOP=1 --file=$path

        if ($LASTEXITCODE -ne 0) {
            throw "psql devolvió $LASTEXITCODE al sembrar $Database"
        }
    }
    finally {
        Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    }
}

if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
    Write-Error @'
No se encuentra psql en el PATH.

Opciones:
  1. Instalar el cliente de PostgreSQL:  winget install PostgreSQL.PostgreSQL
  2. O usar el psql que ya viene en el contenedor, sin instalar nada:

     docker compose exec -T postgres psql -U auth_service    -d authdb    < docker/postgres/seed/01-seed-authdb.sql
     docker compose exec -T postgres psql -U profile_service -d profiledb < docker/postgres/seed/02-seed-profiledb.sql
     docker compose exec -T postgres psql -U post_service    -d postdb    < docker/postgres/seed/03-seed-postdb.sql
     docker compose exec -T postgres psql -U like_service    -d likedb    < docker/postgres/seed/04-seed-likedb.sql
'@
    exit 1
}

$authPassword    = if ($env:AUTH_DB_PASSWORD)    { $env:AUTH_DB_PASSWORD }    else { 'auth_dev_pwd' }
$profilePassword = if ($env:PROFILE_DB_PASSWORD) { $env:PROFILE_DB_PASSWORD } else { 'profile_dev_pwd' }
$postPassword    = if ($env:POST_DB_PASSWORD)    { $env:POST_DB_PASSWORD }    else { 'post_dev_pwd' }
$likePassword    = if ($env:LIKE_DB_PASSWORD)    { $env:LIKE_DB_PASSWORD }    else { 'like_dev_pwd' }

Invoke-Seed -Database 'authdb'    -User 'auth_service'    -Password $authPassword    -File '01-seed-authdb.sql'
Invoke-Seed -Database 'profiledb' -User 'profile_service' -Password $profilePassword -File '02-seed-profiledb.sql'
Invoke-Seed -Database 'postdb'    -User 'post_service'    -Password $postPassword    -File '03-seed-postdb.sql'
Invoke-Seed -Database 'likedb'    -User 'like_service'    -Password $likePassword    -File '04-seed-likedb.sql'

Write-Host ''
Write-Host 'Sembrado completo. Todas las cuentas usan la contraseña: Password123!' -ForegroundColor Green
