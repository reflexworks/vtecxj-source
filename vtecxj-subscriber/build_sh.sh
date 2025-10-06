#!/bin/bash

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`
DIR=`pwd`

# 定数読み込み
# build.txt を1階層上に配置する。
# build.txt の定数 VTECX_SUBSCRIBER には、コピー先ディレクトリを記述したファイルを絶対パスで指定する。
# 指定したファイルには、コピー先ディレクトリを記述する。コピー先が複数ある場合は改行して記述する。
# build.txt の定数 VTECX_SUBSCRIBER_SH には、シェル格納ディレクトリのコピー先を指定する。
source ../build.txt
copylist_sh=$VTECX_SUBSCRIBER_SH

echo '[build_sh] start'

# shディレクトリをコピー
size=0
while read line
do
  if [ -n "$line" ]; then
    cp -pr $DIR/sh $line/.
  fi

done < $copylist_sh

echo '[build_sh] end'
