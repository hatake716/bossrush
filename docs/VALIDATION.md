# BOSSRUSH 1.0.8 検証記録

検証日：2026-09-09。Android 8.0以上、applicationId `io.github.hatake716.bossrush`、versionName `1.0.8`、versionCode `9`。

## 変更内容

タイトルBGMを無音にしました。タイトルと同じ音楽シーンを使用する職業選択・図鑑・遊び方もBGMを鳴らしません。タイトル曲の生成コードを削除し、タイトル曲のWAVプレビュー生成も停止しました。

戦闘・ボス紹介、ショップ・報酬、エンディングのBGM、操作・攻撃のSE、音のON/OFF設定は維持しています。詳細は[楽曲仕様](MUSIC.md)を参照してください。

## ビルドと単体テスト

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest
```

- ビルド：成功。Debug APKと、R8最適化・リソース縮小を含む未署名Release APKを生成。
- 単体テスト：**53件成功、失敗0件**。
- タイトルのPCM生成が全32ボスの選択状態・0〜60秒の標本時刻で0になることを検査。
- 戦闘・ショップ・エンディングの波形と攻撃SEが無音にならないことを検査。
- 全32戦闘曲、戦闘ロジック、入力座標などの既存単体テストも成功。
- Lint Debug：**エラー0、警告25**。
- Debug APK：既存と同じ署名証明書、16 KB zipalignの検証成功。
- `git diff --check` とローカルMarkdownリンクの確認：成功。

## エミュレーター

API 35 / x86_64、対象 `emulator-5554`。変更に関連する既存の `GameplayTest` を実行し、**4件成功、失敗0件、97.597秒**。

```sh
adb -s emulator-5554 shell am instrument -w -r \
  -e class io.github.hatake716.bossrush.GameplayTest \
  io.github.hatake716.bossrush.test/androidx.test.runner.AndroidJUnitRunner
```

1. 戦士でタイトルから出発し、タッチ操作で最初のボスを撃破。カットイン、強化、ショップ購入、Activity終了後の保存再開まで確認。
2. 全4職業の移動・技・召喚、アイテム、バックグラウンドでの停止・復帰を確認。
3. 図鑑を巡り、オーディンのページまで表示。
4. 盗賊の戦利品交換とエンディング、最高スコアの保存とタイトル復帰を確認。終盤にはテスト用の状態を使用。

タイトルの無音はPCM生成の検査によるものです。エミュレーターでは画面遷移と操作を確認しています。今回は画像や戦闘仕様を変更していないため、描画比較とコントローラー専用のAndroidテストの再実行は行っていません。[1.0.7の全18件の検証記録](https://github.com/hatake716/bossrush/blob/f870dc2bb4f2e8632308f76a83ca9cdf0e3673bf/docs/VALIDATION.md)と区別しています。

ログ：`artifacts/instrumentation-silent-title.txt`。

## Pixel 10aへの更新

エミュレーターで検証したAPKを `adb install --no-incremental -r` で **1.0.7から1.0.8へ更新**しました。

- インストール：`Success`。
- 起動：`Status: ok`、637 ms。前面Activityとプロセス生存を確認。
- versionName `1.0.8` / versionCode `9` を確認。
- SharedPreferencesの更新前後のSHA-256が一致。既存の保存データを維持。
- 実機のAPKとエミュレーターで検証したAPKのSHA-256が一致。
- 確認時点のアプリプロセスにクラッシュ・ANRのログなし。
- 物理端末へのタップ・スワイプ・キーの入力注入なし。

証跡：`artifacts/delivery/physical-install-1.0.8.json`。

## 引き渡しAPK

`artifacts/delivery/BOSSRUSH-1.0.8-debug.apk`

```text
サイズ：154,306,044 bytes（約147.16 MiB）
SHA-256：1446393123c45595a93e7fe78ed1ccdff82b210c79c959d39764e45b50c957a8
署名：Android Debug
署名証明書 SHA-256：2c53b411c1193758289715cc230989564a571259b2eb4f5702b774c07a515e87
zipalign -c -P 16 4：成功
```

APKはGitへ含めず、ローカルの引き渡し用フォルダに保存しています。GitHub Actionsは画像検査・単体テスト・Lint・ビルドを行います。CIのDebug APKは別の署名を使うため、ローカルAPKとはハッシュ・署名が異なります。
