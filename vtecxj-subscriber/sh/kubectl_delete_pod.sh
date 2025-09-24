#!/bin/bash

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`

# $1 : 削除Pod名
# $2 : サービスアカウント名
# $3 : サービスアカウント秘密鍵 (フルパス)
# $4 : プロジェクトID

POD=$1
SERVICE_ACCOUNT=$2
SERVICE_ACCOUNT_JSON=$3
GCP_PROJECT_ID=$4

echo '[kubectl_exec] POD='$POD', SERVICE_ACCOUNT='$SERVICE_ACCOUNT', SERVICE_ACCOUNT_JSON='$SERVICE_ACCOUNT_JSON', GCP_PROJECT_ID='$GCP_PROJECT_ID

# 定数読み込み
source ./settings.txt

# gcloud認証
gcloud auth activate-service-account $SERVICE_ACCOUNT --key-file $SERVICE_ACCOUNT_JSON --project $GCP_PROJECT_ID

# kubectlが使用できるようにする。
./setup-config-auth.sh

# Pod削除
kubectl delete pod $POD
