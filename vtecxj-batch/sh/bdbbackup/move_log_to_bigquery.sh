#!/bin/bash

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`

# 定数読み込み
source ./batch_settings.txt
# BDBに登録されたログEntryをBigQueryに移動させるクラス(java)
MOVE_LOG_TO_BIGQUERY_CLASS="jp.reflexworks.batch.MoveLogToBigQueryApp"

# BDBに登録されたログEntryをBigQueryに移動
java -cp $CLASSPATH $JAVA_OPTIONS $MOVE_LOG_TO_BIGQUERY_CLASS $VALID_NAMESPACELIST_FILEPATH

