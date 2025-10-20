vte.cx 初期データ登録処理
==========

vte.cxの初期データを登録するバッチです。

## 動作環境

* GKEクラスタ上で実行する
* vte.cxの各BDBサーバが起動していること
  * entryサーバ
  * maniestサーバ
  * indexサーバ
  * allocidsサーバ
  * 全文検索indexサーバ

## 設定

クラスパス上にプロパティファイルを用意し、そのファイルに各設定を定義しておく。

### プロパティファイル名

vtecxinit.properties

### 設定内容

#### 初期設定

```
# システム管理サービスのAPIKEY
_init.systemservice.apikey={システム管理サービスのAPIKEY}
# システム管理サービスの管理ユーザメールアドレス
_init.systemservice.email={システム管理サービスの管理ユーザメールアドレス}
# システム管理サービスの管理ユーザパスワード
_init.systemservice.password={システム管理サービスの管理ユーザパスワード}
# 各BDBサーバ登録情報
# Entryサーバのサーバ名とホスト名を指定。複数指定可能。
_init.bdbserver.entry.{サーバ名}={ホスト名}
# Manifestサーバのサーバ名とホスト名を指定。複数指定可能。
_init.bdbserver.manifest.{サーバ名}={ホスト名}
# Indexサーバのサーバ名とホスト名を指定。複数指定可能。
_init.bdbserver.index.{サーバ名}={ホスト名}
# 採番・カウンタサーバのサーバ名とホスト名を指定。複数指定可能。
_init.bdbserver.allocids.{サーバ名}={ホスト名}
# 全文検索Indexサーバのサーバ名とホスト名を指定。複数指定可能。
_init.bdbserver.fulltextsearch.{サーバ名}={ホスト名}
```

#### vte.cx環境設定

```
# プラグイン
# データストア管理プラグインクラス名
_plugin.datastoremanager=jp.reflexworks.taggingservice.bdbclient.BDBClientManager
# 採番管理プラグインクラス名
_plugin.allocateidsmanager=jp.reflexworks.taggingservice.bdbclient.BDBClientAllocateIdsManager
# 加算管理プラグインクラス名
_plugin.incrementmanager=jp.reflexworks.taggingservice.bdbclient.BDBClientIncrementManager
# キャッシュ管理プラグインクラス名
_plugin.cachemanager=jp.reflexworks.taggingservice.redis.JedisCacheManager
# セッション管理プラグインクラス名
_plugin.sessionmanager=jp.reflexworks.taggingservice.redis.JedisSessionManager
# RXID管理プラグインクラス名
_plugin.rxidmanager=jp.reflexworks.taggingservice.redis.JedisRXIDManager
# 認証管理プラグインクラス名
_plugin.authenticationmanager=jp.reflexworks.taggingservice.auth.TaggingAuthenticationManager
# ユーザ管理プラグインクラス名
_plugin.usermanager=jp.reflexworks.taggingservice.auth.TaggingUserManager
# シークレット管理プラグインクラス名
_plugin.secretmanager=jp.reflexworks.taggingservice.secret.ReflexSecretManager

# サーバタイプ
_reflex.servertype=patch

# 環境ステージ名
# すべてのサーバで同一の値を設定する必要あり。デフォルト値は”main”。
_env.stage={環境ステージ名}

# システム管理サービスのBDBサーバ {サーバ名}=http://{ホスト名}/b
_bdbrequest.url.system.manifest.{サーバ名}=http://{ホスト名}/b
_bdbrequest.url.system.entry.{サーバ名}=http://{ホスト名}/b
_bdbrequest.url.system.index.{サーバ名}=http://{ホスト名}/b
_bdbrequest.url.system.fulltext.{サーバ名}=http://{ホスト名}/b
_bdbrequest.url.system.allocids.{サーバ名}=http://{ホスト名}/b

# Google Cloud ProjectID
_gcp.projectid={Google CloudのプロジェクトID}

# Secret Manager シークレットアクセサー権限を持つサービスアカウントのJSON鍵
_secret.file.secret={サービスアカウントのJSON鍵}

# 暗号化キーのシークレット名
_secret.secretkey.name={シークレット名}

# Redisインスタンスの接続先
_redis.host.master={IPアドレス}:{ポート番号}

```