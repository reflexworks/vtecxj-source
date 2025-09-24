#!/bin/bash

# 本ファイルが配置されているディレクトリに移動
cd `dirname $0`
DIR=`pwd`

# 定数読み込み
# build.txt を1階層上に配置する。
# build.txt の定数 VTECX_BIGQUERY には、コピー先ディレクトリを記述したファイルを絶対パスで指定する。
# 指定したファイルには、コピー先ディレクトリを記述する。コピー先が複数ある場合は改行して記述する。
source ../build.txt
copylist=$VTECX_BIGQUERY

echo '[build] start'
echo '[build] pom: '$DIR/pom.xml

# jar作成
mvn clean install

# 生成したファイル名をpom.xmlから抽出する
version=''
artifactId=''

# <version>
versionTagLen=9
# <artifactId>
artifactIdTagLen=12

# jarファイル名をpom.xmlから抽出
size=0
while read line
do
  if [ -n "$line" ]; then
    tmpline=`echo $line`
    len=${#tmpline}
    if [ -z "$version" ] && [ $len -ge $versionTagLen ]; then
      vtag=${line:0:$versionTagLen}
      if [ -n "$vtag" ] && [ "$vtag" = "<version>" ]; then
        tmp=${tmpline:$versionTagLen}
        version=${tmp%<*}
        echo '* version='$version
      fi
    fi
    if [ -z "$artifactId" ] && [ $len -ge $artifactIdTagLen ]; then
      atag=${line:0:$artifactIdTagLen}
      if [ -n "$atag" ] && [ "$atag" = "<artifactId>" ]; then
        tmp=${tmpline:$artifactIdTagLen}
        artifactId=${tmp%<*}
        echo '* artifactId='$artifactId
      fi
    fi
    if [ -n "$version" ] && [ -n "$artifactId" ]; then
      break
    fi
  fi

done < $DIR/pom.xml

jarName=$artifactId'-'$version'.jar'
echo 'jarName='$jarName
jarFile=$DIR'/target/'$jarName
echo 'jarFile='$jarFile

# jarファイルをコピー
size=0
while read line
do
  if [ -n "$line" ]; then
    echo '['$artifactId']'$line
    if [ ${line:0:1} != "#" ]; then
      cp -p $jarFile $line/.
    fi

  fi

done < $copylist

echo '[build] end'
