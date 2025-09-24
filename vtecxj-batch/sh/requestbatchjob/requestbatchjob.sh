#!/bin/bash

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`

URL="http://vtecxbatchjob-svc/batchjob/"

# バッチジョブ実行管理
#echo "[request batchjob] curl -X POST "$URL
# -o "/dev/null" をつけるとレスポンスを出力しない。
curl -X POST -s $URL

# メッセージキュー未送信チェック
#echo "[request batchjob] check message queue start."
./checkmessagequeue.sh
#echo "[request batchjob] check message queue end."
