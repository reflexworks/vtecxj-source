#!/bin/bash

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`

# 定数読み込み
source ./batch_settings.txt
# 削除済みサービスのコンテンツ削除クラス(java)
CLEAN_STORAGE_CLASS="jp.reflexworks.batch.CleanStorageApp"

# 削除済みサービスのコンテンツ(bucket)削除
java -cp $CLASSPATH $JAVA_OPTIONS $CLEAN_STORAGE_CLASS $VALID_NAMESPACELIST_FILEPATH

