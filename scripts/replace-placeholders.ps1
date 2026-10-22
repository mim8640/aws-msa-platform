param(
  [Parameter(Mandatory=$true)][string]$Domain,
  [Parameter(Mandatory=$true)][string]$EcrRegistry,
  [string]$Region="ap-northeast-2"
)
Get-ChildItem -Recurse -File | Where-Object {
  $_.FullName -notmatch '\\.git\\|build\\|node_modules\\'
} | ForEach-Object {
  $c = Get-Content $_.FullName -Raw
  $n = $c.Replace("YOUR_DOMAIN",$Domain).Replace("YOUR_ECR_REGISTRY",$EcrRegistry).Replace("ap-northeast-2",$Region)
  if ($n -ne $c) { Set-Content $_.FullName $n -NoNewline }
}
Write-Host "Replaced domain/ECR/region placeholders. RDS, Redis, S3 and GitHub placeholders still require review."
