# BOSSRUSH 1.0.9 検証記録

検証日：2026-09-09。Android 8.0以上、applicationId `io.github.hatake716.bossrush`、versionName `1.0.9`、versionCode `10`。

## 変更内容

32体すべてに突進・飛び込み・回り込み・瞬間移動のいずれかを行う通常技を追加し、必殺技にも移動を組み込みました。攻撃の合間にも位置を変えます。移動経路と予兆の時計を共有し、突進の接触、着地と範囲攻撃、移動先からの照準、矢・炎の命中を実際のボス座標に合わせています。残像、土煙、ジャンプの高さ・影、転移ルーンを描画します。[全32体の仕様](COMBAT.md)。

## ビルドと単体テスト

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest
```

- ビルド成功。Debug APKとR8最適化・リソース縮小を含む未署名Release APKを生成。
- 単体テスト：**61件成功、失敗0件**。追加した移動専用テスト8件を含みます。
- 全32体の移動、経路固定、移動後の照準、画面端、突進の予兆と接触・1回限りの判定、着地、一時停止、矢の交差、瞬間移動中の誤命中防止、カットイン・勝利時の取り消し、二重詠唱との同期を検査。
- 全32体の通常技・必殺技の安全地点、HP1/2と1/4の移行、ランダムな連戦進行、4職業・30秒試算、タイトルの無音と戦闘曲・SE、入力・保存の既存検査も成功。
- Lint Debug：**エラー0、警告27**。CanvasのKTX拡張への置換提案2件が増えています。
- 39体のキャラクターPNGと33背景PNGの検査成功。
- Debug APK：既存と同じ署名証明書、16 KB zipalignの検証成功。
- `git diff --check` とローカルMarkdownリンクの確認成功。

## エミュレーター

API 35 / x86_64、`emulator-5554`。**操作・描画の19項目すべてについて成功を確認**しました。

最初に `tools/test-emulator.sh emulator-5554` を実行し、既存18項目が成功しました（全体 234.015 秒）。追加した移動描画テスト1項目では、突進終了から0.10秒後の画像にも位置が完全一致することを要求していましたが、実装ではその間に次の位置取りが始まります。終了直後の移動可能距離を含む確認へテストの期待値を修正し、その1項目だけを再実行して成功しました（24.756 秒）。着地・突進終了時点の座標一致は単体テストで別途検査しています。

```sh
adb -s emulator-5554 shell am instrument -w -r \
  -e class io.github.hatake716.bossrush.BossMovementRenderTest \
  io.github.hatake716.bossrush.test/androidx.test.runner.AndroidJUnitRunner
```

再実行の前後でアプリ本体のAPKは同一です。変更したのはテストAPKだけです。結果を `artifacts/delivery/movement-tests.json` に集約しています。

- **実際のAndroid入力**：戦士でタイトルから出発、移動する最初のボスを撃破、カットイン、技の強化、購入、Activity終了と保存再開。4職業の操作とバックグラウンド停止・復帰。
- **コントローラー入力**：冒険開始、移動、技、アイテム、メニュー、一時停止、解除と再開。物理ゲームパッドの接続試験とは区別しています。
- **描画用の状態**：全32ボスの通常攻撃・必殺技・カットイン・二重詠唱、キャラクター・背景、各画面比率、左右のカメラ穴を確認。
- **移動の連続フレーム**：グリンブルスティ、トール、スカジ、ロキを実際のGameEngineで進行させ、各60枚を出力。移動距離、足元マーカーの視認性、移動後の位置を検査。4段階の画像を目視確認。
- 盗賊の報酬、終盤・エンディング・最高スコアにはテスト用状態を使用。32体を人間が通しプレイした記録ではありません。

ログ：`artifacts/instrumentation.txt`（最初の実行）、`artifacts/instrumentation-movement.txt`（修正した1項目の再検証）。描画フレームは `artifacts/screenshots/motion-*.png`。比較動画 `artifacts/delivery/boss-movement-preview.mp4` は4種類のテスト用フレームを30 fpsで並べたものです。

![予兆・開始・移動中・終了の4段階](screenshots/boss-movement-storyboard.png)

## Pixel 10aへの更新

エミュレーターで検証したAPKを `adb install --no-incremental -r` で **1.0.8から1.0.9へ更新**しました。

- インストール：`Success`。
- 起動：`Status: ok`、583 ms。前面Activityとプロセス生存を確認。
- versionName `1.0.9` / versionCode `10` を確認。
- SharedPreferencesの更新前後のSHA-256が一致。既存の保存データを維持。
- 実機・検証したエミュレーター・引き渡しAPKのSHA-256が一致。
- 確認時点のアプリプロセスにクラッシュ・ANRのログなし。
- 物理端末へのタップ・スワイプ・キーの入力注入なし。実機の操作感はユーザーによるプレイ確認と区別しています。

証跡：`artifacts/delivery/physical-install-1.0.9.json`。

## 引き渡しAPK

`artifacts/delivery/BOSSRUSH-1.0.9-debug.apk`

```text
サイズ：154,527,388 bytes（約147.37 MiB）
SHA-256：a96f34767f17eefe8b9d8e54dfd46fe51293b48de53dbdf03541ad274efd5d59
署名：Android Debug
署名証明書 SHA-256：2c53b411c1193758289715cc230989564a571259b2eb4f5702b774c07a515e87
zipalign -c -P 16 4：成功
```

APKはGitへ含めず、ローカルの引き渡し用フォルダに保存しています。GitHub Actionsは画像検査・単体テスト・Lint・ビルドを行います。CIのDebug APKは別の署名を使うため、ローカルAPKとはハッシュ・署名が異なります。
