#!/bin/bash

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`

# 定数読み込み
source ./settings.txt
source ./restart_settings.txt

# APサーバ再起動
#   1. deployment名
function restart() {
  DEPLOYMENT=$1
  echo '[restart] DEPLOYMENT='$DEPLOYMENT

  #echo '** rollout restart'
  kubectl rollout restart deployments/$DEPLOYMENT
}

restart $DEPLOYMENT_VTECX_BASE
restart $DEPLOYMENT_VTECX_BURST
