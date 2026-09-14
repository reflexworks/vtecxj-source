#!/bin/bash

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`

# 定数読み込み
source ./checkservices_settings.txt
# サービス単位の定期チェック処理リクエストクラス(java)
# (メッセージキュー未送信チェック・BDBQリトライチェック・バッチジョブ実行管理を統合)
CHECKSERVICES_CLASS="jp.reflexworks.batch.CheckServicesApp"

#echo 'CLASSPATH='$CLASSPATH

# サービス単位の定期チェック処理リクエスト
java -cp $CLASSPATH $JAVA_OPTIONS $CHECKSERVICES_CLASS
