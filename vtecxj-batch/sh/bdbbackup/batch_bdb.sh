#!/bin/bash

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`

# 夜間バッチstart
echo '[bdb batch] 夜間バッチstart 開始'
./start_batch_bdb.sh
echo '[bdb batch] 夜間バッチstart 終了'

# 一覧をファイルに出力 (有効なサービスと名前空間、BDBサーバ)
echo '[bdb batch] 一覧をファイルに出力 開始'
./write_list.sh
echo '[bdb batch] 一覧をファイルに出力 終了'

# BDBに登録されたログEntryをBigQueryに移動
echo '[bdb batch] BDBに登録されたログEntryをBigQueryに移動 開始'
./move_log_to_bigquery.sh
echo '[bdb batch] BDBに登録されたログEntryをBigQueryに移動 終了'

# BDBクリーンリクエスト処理
echo '[bdb batch] BDBクリーンリクエスト 開始'
./request_clean_bdb.sh
echo '[bdb batch] BDBクリーンリクエスト 終了'

DATETIME=`date "+%Y%m%d%H%M%S"`
echo '[batch_bdb] datetime='$DATETIME

# BDBバックアップ実行処理
echo '[bdb batch] BDBバックアップ実行 開始'
./exec_bdb_backup.sh $DATETIME
echo '[bdb batch] BDBバックアップ実行 終了'

# 古いBDBバックアップ削除処理
echo '[bdb batch] 古いBDBバックアップ削除 開始'
./clean_bdb_backup.sh $DATETIME
echo '[bdb batch] 古いBDBバックアップ削除 終了'

# 削除済みサービスのコンテンツ削除
echo '[bdb batch] 削除済みサービスのコンテンツ削除 開始'
./clean_storage.sh
echo '[bdb batch] 削除済みサービスのコンテンツ削除 終了'

# BDBディスク使用率チェック
echo '[bdb batch] BDBディスク使用率チェック 開始'
./check_bdb_disk_usage.sh
echo '[bdb batch] BDBディスク使用率チェック 終了'

# 夜間バッチend
echo '[bdb batch] 夜間バッチend 開始'
./end_batch_bdb.sh
echo '[bdb batch] 夜間バッチend 終了'
