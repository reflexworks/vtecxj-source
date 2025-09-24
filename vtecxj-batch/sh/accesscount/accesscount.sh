#!/bin/bash

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`

# 定数読み込み
source ./settings.txt
source ./accesscount_settings.txt
# アクセスカウンタ集計クラス(java)
ACCESSCOUNT_CLASS="jp.reflexworks.batch.AccessCountApp"

#echo 'CLASSPATH='$CLASSPATH

# サービスアカウントを有効にする
gcloud auth activate-service-account $SERVICE_ACCOUNT --key-file $CLASSES_DIR/$SERVICE_ACCOUNT_JSON --project $GCP_PROJECT_ID --quiet

# アクセスカウンタ集計、ストレージ容量取得
echo '[accesscount] 開始'
java -cp $CLASSPATH $JAVA_OPTIONS $ACCESSCOUNT_CLASS $GSUTIL_DIR
echo '[accesscount] 終了'
