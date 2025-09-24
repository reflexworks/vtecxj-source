#!/bin/bash

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`

# 定数読み込み
source ./batch_settings.txt
# 夜間バッチ実行フラグON・OFFクラス(java)
BATCH_BDB_FLAG_CLASS="jp.reflexworks.batch.BatchBDBFlagApp"

echo '[start_batch_bdb] start'

# 夜間バッチstart
java -cp $CLASSPATH $JAVA_OPTIONS $BATCH_BDB_FLAG_CLASS 'start' $URI_BATCH_BDB

echo '[start_batch_bdb] end'

