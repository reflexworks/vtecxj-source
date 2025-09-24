#!/bin/bash

# $1: 名前空間
# $2: Cloud Storage URL
#     * 日次バックアップ先 : gs://{bucket}/{mnf|entry|idx|ft|al}_backup//{stage}/{BDBサーバ名}/{yyyyMMddHHmmss}/{namespace}
#     * データ移行の一時バックアップ先 : gs://{bucket}/temp_backup_for_migration/{stage}/{yyyyMMddHHmmss}/{namespace}/{mnf|entry|idx|ft|al}/{サーバ名}
NAMESPACE=$1
STORAGE_URL=$2

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`

# 各BDBサーバに以下のファイルを配置する。以下の定義を行う。
#  $VTECNAME : vte.cxプロジェクト名(プロパティ名・クラスパスの階層に使用。)
source ./bdb_settings.txt

# プロパティファイルパス
PROP_FILENAME=$VTECNAME'.properties'
JETTY_HOME='/usr/local/jetty'
CLASSES_DIR=$JETTY_HOME'/webapps/'$VTECNAME'/WEB-INF/classes'
PROPFILE=$CLASSES_DIR'/'$PROP_FILENAME

# プロパティファイル読み込み
# Usage: getProperty KEY
function getProperty() {
    grep "^$1=" "$PROPFILE" | cut -d'=' -f2
}

STAGE=`getProperty '_env.stage'`
PROJECT_ID=`getProperty '_gcp.projectid'`
GCLOUD_DIR=`getProperty '_gcloud.dir'`
SERVICE_ACCOUNT=`getProperty '_storage.service.account'`
JSON_KEY=$CLASSES_DIR/`getProperty '_storage.file.secret'`
BDB_HOME=`getProperty '_bdb.dir'`

# BDBログファイルディレクトリ : {_bdb.dir}/{stage}/{namespace}
BDB_DIR=$BDB_HOME'/'$STAGE'/'$NAMESPACE

echo '[bdb_backup] BDB_DIR='$BDB_DIR' -> STORAGE_URL='$STORAGE_URL

# gcloud認証
# --quiet オプションを付けているが、各BDBサーバではなぜか標準エラー出力にメッセージが出力されるためログに結果が出力されてしまう。
#   → エラーの場合にログ出力されないと調査できないのでこのままとする。
$GCLOUD_DIR/gcloud auth activate-service-account $SERVICE_ACCOUNT --key-file $JSON_KEY --project $PROJECT_ID --quiet

# バックアップ
$GCLOUD_DIR/gsutil -m -q cp -r $BDB_DIR $STORAGE_URL
