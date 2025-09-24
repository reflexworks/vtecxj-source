#!/bin/bash

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`

# 定数読み込み
source ./batch_settings.txt
# BDBクリーンリクエストクラス(java)
REQUEST_CLEAN_BDB_CLASS="jp.reflexworks.batch.CleanBDBApp"

# システム日時を yyyyMMddHHmmss 形式で取得。(時間確認のためだけに使用。BDBバックアップバッチの後続の処理とは無関係)
DATETIME=`date "+%Y%m%d%H%M%S"`
echo '[request_clean_bdb] start. datetime='$DATETIME

# $!は、バックグラウンドで最も直近に実行された最後のジョブのPID

# ManifestBDBの対象名前空間データ削除、クリーン
# [0]Manifestサーバ一覧ファイル名(サーバ名:URL)(フルパス)
# [1]有効なサービスのサーバごとの名前空間一覧格納ディレクトリ(フルパス)
#    配下のファイルの内容は(サービス名:名前空間)のリスト
java -cp $CLASSPATH $JAVA_OPTIONS $REQUEST_CLEAN_BDB_CLASS $BDBSERVERLIST_FILEPATH_MNF $VALID_NAMESPACELIST_EACH_SERVER_DIRPATH_MNF &
array[0]=$!
echo "clean bdb async pid (mnf): ${array[0]}"

# EntryBDBの対象名前空間データ削除、クリーン
# [0]Entryサーバ一覧ファイル名(サーバ名:URL)(フルパス)
# [1]有効なサービスのサーバごとの名前空間一覧格納ディレクトリ(フルパス)
#    配下のファイルの内容は(サービス名:名前空間)のリスト
java -cp $CLASSPATH $JAVA_OPTIONS $REQUEST_CLEAN_BDB_CLASS $BDBSERVERLIST_FILEPATH_ENTRY $VALID_NAMESPACELIST_EACH_SERVER_DIRPATH_ENTRY &
array[1]=$!
echo "clean bdb async pid (entry): ${array[1]}"

# インデックスBDBの対象名前空間データ削除、クリーン
# [0]インデックスサーバ一覧ファイル名(サーバ名:URL)(フルパス)
# [1]有効なサービスのサーバごとの名前空間一覧格納ディレクトリ(フルパス)
#    配下のファイルの内容は(サービス名:名前空間)のリスト
java -cp $CLASSPATH $JAVA_OPTIONS $REQUEST_CLEAN_BDB_CLASS $BDBSERVERLIST_FILEPATH_IDX $VALID_NAMESPACELIST_EACH_SERVER_DIRPATH_IDX &
array[2]=$!
echo "clean bdb async pid (idx): ${array[2]}"

# 全文検索インデックスBDBの対象名前空間データ削除、クリーン
# [0]全文検索インデックスサーバ一覧ファイル名(サーバ名:URL)(フルパス)
# [1]有効なサービスのサーバごとの名前空間一覧格納ディレクトリ(フルパス)
#    配下のファイルの内容は(サービス名:名前空間)のリスト
java -cp $CLASSPATH $JAVA_OPTIONS $REQUEST_CLEAN_BDB_CLASS $BDBSERVERLIST_FILEPATH_FT $VALID_NAMESPACELIST_EACH_SERVER_DIRPATH_FT &
array[3]=$!
echo "clean bdb async pid (ft): ${array[3]}"

# 採番・カウンタBDBの対象名前空間データ削除、クリーン
# [0]採番・カウンタサーバ一覧ファイル名(サーバ名:URL)(フルパス)
# [1]有効なサービスのサーバごとの名前空間一覧格納ディレクトリ(フルパス)
#    配下のファイルの内容は(サービス名:名前空間)のリスト
java -cp $CLASSPATH $JAVA_OPTIONS $REQUEST_CLEAN_BDB_CLASS $BDBSERVERLIST_FILEPATH_AL $VALID_NAMESPACELIST_EACH_SERVER_DIRPATH_AL &
array[4]=$!
echo "clean bdb async pid (al): ${array[4]}"

# 非同期処理の終了を待つ
wait ${array[@]}
echo "clean bdb async finish."
