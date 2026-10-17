# 配置学生机浏览器（掌控板编程用），在每台学生机上运行一次，需要管理员权限。
#
# 浏览器只允许 https 或本机地址使用串口（WebSerial）；学生机打开的是 http://教师机IP:端口，不算安全地址，
# 页面里就连不了掌控板。这里给 Edge 和 Chrome 写两条机器级策略（HKLM；实测 HKCU 下的不生效）：
#   OverrideSecurityRestrictionsOnInsecureOrigin  把教师机地址当成安全地址
#   SerialAllowUsbDevicesForUrls                  预先批准掌控板常见的 USB 芯片，插上板子自动连接、不弹选择框
#     0x303A(12346) 乐鑫原生 USB（掌控板 3.0）  0x1A86(6790) CH340/CH9102  0x10C4(4292) CP210x  0x0403(1027) FTDI
# 用法：双击“配置学生机浏览器.bat”，或  配置学生机浏览器.bat 192.168.1.10:8300
param([string]$Server)

$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) {
    $argList = "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`""
    if ($Server) { $argList += " -Server `"$Server`"" }
    Start-Process powershell -Verb RunAs -ArgumentList $argList
    exit
}

if (-not $Server) { $Server = Read-Host '请输入教师机上 ITtools 的地址（例如 192.168.1.10:8300）' }
$Server = ($Server.Trim() -replace '^https?://', '' -replace '/.*$', '')
if (-not $Server) { Write-Host '没有填地址，已退出。'; Read-Host '按回车关闭'; exit 1 }
$origin = "http://$Server"
$serial = '[{"devices":[{"vendor_id":12346},{"vendor_id":6790},{"vendor_id":4292},{"vendor_id":1027}],"urls":["' + $origin + '"]}]'

foreach ($k in 'HKLM:\SOFTWARE\Policies\Microsoft\Edge', 'HKLM:\SOFTWARE\Policies\Google\Chrome') {
    if (-not (Test-Path $k)) { New-Item -Path $k | Out-Null }
    Set-ItemProperty -Path $k -Name SerialAllowUsbDevicesForUrls -Value $serial -Type String
    $list = Join-Path $k 'OverrideSecurityRestrictionsOnInsecureOrigin'
    if (-not (Test-Path $list)) { New-Item -Path $list | Out-Null }
    $item = Get-Item -Path $list
    $values = @($item.GetValueNames() | ForEach-Object { $item.GetValue($_) })
    if ($values -notcontains $origin) {
        $n = 1
        while ($item.GetValueNames() -contains "$n") { $n++ }
        Set-ItemProperty -Path $list -Name "$n" -Value $origin -Type String
    }
}

Write-Host ''
Write-Host "已配置：$origin 可以连接掌控板（Edge 和 Chrome）。" -ForegroundColor Green
Write-Host '请关闭所有浏览器窗口后重新打开；可在地址栏打开 edge://policy 检查，两条策略状态应为“正常”。'
Read-Host '按回车关闭'
