#!/bin/bash

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`

# Pod再起動処理
# OOM発生時にPub/SubのSubscriptionから呼び出される
# $1 : 削除Pod名
# $2 : サービスアカウント名 (2025.10.21廃止)
# $3 : サービスアカウント秘密鍵 (フルパス) (2025.10.21廃止)
# $4 : プロジェクトID (2025.10.21廃止)

POD=$1
#SERVICE_ACCOUNT=$2
#SERVICE_ACCOUNT_JSON=$3
#GCP_PROJECT_ID=$4

#echo '[kubectl_delete_pod] POD='$POD', SERVICE_ACCOUNT='$SERVICE_ACCOUNT', SERVICE_ACCOUNT_JSON='$SERVICE_ACCOUNT_JSON', GCP_PROJECT_ID='$GCP_PROJECT_ID
echo '[kubectl_delete_pod] POD='$POD

# 定数読み込み
#source ./settings.txt

# gcloud認証
#gcloud auth activate-service-account $SERVICE_ACCOUNT --key-file $SERVICE_ACCOUNT_JSON --project $GCP_PROJECT_ID

# kubectlが使用できるようにする。
#./setup-config-auth.sh

# Pod削除
kubectl delete pod $POD
