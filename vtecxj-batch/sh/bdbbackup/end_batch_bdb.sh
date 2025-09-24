#!/bin/bash

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`

# 定数読み込み
source ./batch_settings.txt
# 夜間バッチ実行フラグON・OFFクラス(java)
BATCH_BDB_FLAG_CLASS="jp.reflexworks.batch.BatchBDBFlagApp"

echo '[end_batch_bdb] start'

# 夜間バッチend
java -cp $CLASSPATH $JAVA_OPTIONS $BATCH_BDB_FLAG_CLASS 'end' $URI_BATCH_BDB

echo '[end_batch_bdb] end'

