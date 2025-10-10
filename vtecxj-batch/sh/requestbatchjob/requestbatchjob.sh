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

# バッチジョブ実行管理
#echo "[request batchjob] "`curl --version`
#echo "[request batchjob] curl -X POST "$URL
# -o "/dev/null" をつけるとレスポンスを出力しない。
#curl -X POST -s $URL
#curl -fsS -X POST -s $URL > /dev/null
curl --fail-with-body -X POST -s $URL > /dev/null

# メッセージキュー未送信チェック
#echo "[request batchjob] check message queue start."
./checkmessagequeue.sh
#echo "[request batchjob] check message queue end."
