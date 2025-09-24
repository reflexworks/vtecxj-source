#!/bin/bash

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`

# 定数読み込み
source ./batch_settings.txt

# プロパティファイル読み込み
# Usage: getProperty KEY
function getProperty() {
    grep "^$1=" "$PROPFILE" | cut -d'=' -f2
}

STAGE=`getProperty '_env.stage'`
PROJECT_ID=`getProperty '_gcp.projectid'`

# $1: BDBサーバ名
# $2: システム日時（これ以前の日時が削除対象）
# $3: バックアップディレクトリ (BDB用 or 全文検索用)
BDBSERVER=$1
DATETIME=$2
BACKUP_DIR=$3

#echo '[clean backup] '$BACKUP_DIR': bdbserver='$BDBSERVER', datetime='$DATETIME

# gs://{bucket}/{バックアップディレクトリ}/{stage}/{BDBサーバ名}/{yyyyMMddHHmmss}/{namespace}
# のBDBサーバ名まで
GS_BACKUP_URL='gs://'$BUCKET_NAME$BACKUP_DIR'/'$STAGE'/'$BDBSERVER'/'
DATE_URL=$GS_BACKUP_URL$DATETIME'/'

# gcloud認証
gcloud auth activate-service-account $SERVICE_ACCOUNT --key-file $CLASSES_DIR/$SERVICE_ACCOUNT_JSON --project $PROJECT_ID --quiet

# クリーンアップ
# Storageからバックアップ一覧フォルダを取得
backuplist=`gsutil ls $GS_BACKUP_URL`

sortlist=`sort -r <<END
$backuplist
END`

#sortlist=`$GCLOUD_DIR/gsutil ls $GS_BACKUP_URL | sort -r`

#echo '[clean backup] backup_num='$BACKUP_NUM

count=$(( $BACKUP_NUM-1 ))

#echo '[clean backup] date_url = '$DATE_URL

while read line
do
  isEndStr=`expr "END" "=" "$line"`
  if [ $isEndStr -ne 1 ]; then
    isOld=`expr "$DATE_URL" ">" "$line"`
    #echo 'sortlist line = '$line' isOld='$isOld
    if [ $isOld -eq 1 ]; then
      count=$(( $count-1 ))
      if [ $count -lt 0 ]; then
        #Storageのフォルダ削除
        echo '[clean backup] delete: '$line
        gsutil -m -q rm -r $line
      fi
    fi
  fi

done <<END
$sortlist
END
