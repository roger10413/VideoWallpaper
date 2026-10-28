# 影片桌布（VideoWallpaper）

在 Android 桌布上播放自選影片，可以自動輪播。只給自己用，直接安裝 APK，不上架。

## 功能
- 從相簿挑選多部影片當播放清單，可以調整順序和移除
- 輪播：每部播完換、每隔 N 分鐘換、每次回到桌面時換；可選隨機順序
- 聲音開關（預設靜音，靜音時不解碼音訊）
- 畫面：填滿（裁切）或完整顯示（留黑邊）
- 只在桌布看得到時播放
- 發票載具：輸入手機條碼後，用常駐通知把條碼直接顯示在鎖定畫面，不用解鎖就能給店員掃；點通知會跳出全螢幕、最高亮度的大條碼

## 程式結構
| 檔案 | 內容 |
|---|---|
| `VideoWallpaperService.kt` | 桌布服務：ExoPlayer 播放、輪播邏輯、可見時才播放 |
| `VideoRenderer.kt` | OpenGL ES 繪製影格，負責填滿／完整顯示縮放 |
| `WallpaperSettings.kt` | 設定（SharedPreferences），設定畫面和桌布共用 |
| `MainActivity.kt` | 設定畫面（Jetpack Compose） |
| `Code39.kt` | 產生 Code 39 條碼（發票手機條碼用的格式） |
| `CarrierNotification.kt` | 鎖定畫面的載具條碼常駐通知 |
| `CarrierActivity.kt` | 全螢幕條碼，可顯示在鎖定畫面上 |

## 建置
需要 Android Studio（附 JDK 和 Android SDK，compileSdk 37）。

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

正式版（`assembleRelease`）會用 `~/.android/videowallpaper/keystore.properties` 指定的發行金鑰簽章；找不到時改用 debug 金鑰。下載好的 APK 在 [Releases](https://github.com/roger10413/VideoWallpaper/releases)。

## 已知限制
- HyperOS 鎖定畫面可能不顯示第三方動態桌布（系統限制）
- 如果桌布過一陣子停住，到 App 資訊 → 省電策略改成「無限制」
