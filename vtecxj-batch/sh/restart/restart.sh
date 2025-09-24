#!/bin/bash

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`

# 定数読み込み
source ./settings.txt
source ./restart_settings.txt

# APサーバ再起動
#   1. Dockerイメージtags
function restart() {
  REV=$1
  echo '[restart] REV='$REV

  #echo '** deployment.yaml にタグを設定'
  sed -i s/XXX/$REV/ vtecx-deployment.yaml

  #echo '** rollout restart'
  kubectl rollout restart deployments/$DEPLOYMENT_VTECX

  #echo '** deployment.yaml のタグを元に戻す'
  sed -i s/$REV/XXX/ vtecx-deployment.yaml
}

cnt=0

#echo '** gcloud認証'
gcloud auth activate-service-account $SERVICE_ACCOUNT --key-file $CLASSES_DIR/$SERVICE_ACCOUNT_JSON --project $GCP_PROJECT_ID --quiet

#echo '** kubectlが使用できるようにする。'
./setup-config-auth.sh

#echo '** Dockerイメージ一覧を取得'
gcloud container images list-tags asia-northeast1-docker.pkg.dev/$GCP_PROJECT_ID/$ARTIFACTS_REPOSITORY/$GIT_PROJECT-$GIT_BRANCH | while read digest tags timestamp
do
  cnt=$((cnt+1))

  if [[ $cnt = 2 ]] ; then
  	restart $tags
  	exit
  fi
done
