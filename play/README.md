# Google Play 公開資料 — BOSSRUSH 1.0.20

Play Console への提出に必要な成果物一式です。2026-09-10 作成、versionName `1.0.20` / versionCode `21`。

## 中身

| パス | 内容 |
|---|---|
| `release/BOSSRUSH-1.0.20.aab` | **Play にアップロードするファイル**。署名済み App Bundle |
| `release/BOSSRUSH-1.0.20-release.apk` | 動作確認用の署名済み APK（Play には不要） |
| `release/SHA256SUMS.txt` | 上記2ファイルのハッシュ |
| `listing/ja-JP-store-listing.md` | アプリ名・短い説明・詳細説明・リリースノート・カテゴリ・タグ |
| `listing/content-rating-and-data-safety.md` | IARC 質問票とデータセーフティの回答案 |
| `listing/privacy-policy.md` | プライバシーポリシー本文（公開版は `docs/privacy-policy.html`） |
| `graphics/icon-512.png` | アプリアイコン 512×512 |
| `graphics/feature-graphic-1024x500.png` | フィーチャーグラフィック 1024×500 |
| `screenshots/*.png` | スクリーンショット8枚（2160×1080） |
| `video/BOSSRUSH-short-30s-9x16-odin.mp4` | 宣伝動画 縦版 1080×1920 / 30秒（メイン） |
| `video/BOSSRUSH-promo-30s-16x9.mp4` | 宣伝動画 横版 1920×1080 / 30秒 |
| `video/BOSSRUSH-promo-30s-9x16.mp4` | 宣伝動画 縦版 1080×1920 / 30秒（別案） |

`release/` と `video/` は容量が大きいため `.gitignore` 済みです。手元の成果物として扱ってください。

## 署名

アップロード鍵を新規作成しました。**この鍵を失うと同じアプリを更新できなくなります。**

- キーストア: `~/.android/keystores/bossrush-upload.jks`（RSA 4096bit、2056-09-02 まで有効）
- パスワード: `~/.android/keystores/bossrush-upload.pass`
- 別名: `bossrush-upload`
- 証明書 SHA-256: `4D:17:EC:AB:52:96:6F:39:C3:CB:C6:9F:4B:C6:CA:A0:8A:02:3A:13:42:C2:96:49:E1:B2:CD:C6:C2:16:7B:0A`

**両ファイルを安全な場所にバックアップしてください。**
リポジトリの `keystore.properties` が鍵の場所とパスワードを Gradle に渡します。
このファイルと `*.jks` は `.gitignore` 済みです。

Play App Signing を利用する場合、この鍵は「アップロード鍵」として登録され、
配信用の署名は Google が管理する鍵で行われます。

再ビルド:

```bash
./gradlew bundleRelease
```

`keystore.properties` が無い環境では、従来どおり署名なしのリリースビルドになります。

## 提出前に必要な作業

1. ~~プライバシーポリシーの公開~~ — 公開済み。`docs/privacy-policy.html` を GitHub Pages で配信しています。
   URL: https://hatake716.github.io/bossrush/privacy-policy.html
2. **販売価格の設定** — 有料アプリとして配信するため、販売者アカウントの登録と価格設定が必要です。
3. **対象年齢の設定** — 13歳以上に限定すればファミリーポリシーの適用外にできます。

## Play Console での入力順

1. アプリを作成（アプリ名・デフォルト言語 日本語・アプリ／ゲーム＝ゲーム・無料／有料＝有料）
2. ストアの設定 → カテゴリ「アクション」、タグを選択、連絡先情報
3. メインのストアの掲載情報 → `listing/ja-JP-store-listing.md` から短い説明・詳細な説明を貼り付け、
   `graphics/` と `screenshots/` をアップロード、動画は YouTube にアップロードしてその URL を登録
4. アプリのコンテンツ → プライバシーポリシー URL、広告「なし」、コンテンツのレーティング（IARC）、
   対象年齢、データセーフティ。回答は `listing/content-rating-and-data-safety.md` を参照
5. 製品版リリース → `release/BOSSRUSH-1.0.20.aab` をアップロード、リリースノートを貼り付け
6. 価格の設定 → 販売価格を入力
7. 審査へ送信

## 動画について

Play のプロモーション動画は YouTube の URL で登録します（ファイルの直接アップロードはできません）。
YouTube に「限定公開」でアップロードし、その URL を登録してください。

- `BOSSRUSH-short-30s-9x16-odin.mp4` — メインのショート動画。見出しコピー付きのカード構成で、
  必殺技のフルカラー演出とエンディングのカラー化まで見せます。BGM はゲーム内の自作曲
  `assets/music/odin.ogg`（「隻眼の夜明け」228 BPM）。ラウドネスは -14.0 LUFS / -1.0 dBTP に
  正規化済みで、YouTube・TikTok・Instagram の推奨値に合わせています。
- `BOSSRUSH-promo-30s-16x9.mp4` / `BOSSRUSH-promo-30s-9x16.mp4` — 別案。API 35 エミュレーターでの
  実プレイ録画をつなぎ、カット割りを BGM（`thor.ogg`「九歩の雷鳴」232 BPM）の拍に合わせたもの。

同梱の楽曲はすべて本作の自作曲のため、動画での使用に権利上の制約はありません。

## 検証済み事項

- 署名済み APK をエミュレーター（API 35）にインストールし、タイトル表示・新規開始・
  職業選択・ボス戦・予兆回避・ダメージ処理が正常に動作することを確認。
  `isMinifyEnabled` / `isShrinkResources` を有効にしたリリースビルドで、難読化による不具合なし。
- `apksigner verify`：v2 スキームで検証成功、署名者1名、証明書 SHA-256 が生成時と一致。
- AAB のベースモジュールは約 170 MB。Play のベースモジュール上限 500 MB（圧縮ダウンロードサイズ基準）
  に収まります。内訳は背景 83.9 MB、スプライト 60.6 MB、音楽 17.8 MB、フォント 1.1 MB。
- `AndroidManifest.xml` に `<uses-permission>` が 0 件であること、ソース全体に通信・広告・解析・
  課金 SDK の参照が無いことを grep で確認済み。データセーフティの「収集なし」申告と実装が一致します。
