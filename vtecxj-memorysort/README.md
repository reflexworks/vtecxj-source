vte.cx Memory sort
==========

vte.cx Memory sort

### jarファイルの抽出プロジェクト

依存するjarファイルの大規模更新があった場合など、jarを入れ替える場合は対象のプロジェクトディレクトリ直下で以下のコマンドを実行し、dependencyディレクトリ配下のjarファイルを取得し配置する。

```
mvn dependency:copy-dependencies -DincludeScope=compile -DincludeScope=runtime
```

対象プロジェクトは以下の通り

* vtecxj-memorysort
* vtecxj-redis
* vtecxj-secret

その他必要なjar

* logback-core-{version}.jar
* logback-classic-{version}.jar
