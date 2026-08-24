#!/bin/bash

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`

# 定数読み込み
source ./batch_settings.txt
# 一定期間経過した削除済みサービスの情報削除クラス(java)
DELETE_DELETED_SERVICEINFO_CLASS="jp.reflexworks.batch.DeleteDeletedServiceInfoApp"

# 一定期間経過した削除済みサービスの情報削除
java -cp $CLASSPATH $JAVA_OPTIONS $DELETE_DELETED_SERVICEINFO_CLASS $VALID_NAMESPACELIST_FILEPATH
