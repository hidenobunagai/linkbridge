# LinkBridge

ストックダイヤラーからの発信を楽天リンク (`jp.co.rakuten.mobile.rcs`) に転送する個人用 Android アプリ。

## 動作

1. 「デフォルト通話転送アプリ」に設定すると、ダイヤラーからの発信が `CallRedirectionService` に届く
2. 通常の電話番号 (`tel:` スキーム、`*` / `#` を含まない番号) のみ、楽天リンクのダイヤラーを開いて転送
3. それ以外 (ショートコード `*123#` 等・USSD・`sip:` 等) は通常発信のまま通す
4. 転送時に通話履歴へ発信記録 (0 秒) を補完する

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

## テスト

```sh
./gradlew :app:testDebugUnitTest
```

## 注意事項

- ビデオ通話は区別できず、音声通話として楽天リンクへ渡る (`onPlaceCall` の 3 引数 API にビデオ情報がないため)
- 楽天リンクで発信せずに戻った場合も、0 秒の発信履歴が残る
- 楽天リンク側も通話履歴を書く場合、1 発信で 2 エントリになる可能性がある
