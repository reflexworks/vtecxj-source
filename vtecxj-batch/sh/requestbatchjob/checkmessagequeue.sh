#!/bin/bash

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`

# 定数読み込み
source ./checkmessagequeue_settings.txt
# メッセージキュー未送信チェック処理リクエストクラス(java)
CHECKMESSAGEQUEUE_CLASS="jp.reflexworks.batch.CheckMessageQueueApp"

#echo 'CLASSPATH='$CLASSPATH

# メッセージキュー未送信チェック処理リクエスト
java -cp $CLASSPATH $JAVA_OPTIONS $CHECKMESSAGEQUEUE_CLASS

