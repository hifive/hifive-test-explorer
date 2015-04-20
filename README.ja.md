Hifive Test Explorer
========
This is the Test Result Explorer tool which supports hifive-test-library. You
can easily access your test results and understand what is wrong, if these tests
are written with hifive-test-library.

This product is licensed under the [Apache License, Version 2.0][license].
Our developer site is located at [htmlhifive.com][].

Let's **hifive**!

[license]: http://www.apache.org/licenses/LICENSE-2.0)
[htmlhifive.com]: http://www.htmlhifive.com

### 前提
* [Eclipse IDE for Java EE (Luna SR2)][ide]
* [Tomcat 7][tomcat]
* [Sysdeo Eclipse Tomcat Launcher plugin (3.3.1)][plugin]
  * Set `Tomcat home` in `Window` → `Preferences` → `Tomcat` in *Eclipse*.
  * `com.sysdeo.eclipse.tomcat_3.3.1.jar`フォルダーにある`DevloaderTomcat7.jar`
    を`$TOMCAT_HOME/lib`へコピーすること。

[ide]: https://eclipse.org/downloads/packages/release/Luna/SR2
[tomcat]: http://tomcat.apache.org/download-70.cgi
[plugin]: http://www.eclipsetotale.com/tomcatPlugin.html

### Supported browsers
* Chrome *≥ 44*
* Internet Explorer 11
* Firefox *≥ 37*

### 手順
1.  `ivy_build.xml.launch`を選択し、右クリックし、`Run As` → `ivy_build.xml`で実
    行します。

2.  `hifive-test-explorer`プロジェクト - `src/main/resources` →
    `appConf`の`api-conf.properties.sample`をコピーして`api-conf.properties`を作
    ってその中身を書き換えます。テストデータを格納した`sampleData`フォルダはプロ
    ジェクトの直下にあります。各自の環境に合わせて絶対パスを書き換えてください。

    例）`resultsDir=C:\\hifive\\workspace\\hifive-test-explorer\\sampleData`

3.  Tomcatのコンテキスト定義を更新します。プロジェクトを選択し、右クリックし、
    `Tomcatプロジェクト` → `コンテキスト定義を更新`で実行します。

4.  Tomcatを起動します。

5.  下記にアクセスできることを確認してください。

    [http://localhost:8080/hifive-test-explorer/list.html][url-list]

[url-list]: http://localhost:8080/hifive-test-explorer/list.html

### About Web APIs
You can see the list of this application's APIs from
[http://localhost:8080/hifive-test-explorer/spec/api.html][url-api]

By clicking the buttons labeled "Get a sample result" in that page, you can see
the example data.

[url-api]: http://localhost:8080/hifive-test-explorer/spec/api.html

### APIドキュメント（JSDocドキュメント）の生成方法
1.  jsdoc3をダウンロード
    - jsdoc3はここからダウンロードできます。https://github.com/jsdoc3/jsdoc
    - Tagなどからすべてのファイルをダウンロードし、 hifive/jsTool/jsdoc/bin に配置します。
      ("jsdoc"コマンドが"bin"フォルダに存在するようにします。)

2.  生成
    - `build_for_js.xml`の`jsdoc`ターゲットを実行します。
      `hifive/src/main/webapp/doc`の下にドキュメントが生成されます。