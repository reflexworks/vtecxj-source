#!/bin/bash

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`

source ./settings.txt

echo "cluster: " $CLUSTER
echo "projectId: " $GCP_PROJECT_ID

gcloud config set project $GCP_PROJECT_ID --quiet
gcloud config set container/cluster $CLUSTER --quiet

gcloud config list
