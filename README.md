# LinkBridge

ストックダイヤラーからの発信を楽天リンク (`jp.co.rakuten.mobile.rcs` / 法人向け Rakuten Link Office `jp.co.rakuten.mobile.rcs.business`) に転送する個人用 Android アプリ。

## 動作

1. 「デフォルト通話転送アプリ」に設定すると、ダイヤラーからの発信が `CallRedirectionService` に届く
2. 通常の電話番号 (`tel:` スキーム、`*` / `#` を含まない番号) のみ、楽天リンクのダイヤラーを開いて転送。楽天リンクは `+` 付き番号を扱えないため、国内番号は `0` 始まり (`080...` 等)、海外番号は `010` プレフィックス形式 (`01033...` 等) に変換してから渡す
3. それ以外 (ショートコード `*123#` 等・USSD・`sip:` 等・特番 `171`/`188`/`147`/`148`/`1417` 等・ナビダイヤル `0570`・`#7119` 等の短縮番号) は通常発信のまま通す
4. 楽天リンクの「通話中」通知 (`notification_call_ongoing` チャンネル) を監視し、**実際に発信された通話だけ**を通話履歴へ補完する (通知が出ない = 発信していない = 履歴に残らない)

- 緊急通報 (110 / 119 / 118) はフレームワークがリダイレクト対象から除外するため影響しない
- 楽天リンク自身の発信 (自己管理コネクション) はリダイレクト対象外のためループしない

## ビルド

```sh
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## セットアップ

1. APK を端末にインストール
2. アプリを開き「デフォルト通話転送アプリに設定」→ システム画面で LinkBridge を選択
3. 「通話履歴への書き込みを許可」(`WRITE_CALL_LOG`)。一部端末ではダイアログで付与できない場合があり、その場合は adb で付与:

   ```sh
   adb shell pm grant com.hidenobunagai.linkbridge android.permission.WRITE_CALL_LOG
   ```

4. 「画面の上に表示を許可」(`SYSTEM_ALERT_WINDOW`)。通話転送時に楽天リンクをバックグラウンドから起動するために必須 (Android のバックグラウンド起動制限の免除)。adb でも付与可:

   ```sh
   adb shell appops set com.hidenobunagai.linkbridge SYSTEM_ALERT_WINDOW allow
   ```

5. 「通知へのアクセス」を許可。実際に発信された通話だけを通話履歴に記録するために必須。adb でも付与可:

   ```sh
   adb shell cmd notification allow_listener com.hidenobunagai.linkbridge/.CallNotificationListener
   ```

## テスト

```sh
./gradlew :app:testDebugUnitTest
```

## 注意事項

- ビデオ通話は区別できず、音声通話として楽天リンクへ渡る (`onPlaceCall` の 3 引数 API にビデオ情報がないため)
- 通知監視は「転送してから 10 分以内に始まった通話」を転送由来とみなす。転送直後に楽天リンクへの着信があった場合は誤って記録する可能性がある (稀)
- 通知へのアクセスが未許可の間は、楽天リンク発信の履歴補完は行われない
- 通話時間は「通話中通知の開始〜終了」の経過時間 (着信音の時間を含む)
