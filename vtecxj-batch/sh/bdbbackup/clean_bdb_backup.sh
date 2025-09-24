#!/bin/bash

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`

# 定数読み込み
source ./batch_settings.txt
# Google credentials をシェル変数から環境変数へexportする。
#export GOOGLE_APPLICATION_CREDENTIALS

# $1 システム日時
DATETIME=$1

#echo '[clean_bdb_backup] start. datetime='$DATETIME

# バックアップ数を超えた古い履歴を削除
# Manifestサーバデータ

size=0
while read line
do
  #echo $line

  if [ -n "$line" ]; then
    BDBSERVER=`echo $line | cut -d $DELIMITER -f 1`
    #echo '[clean_bdb_backup]→[proc] '$BACKUP_FOLDER_MNF' Manifestサーバ名='$BDBSERVER

    # $1: BDBサーバ名
    # $2: システム日時（これ以前の日時が削除対象）
    # $3: バックアップディレクトリ (全文検索用)
    ./clean_backup.sh $BDBSERVER $DATETIME $BACKUP_FOLDER_MNF
  #else
    #echo 'blank line'
  fi

done < $BDBSERVERLIST_FILEPATH_MNF

# Entryサーバデータ

size=0
while read line
do
  #echo $line

  if [ -n "$line" ]; then
    BDBSERVER=`echo $line | cut -d $DELIMITER -f 1`
    #echo '[clean_bdb_backup]→[proc] '$BACKUP_FOLDER_ENTRY' Entryサーバ名='$BDBSERVER

    # $1: BDBサーバ名
    # $2: システム日時（これ以前の日時が削除対象）
    # $3: バックアップディレクトリ (全文検索用)
    ./clean_backup.sh $BDBSERVER $DATETIME $BACKUP_FOLDER_ENTRY
  #else
    #echo 'blank line'
  fi

done < $BDBSERVERLIST_FILEPATH_ENTRY

# インデックスサーバデータ

size=0
while read line
do
  #echo $line

  if [ -n "$line" ]; then
    BDBSERVER=`echo $line | cut -d $DELIMITER -f 1`
    #echo '[clean_bdb_backup]→[proc] '$BACKUP_FOLDER_IDX' インデックスサーバ名='$BDBSERVER

    # $1: BDBサーバ名
    # $2: システム日時（これ以前の日時が削除対象）
    # $3: バックアップディレクトリ (全文検索用)
    ./clean_backup.sh $BDBSERVER $DATETIME $BACKUP_FOLDER_IDX
  #else
    #echo 'blank line'
  fi

done < $BDBSERVERLIST_FILEPATH_IDX

# 全文検索インデックスサーバデータ

size=0
while read line
do
  #echo $line

  if [ -n "$line" ]; then
    BDBSERVER=`echo $line | cut -d $DELIMITER -f 1`
    #echo '[clean_bdb_backup]→[proc] '$BACKUP_FOLDER_FT' 全文検索インデックスサーバ名='$BDBSERVER

    # $1: BDBサーバ名
    # $2: システム日時（これ以前の日時が削除対象）
    # $3: バックアップディレクトリ (全文検索用)
    ./clean_backup.sh $BDBSERVER $DATETIME $BACKUP_FOLDER_FT
  #else
    #echo 'blank line'
  fi

done < $BDBSERVERLIST_FILEPATH_FT

# 採番・カウンタサーバデータ

size=0
while read line
do
  #echo $line

  if [ -n "$line" ]; then
    BDBSERVER=`echo $line | cut -d $DELIMITER -f 1`
    #echo '[clean_bdb_backup]→[proc] '$BACKUP_FOLDER_AL' 採番・カウンタサーバ名='$BDBSERVER

    # $1: BDBサーバ名
    # $2: システム日時（これ以前の日時が削除対象）
    # $3: バックアップディレクトリ (全文検索用)
    ./clean_backup.sh $BDBSERVER $DATETIME $BACKUP_FOLDER_AL
  #else
    #echo 'blank line'
  fi

done < $BDBSERVERLIST_FILEPATH_AL

#echo '[clean_bdb_backup] end'

