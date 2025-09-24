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

# $1 システム日時
DATETIME=$1

# バケットへのURL
URL_BUCKET='gs://'$BUCKET_NAME

# gcloud認証
gcloud auth activate-service-account $SERVICE_ACCOUNT --key-file $CLASSES_DIR/$SERVICE_ACCOUNT_JSON --project $PROJECT_ID --quiet

# kubectlが使用できるようにする。
source ./settings.txt
./setup-config-auth.sh

#echo '[exec_bdb_backup] start. datetime='$DATETIME

# Pod名取得
# $1: ポッド名接頭辞
function getPodName() {
  ARRAY=(`kubectl get pods | grep $1`)
  # 配列の表示は先頭を返す。
  # シェルのfunctionの戻り値は標準出力で返す。
  echo $ARRAY
}

# BDBバックアップ関数
# $1: バックアップフォルダ
# $2: サーバ名接頭辞
# $3: Deployment名接頭辞
# $4: サーバ一覧ファイルパス
# $5: バックアップ対象のサーバごとの名前空間リスト配置ディレクトリ
function backupBDB() {
  p_backup_folder=$1
  p_server_name_prefix=$2
  p_deployment_name_prefix=$3
  p_serverlist_filepath=$4
  p_backup_namespacelist_dir=$5

  url_bdb_stage=$URL_BUCKET$p_backup_folder'/'$STAGE

  #echo '[exec_bdb_backup] URL_BDB_STAGE='$url_bdb_stage

  # ${#変数名}で文字列長取得
  bdbServerPrefixLen=${#p_server_name_prefix}

  size=0
  while read line
  do
    #echo $line

    if [ -n "$line" ]; then
      bdbServer=`echo $line | cut -d $DELIMITER -f 1`

      # POD名を取得
      serverNo=${bdbServer:$bdbServerPrefixLen}
      podPrefix=$p_deployment_name_prefix$serverNo-
      pod=`getPodName $podPrefix`
      namespacesFilepath=$p_backup_namespacelist_dir'/'$bdbServer

      #echo '[exec_bdb_backup]→[proc] サーバ名='$bdbServer', POD='$pod

      # BDBバックアップ内部処理
      # $1 Cloud Storage URL (ステージまで)
      # $2 システム日時
      # $3 BDBサーバ名
      # $4 POD名
      # $5: バックアップ対象のサーバごとの名前空間リストファイル
      ./exec_bdb_backup_proc.sh $url_bdb_stage $DATETIME $bdbServer $pod $namespacesFilepath

    #else
      #echo 'blank line'
    fi

  done < $p_serverlist_filepath

}

# BDBバックアップ関数
# $1: バックアップフォルダ
# $2: サーバ名接頭辞
# $3: Deployment名接頭辞
# $4: サーバ一覧ファイルパス
# $5: バックアップ対象サービス・名前空間ディレクトリ
# function backupBDB() ...

# BDBバックアップ : Manifest
backupBDB $BACKUP_FOLDER_MNF $SERVER_NAME_PREFIX_MNF $SERVER_DEPLOYMENT_PREFIX_MNF $BDBSERVERLIST_FILEPATH_MNF $BACKUP_NAMESPACELIST_EACH_SERVER_DIRPATH_MNF
# BDBバックアップ : Entry
backupBDB $BACKUP_FOLDER_ENTRY $SERVER_NAME_PREFIX_ENTRY $SERVER_DEPLOYMENT_PREFIX_ENTRY $BDBSERVERLIST_FILEPATH_ENTRY $BACKUP_NAMESPACELIST_EACH_SERVER_DIRPATH_ENTRY
# BDBバックアップ : インデックス
backupBDB $BACKUP_FOLDER_IDX $SERVER_NAME_PREFIX_IDX $SERVER_DEPLOYMENT_PREFIX_IDX $BDBSERVERLIST_FILEPATH_IDX $BACKUP_NAMESPACELIST_EACH_SERVER_DIRPATH_IDX
# BDBバックアップ : 全文検索インデックス
backupBDB $BACKUP_FOLDER_FT $SERVER_NAME_PREFIX_FT $SERVER_DEPLOYMENT_PREFIX_FT $BDBSERVERLIST_FILEPATH_FT $BACKUP_NAMESPACELIST_EACH_SERVER_DIRPATH_FT
# BDBバックアップ : 採番・カウンタ
backupBDB $BACKUP_FOLDER_AL $SERVER_NAME_PREFIX_AL $SERVER_DEPLOYMENT_PREFIX_AL $BDBSERVERLIST_FILEPATH_AL $BACKUP_NAMESPACELIST_EACH_SERVER_DIRPATH_AL

#echo '[exec_bdb_backup] end'
