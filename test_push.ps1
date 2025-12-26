# AWS SNS 行動推送測試腳本
# 用於向 Android 設備發送推送通知
#
# 使用方法:
#   .\test_push.ps1 -PlatformArn "arn:aws:sns:..." -FcmToken "token" -AwsRegion "us-east-2"
#   .\test_push.ps1 -PlatformArn "arn:aws:sns:..." -FcmToken "token" -MessageTitle "標題" -MessageBody "內容"
#
# 參數說明:
#   -PlatformArn: AWS SNS 平台應用 ARN（必需）
#   -FcmToken: Firebase Cloud Messaging 設備令牌（必需）
#   -AwsRegion: AWS 區域（可選，預設: us-east-2）
#   -MessageTitle: 推送通知標題（可選，預設: "測試推送通知"）
#   -MessageBody: 推送通知內容（可選，預設: "這是一條來自 AWS SNS 的測試推送訊息"）
#   -MessageSubject: 訊息主題（可選，預設: "SNS 推送測試"）

param(
    [Parameter(Mandatory=$true, HelpMessage="AWS SNS 平台应用 ARN")]
    [string]$PlatformArn,
    
    [Parameter(Mandatory=$true, HelpMessage="Firebase Cloud Messaging 设备令牌")]
    [string]$FcmToken,
    
    [Parameter(Mandatory=$false)]
    [string]$AwsRegion = "us-east-2",
    
    [Parameter(Mandatory=$false)]
    [string]$MessageTitle = "測試推送通知",
    
    [Parameter(Mandatory=$false)]
    [string]$MessageBody = "這是一條來自 AWS SNS 的測試推送訊息",
    
    [Parameter(Mandatory=$false)]
    [string]$MessageSubject = "SNS 推送測試"
)

# 使用参数值
$PLATFORM_ARN = $PlatformArn
$FCM_TOKEN = $FcmToken
$AWS_REGION = $AwsRegion
$MESSAGE_TITLE = $MessageTitle
$MESSAGE_BODY = $MessageBody
$MESSAGE_SUBJECT = $MessageSubject

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "AWS SNS 行動推送測試腳本" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 檢查 AWS CLI 是否已安裝
Write-Host "檢查 AWS CLI 安裝狀態..." -ForegroundColor Yellow
try {
    $awsVersion = aws --version 2>&1
    Write-Host "✓ AWS CLI 已安裝: $awsVersion" -ForegroundColor Green
} catch {
    Write-Host "✗ 錯誤: AWS CLI 未安裝或未新增到 PATH" -ForegroundColor Red
    Write-Host "請訪問 https://aws.amazon.com/cli/ 安裝 AWS CLI" -ForegroundColor Yellow
    exit 1
}

Write-Host ""

# 檢查 AWS 憑證配置
Write-Host "檢查 AWS 憑證配置..." -ForegroundColor Yellow
try {
    $awsIdentity = aws sts get-caller-identity --region $AWS_REGION 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ AWS 憑證配置正確" -ForegroundColor Green
        Write-Host "  帳戶資訊: $awsIdentity" -ForegroundColor Gray
    } else {
        Write-Host "✗ 錯誤: AWS 憑證未配置或無效" -ForegroundColor Red
        Write-Host "請執行 'aws configure' 配置您的 AWS 憑證" -ForegroundColor Yellow
        exit 1
    }
} catch {
    Write-Host "✗ 錯誤: 無法驗證 AWS 憑證" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 顯示配置資訊
Write-Host "推送配置資訊:" -ForegroundColor Yellow
Write-Host "  平台 ARN: $PLATFORM_ARN" -ForegroundColor Gray
Write-Host "  FCM Token: $($FCM_TOKEN.Substring(0, [Math]::Min(50, $FCM_TOKEN.Length)))..." -ForegroundColor Gray
Write-Host "  AWS 區域: $AWS_REGION" -ForegroundColor Gray
Write-Host "  訊息標題: $MESSAGE_TITLE" -ForegroundColor Gray
Write-Host "  訊息內容: $MESSAGE_BODY" -ForegroundColor Gray
Write-Host ""

# 創建或獲取平台端點
Write-Host "創建/獲取平台端點..." -ForegroundColor Yellow
$endpointArn = $null

try {
    # 尝试创建平台端点
    $createEndpointRaw = aws sns create-platform-endpoint `
        --platform-application-arn $PLATFORM_ARN `
        --token $FCM_TOKEN `
        --region $AWS_REGION `
        2>&1
    
    if ($LASTEXITCODE -eq 0) {
        # 創建成功，解析端點 ARN
        try {
            if ($createEndpointRaw) {
                $createEndpointOutput = $createEndpointRaw | ConvertFrom-Json
                if ($createEndpointOutput -and $createEndpointOutput.EndpointArn) {
                    $endpointArn = $createEndpointOutput.EndpointArn
                    Write-Host "✓ 平台端點創建成功" -ForegroundColor Green
                    Write-Host "  端點 ARN: $endpointArn" -ForegroundColor Gray
                } else {
                    Write-Host "✗ 錯誤: 響應中未找到端點 ARN" -ForegroundColor Red
                    Write-Host "原始響應: $createEndpointRaw" -ForegroundColor Gray
                    exit 1
                }
            } else {
                Write-Host "✗ 錯誤: 創建端點響應為空" -ForegroundColor Red
                exit 1
            }
        } catch {
            Write-Host "✗ 錯誤: 無法解析創建端點響應" -ForegroundColor Red
            Write-Host "原始響應: $createEndpointRaw" -ForegroundColor Gray
            exit 1
        }
    } else {
        # 端點可能已存在，自動從錯誤訊息中提取端點 ARN
        # 嘗試從錯誤訊息中提取端點 ARN（格式：Endpoint arn:aws:sns:... already exists）
        $matchResult = $createEndpointRaw -match "Endpoint\s+(arn:aws:sns:[^\s]+)\s+already\s+exists"
        if ($matchResult -and $matches -and $matches.Count -gt 1) {
            $endpointArn = $matches[1]
            Write-Host "✓ 使用現有端點（端點已存在，自動跳過創建）" -ForegroundColor Green
            Write-Host "  端點 ARN: $endpointArn" -ForegroundColor Gray
        } else {
            # 如果無法從錯誤訊息提取，嘗試列出所有端點並搜尋匹配的 token
            try {
                $endpointsRaw = aws sns list-endpoints-by-platform-application `
                    --platform-application-arn $PLATFORM_ARN `
                    --region $AWS_REGION `
                    2>&1
                
                if ($LASTEXITCODE -eq 0 -and $endpointsRaw) {
                    try {
                        $endpoints = $endpointsRaw | ConvertFrom-Json
                        
                        if ($endpoints -and $endpoints.Endpoints -and $endpoints.Endpoints.Count -gt 0) {
                            foreach ($endpoint in $endpoints.Endpoints) {
                                if (-not $endpoint -or -not $endpoint.EndpointArn) {
                                    continue
                                }
                                
                                try {
                                    $endpointAttrsRaw = aws sns get-endpoint-attributes `
                                        --endpoint-arn $endpoint.EndpointArn `
                                        --region $AWS_REGION `
                                        2>&1
                                    
                                    if ($LASTEXITCODE -eq 0 -and $endpointAttrsRaw) {
                                        try {
                                            $endpointAttrs = $endpointAttrsRaw | ConvertFrom-Json
                                            
                                            if ($endpointAttrs -and $endpointAttrs.Attributes -and $endpointAttrs.Attributes.Token) {
                                                if ($endpointAttrs.Attributes.Token -eq $FCM_TOKEN) {
                                                    $endpointArn = $endpoint.EndpointArn
                                                    Write-Host "✓ 使用現有端點（找到匹配的 token）" -ForegroundColor Green
                                                    Write-Host "  端點 ARN: $endpointArn" -ForegroundColor Gray
                                                    break
                                                }
                                            }
                                        } catch {
                                            # 解析失敗，繼續搜尋下一個端點
                                            continue
                                        }
                                    }
                                } catch {
                                    # 繼續搜尋下一個端點
                                    continue
                                }
                            }
                        }
                    } catch {
                        # JSON 解析失敗，靜默處理
                    }
                }
            } catch {
                # 靜默處理，繼續後續流程
            }
        }
        
        # 如果仍然沒有找到端點 ARN，報錯退出
        if (-not $endpointArn) {
            Write-Host "✗ 錯誤: 無法創建或找到端點 ARN" -ForegroundColor Red
            Write-Host "請確保:" -ForegroundColor Yellow
            Write-Host "  1. FCM Token 正確且有效" -ForegroundColor Yellow
            Write-Host "  2. 平台應用 ARN 正確" -ForegroundColor Yellow
            Write-Host "  3. AWS 憑證有足夠的權限" -ForegroundColor Yellow
            Write-Host ""
            Write-Host "錯誤詳情:" -ForegroundColor Yellow
            Write-Host $createEndpointRaw -ForegroundColor Gray
            exit 1
        }
    }
} catch {
    Write-Host "✗ 錯誤: 創建/獲取端點時發生異常: $_" -ForegroundColor Red
    exit 1
}

# 驗證端點 ARN 格式（必須是 endpoint ARN，不能是 platform application ARN）
if (-not $endpointArn) {
    Write-Host "✗ 錯誤: 端點 ARN 為空" -ForegroundColor Red
    Write-Host "無法獲取有效的端點 ARN" -ForegroundColor Yellow
    exit 1
}

if ($endpointArn -notmatch "arn:aws:sns:[^:]+:[^:]+:endpoint/") {
    Write-Host "✗ 錯誤: 無效的端點 ARN 格式" -ForegroundColor Red
    Write-Host "端點 ARN 必須是 endpoint ARN，不能是 platform application ARN" -ForegroundColor Yellow
    Write-Host "當前 ARN: $endpointArn" -ForegroundColor Gray
    exit 1
}

Write-Host ""

# 建構推送訊息 JSON
# 使用 --message-structure json 時，GCM 的值必須是轉義的 JSON 字串
$gcmMessageObj = @{
    notification = @{
        title = $MESSAGE_TITLE
        body = $MESSAGE_BODY
    }
    data = @{
        message = $MESSAGE_BODY
        timestamp = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
    }
}

# 將 GCM 訊息轉換為 JSON 字串
$gcmMessageJson = $gcmMessageObj | ConvertTo-Json -Depth 10 -Compress

# 使用 PowerShell 的 ConvertTo-Json 建構外層 JSON
# PowerShell 會自動轉義字串值中的特殊字元
$messageObj = [PSCustomObject]@{
    GCM = $gcmMessageJson
}

# 轉換為 JSON 字串
$messageJson = $messageObj | ConvertTo-Json -Depth 10 -Compress

# 除錯：顯示生成的 JSON（用於排查問題）
Write-Host "生成的 JSON 訊息:" -ForegroundColor Gray
Write-Host $messageJson -ForegroundColor Gray
Write-Host ""

# 發送推送通知
Write-Host "發送推送通知..." -ForegroundColor Yellow
try {
    $publishResult = aws sns publish `
        --target-arn $endpointArn `
        --message $messageJson `
        --subject $MESSAGE_SUBJECT `
        --message-structure json `
        --region $AWS_REGION `
        2>&1
    
    if ($LASTEXITCODE -eq 0) {
        $result = $publishResult | ConvertFrom-Json
        Write-Host "✓ 推送通知發送成功！" -ForegroundColor Green
        Write-Host "  訊息 ID: $($result.MessageId)" -ForegroundColor Gray
        Write-Host ""
        Write-Host "請檢查您的設備是否收到推送通知" -ForegroundColor Cyan
    } else {
        Write-Host "✗ 錯誤: 推送通知發送失敗" -ForegroundColor Red
        Write-Host "錯誤資訊: $publishResult" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "✗ 錯誤: 發送推送時發生異常: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "腳本執行完成" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

