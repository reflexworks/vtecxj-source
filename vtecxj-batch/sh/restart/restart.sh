#!/bin/bash

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`

# 定数読み込み
source ./settings.txt
source ./restart_settings.txt

# APサーバ再起動
#   1. Dockerイメージtags
#   2. deployment.yaml ファイル名
#   3. deployment名
function restart() {
  REV=$1
  YAML=$2
  DEPLOYMENT=$3
  echo '[restart] REV='$REV

  #echo '** deployment.yaml にタグを設定'
  sed -i s/XXX/$REV/ $YAML

  #echo '** rollout restart'
  kubectl rollout restart deployments/$DEPLOYMENT

  #echo '** deployment.yaml のタグを元に戻す'
  sed -i s/$REV/XXX/ $YAML
}

cnt=0

#echo '** Dockerイメージ一覧を取得'
gcloud container images list-tags asia-northeast1-docker.pkg.dev/$GCP_PROJECT_ID/$ARTIFACTS_REPOSITORY/$GIT_PROJECT-$GIT_BRANCH | while read digest tags timestamp
do
  cnt=$((cnt+1))

  if [[ $cnt = 2 ]] ; then
  	restart $tags $YAML_VTECX_BASE $DEPLOYMENT_VTECX_BASE
  	restart $tags $YAML_VTECX_BURST $DEPLOYMENT_VTECX_BURST
  	exit
  fi
done
