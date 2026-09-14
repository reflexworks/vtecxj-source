#!/bin/bash

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`

# 定数読み込み
source ./requestbatchjob_settings.txt

# プロパティファイル読み込み
# Usage: getProperty KEY
function getProperty() {
    grep "^$1=" "$PROPFILE" | cut -d'=' -f2
}

URL=`getProperty '_url.batchjob'`

# バッチジョブ実行管理 (旧方式: 1 Pod で全サービスを処理)
# 移行期間中の後方互換のため残置。checkservices.sh の per-service 方式が
# 安定稼働したら、この POST は削除する。
##curl --fail-with-body -X POST -s $URL > /dev/null

# サービス単位の定期チェック
# (メッセージキュー未送信チェック・BDBQリトライチェック・バッチジョブ実行管理を統合)
#echo "[request batchjob] check services start."
./checkservices.sh
#echo "[request batchjob] check services end."
