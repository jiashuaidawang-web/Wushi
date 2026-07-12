$secpasswd = ConvertTo-SecureString "9fchpda2" -AsPlainText -Force
$cred = New-Object System.Management.Automation.PSCredential ("user", $secpasswd)

# 试 Basic Auth
Write-Host "=== Try Basic Auth ==="
try {
    $r = Invoke-WebRequest -Uri "http://192.168.1.1/html/ssmp/restore/resetfactory.asp" -Credential $cred -TimeoutSec 10 -UseBasicParsing
    Write-Host "Status: $($r.StatusCode), Length: $($r.Content.Length)"
    if ($r.Content -match 'name="onttoken".+?value="([^"]+)"') { Write-Host "Token: $($Matches[1].Substring(0,20))..." }
    if ($r.Content -match 'id="hwonttoken".+?value="([^"]+)"') { Write-Host "Token: $($Matches[1].Substring(0,20))..." }
} catch { Write-Host "Basic Auth failed: $($_.Exception.Message)" }

# 试 Digest Auth
Write-Host "`n=== Try Digest Auth ==="
try {
    $r = Invoke-WebRequest -Uri "http://192.168.1.1/html/ssmp/restore/resetfactory.asp" -Credential $cred -UseDefaultCredentials -TimeoutSec 10 -UseBasicParsing -Authentication Digest
    Write-Host "Status: $($r.StatusCode), Length: $($r.Content.Length)"
} catch { Write-Host "Digest failed: $($_.Exception.Message)" }
