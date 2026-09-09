# BOSSRUSH 1.0.7 検証記録

検証日：2026-09-09。Android 8.0以上、applicationId `io.github.hatake716.bossrush`、versionName `1.0.7`、versionCode `8`。

## 今回の変更

- 初回必殺技を残りHP1/2で発動。各戦闘の初回だけ3秒のカットインを表示。
- 初動、基準予兆、技と段の間隔、必殺技の段間隔と追撃の時間差を0.5倍に変更。安全地点への到達に足りない場合は予兆を延長。
- HP1/4以下で二重詠唱。通常技の各段や必殺技に、そのボスの別の固有技の一撃を同時発動。共通の退避場所と吹き飛ばし後の着地点を確認。
- 全4職業の基礎移動速度を1.2倍に変更。二重詠唱の説明は操作ボタンと重ならないよう折り返し、詠唱バーは実際の予兆時間を反映。
- 全32体のカットイン背景を480×132の手描きドット絵へ変更。世界樹・石柱・組紐・神ごとの紋章・地域の景色を7〜12色で描画。攻撃色との連動を維持。
- タイトル曲「九つの世界への旅立ち」を新作。144 BPM、ニ長調、32小節のファンファーレと冒険の主題。約53.33秒でループ。

詳細：[戦闘仕様](COMBAT.md)、[楽曲](MUSIC.md)、[コントローラー操作](CONTROLS.md)。

## ビルド・静的検査

JDK 17 / Gradle 8.14.3 / Android Gradle Plugin 8.13.0 / Kotlin 2.2.20 / compileSdk 36。

```text
python3 tools/check-art.py
39 distinct RGBA character PNGs match all boss IDs and generation records.

python3 tools/check-backgrounds.py
33 distinct landscape PNGs match all 32 boss IDs, the world tree and generation records.

./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest
BUILD SUCCESSFUL
```

- 単体テスト：**53件成功、失敗0件**。
- Lint Debug：**エラー0、警告25**。SDK・依存更新通知、画面方向、KTX提案、アクセシビリティAPIなど。
- キャラクター39枚と背景33枚のID・PNG・生成記録のSHA-256を検査。既存の原画72枚を維持。
- Debug APK：署名と16 KB zipalignの検証成功。
- Release APK：R8最適化・リソース縮小を含むビルド成功。未署名。
- `git diff --check` とローカルMarkdownリンクの確認：成功。

| 単体テスト | 件数 | 主な確認内容 |
|---|---:|---|
| GameEngineTest | 25 | 4職業、直線成長、30秒基準のHP、1/2での初回必殺技、回避猶予、スコア・報酬・ショップ |
| BossCombatTest | 12 | 全66通常技、後半の難易度、ランダムな再使用、1/4での二重詠唱、同時判定、共通の退避場所、速度1.2倍 |
| MusicTest | 5 | 全32戦闘曲の和声・波形・ループ、新タイトル32小節の波形・音量・区間の違い、WAV生成 |
| FeedbackTest | 5 | 主人公のSE、着弾イベント、残光とダメージの分離 |
| GameViewportTest | 3 | 縦横比、安全領域、描画と入力の座標 |
| ControllerMathTest | 3 | スティック・十字キーの計算、メニュー選択、カットイン配色 |

複合攻撃は全32体の通常技・必殺技について、四隅と中央付近からの共通の退避地点と、未強化の召喚士の移動時間を検査しました。吹き飛ばしが重なる場合は各着地の外壁と別の技の判定を確認しています。さらに全32体×24通りの乱数固定の位置から連撃を進め、HP1/4の全32戦を100秒ずつ進めて、通常技と必殺技の同時判定・ランダムな再使用・追加カットインがないことを確認しました。

## エミュレーター

API 35 / x86_64 / 2400×1080横画面、対象 `emulator-5554`。**最終APKで18件成功、失敗0件、229.355秒**。

```sh
tools/test-emulator.sh emulator-5554
```

- **実際のタッチ操作**：戦士で最初のボスの予兆を回避・攻撃して撃破。HPや攻撃量を直接変更せず、初回カットイン、技の強化、ショップ購入、Activity終了後の保存再開まで確認。
- **全4職業**：移動と攻撃の同時入力、召喚、アイテム、バックグラウンドでの停止と復帰を検査。
- **外部コントローラーの入力処理**：AndroidのSOURCE_GAMEPAD／SOURCE_JOYSTICKイベントで、冒険開始・スティックと十字キーの上下左右と斜め移動・全16技・長押し・アイテム確認・メニュー・一時停止を検査。切断通知の処理も直接呼んで確認。
- **描画用の戦闘状態**：全32体の背景と発色、全66通常技、16種類の予兆、安全円の維持、残光より手前に描く足元、タイトルとカラーエンディングを検査。
- **新カットイン**：全32体で7〜12色の限定パレット・不透明性・固有の形状・攻撃の主色と副色の使用を画素検査。全32体の一覧を生成し、文字、人物、紋章と背景の見え方を確認。
- **二重詠唱の描画**：全32体で通常技と必殺技を同時に表示し、二重詠唱の説明と主人公の足元が描かれることを確認。
- **画面と進行**：複数の縦横比・左右の安全領域・両向きの横画面、図鑑32体、盗賊の戦利品交換、終盤の成長とエンディングの保存を検査。終盤はテスト用の状態を使用。

テストはエミュレーター内のアプリ保存データを初期化し、物理端末への入力注入を拒否します。初回の18件成功後、背景・説明の折り返し・詠唱バー・吹き飛ばしの合成判定を仕上げた最終APKでも全18件を再実行しました。

ログ：`artifacts/instrumentation.txt`。画像：`artifacts/screenshots/`、公開用画像：`docs/screenshots/`。

- [新カットイン：前半16体](screenshots/mythic-cutins-1.png)、[後半16体](screenshots/mythic-cutins-2.png)
- [オーディンのカットイン](screenshots/color-cutin-odin.png)、[スルトのカットイン](screenshots/color-cutin-surtr.png)
- [トールの二重詠唱](screenshots/double-cast-thor.png)、[オーディンの二重詠唱](screenshots/double-cast-odin.png)

上記の比較画像は描画検証用の状態です。`cutin.png` は最初のボスをタッチ操作で攻略中に取得した画面です。

## Pixel 10aへの更新

最終エミュレーターテストと同じAPKを `adb install --no-incremental -r` で **1.0.6から1.0.7へ更新**しました。

- インストール：`Success`。
- 起動：`Status: ok`、552 ms。前面Activityとプロセス生存を確認。
- versionName `1.0.7` / versionCode `8` を確認。
- 既存SharedPreferencesの更新前後のSHA-256が一致。セーブデータを維持。
- 実機のインストール済みAPKと、最終エミュレーターテストのAPKのSHA-256が一致。
- 確認時点のアプリプロセスにクラッシュ・ANRのログなし。
- 実機へのタップ・スワイプ・キーなどの入力注入なし。

証跡：`artifacts/delivery/physical-install-1.0.7.json`。

## 引き渡し

ファイル：`artifacts/delivery/BOSSRUSH-1.0.7-debug.apk`

```text
サイズ：154,509,211 bytes（約147.35 MiB）
SHA-256：3a937d14ba7dc5c0091ec9d7beb725271ba2ff46c573ae8d79a12d82aed3fae3
署名：Android Debug
署名証明書 SHA-256：2c53b411c1193758289715cc230989564a571259b2eb4f5702b774c07a515e87
zipalign -c -P 16 4：成功
```

タイトル曲のプレビュー：`artifacts/music/title-overture.wav`（22,050 Hz、16-bit、mono、約53.33秒）。他のBGM7曲とSE7種のプレビューも出力しています。

APKとWAVはGitへ含めず、ローカルの引き渡し用フォルダへ保存。GitHub Actionsは画像検査・単体テスト・Lint・ビルドを行い、Debug APKをArtifactとして生成します。CIは別のDebug署名を使うため、ローカルAPKとはハッシュ・署名が異なります。

## 確認範囲

実機での更新・起動・データ保持と、エミュレーターでの表示・入力を確認しました。**人による32連戦の通しプレイ、実機でのBGMの聴感、物理コントローラー機種ごとのBluetooth／USB接続とプレイは未確認**です。全32体の複合攻撃とカットインは、テスト用に用意した戦闘状態での検査です。
