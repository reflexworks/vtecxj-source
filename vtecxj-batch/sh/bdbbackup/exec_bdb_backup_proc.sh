#!/bin/bash

# exec_bdb_backup.sh から呼び出されるシェル
# $1 Cloud Storage URL (ステージまで)
# $2 システム日時
# $3 BDBサーバ名
# $4 POD名
# $5 バックアップ対象のサーバごとの名前空間リストファイル

# 定数読み込み
source ./batch_settings.txt

# BDBバックアップコマンドパス
BDB_BACKUP_SH_PATH='/var/vtecx/sh/bdb_backup.sh'

URL_STAGE=$1
DATETIME=$2
SERVERNAME=$3
POD=$4
NAMESPACES_FILEPATH=$5

# ${変数名//置換前文字列/置換後文字列} → 文字列置換(マッチしたものすべて)
#namespacesFilepath=${BACKUP_NAMESPACELIST_EACH_SERVER_FILEPATH}/@/$SERVERNAME}

#echo '[exec_bdb_backup] serverName='$SERVERNAME', pod='$POD', namespacesFilepath='$NAMESPACES_FILEPATH

# 名前空間ごとに処理を行う
if [ -e $NAMESPACES_FILEPATH ]; then

  # BDBバックアップ処理

  # Cloud Storage URL
  # gs://{bucketName}/{バックアップフォルダ}/{stage}/{サーバ名}/{yyyyMMddHHmmss}/{namespace}

  STORAGE_URL_DATETIME=$URL_STAGE'/'$SERVERNAME'/'$DATETIME

  size=0
  while read line
  do
    #echo $line

    if [ -n "$line" ]; then
      NAMESPACE=`echo $line | cut -d $DELIMITER -f 2`

      # BDBバックアップシェル
      # $1: 名前空間
      # $2: Cloud Storage URL

      STORAGE_URL=$STORAGE_URL_DATETIME'/'$NAMESPACE

      echo '[exec_bdb_backup] serverName='$SERVERNAME' →[bdb_backup] namespace='$NAMESPACE' storage url='$STORAGE_URL

      kubectl exec $POD -- $BDB_BACKUP_SH_PATH $NAMESPACE $STORAGE_URL

    #else
      #echo 'blank line'
    fi

  done < $NAMESPACES_FILEPATH

  #echo '[exec_bdb_backup] serverName='$SERVERNAME' end.'

else
  echo '[exec_bdb_backup] serverName='$SERVERNAME' The namespaces file does not exist.'
fi
