# 1.0.12 BGM変更の検証記録

2026-09-10。versionName `1.0.12` / versionCode `13`。変更範囲は全34曲のピアノ＋ロックへの編曲、音声再生、関連資料とテストです。タイトルの無音を維持しています。

## 音源とビルド

- 全32体＋ショップ・報酬＋エンディングの34曲を収録。各音声・MIDIのSHA-256、BPM・拍子・小節数・曲順を検査。
- 全曲44.1 kHzステレオ。戦闘曲1周28.235〜32.308秒。ショップ・エンディングは30秒。
- FFmpegで全音声を復号し、音量・ピーク・ステレオ成分・10ms以上の無音区間がないこと・端点の接続補正を検査。
- JVMテスト **75件成功**。音楽ルーティング、連続ループ、ミュート、SEダッキング、44.1 kHz出力時のSEの長さとサンプル補間、従来のゲーム仕様を含む。
- `testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest` 成功。Lintエラー0件。releaseはビルド確認用の未署名APK、実機への配布は従来と同じ開発署名のdebug APK。

## Androidエミュレーター

`MusicPlaybackTest` の **3件成功（41.339秒）**。

1. APKに入った全34曲をMediaCodecで復号し、ハッシュ、左右チャンネル、音量、端点を検査。復号後の全曲のサンプル数は指定値と完全一致。曲ごとの復号時間はこのエミュレーターで342〜620ms。各曲2周を実際のAudioMixに通し、周回後の出力も確認。
2. AudioTrackで戦闘・ショップ・エンディングを再生。ミュート、急な曲切り替え、タイトルへ戻った後の遅延した読み込み、タイトル操作SE、3回の停止・再開を検査。
3. MainActivityとGameViewに戦闘・報酬・エンディング・各メニューのテスト用状態を作り、実際のフレーム更新から正しい曲または無音へ切り替わることを確認。全32体を手操作で倒した結果ではない。

## Pixel 10a

エミュレーターで検査した同一APKを `adb install --no-incremental -r` で1.0.11から更新。1.0.12 / code 13、MainActivityの起動成功、プロセスの継続稼働、アプリの警告・クラッシュログが出ていないことを確認。インストール前後で本アプリの保存データのハッシュは一致しました。端末にインストールされたbase.apkのハッシュも検証済みAPKと一致しています。

実機にタッチ・キー操作を注入していません。実機スピーカーでの聴感・演奏バランスについてはユーザーによる確認と区別します。

## 成果物

- APK: `artifacts/delivery/music-1.0.12/BOSSRUSH-1.0.12-debug.apk`
- SHA-256: `5e96a72018ee5c03b8e4735d53a208d011a830ed8c6d1926bd09e4a94e61c7cc`
- 署名証明書 SHA-256: `2c53b411c1193758289715cc230989564a571259b2eb4f5702b774c07a515e87`
- 全曲の試聴: `artifacts/music-1.0.12/index.html` と同フォルダーのWAV。APK内Oggから復号した音源。
- 音声・Android・実機の検証結果: `artifacts/delivery/music-1.0.12/`

音楽の制作方法・音源ライセンス・全曲表は [MUSIC.md](MUSIC.md)。以前のゲーム機能の検証記録は [VALIDATION.md](VALIDATION.md)、技成長の仕様は [SKILL_GROWTH.md](SKILL_GROWTH.md) を参照してください。
