/**
 * pitalium-explorer
 */
node {
	stage('レジカウンター')
    deleteDir()
    git(
        branch: BRANCH_NAME,
        url: 'https://github.com/hifive/hifive-pitalium-explorer.git'
    )

	stage('ライブラリをダウンロード')
	def antHome = tool(name: 'Default_Ant')
    withEnv(["ANT_OPTS=-Dhttp.proxyHost=${IVY_PROXY_HOST} -Dhttp.proxyPort=${IVY_PROXY_PORT} -Dhttp.proxyUser=${IVY_PROXY_USER} -Dhttp.proxyPassword=${IVY_PROXY_PASSWORD}"]) {
        bat("${antHome}/bin/ant.bat -file pitalium-explorer/ivy_build.xml resolve && exit %%ERRORLEVEL%%")
    }

	stage('ビルド + テスト + WARの生成')
    bat("${antHome}/bin/ant.bat -file pitalium-explorer/ci_build.xml clean test_instrument unit_test build-war && exit %%ERRORLEVEL%%")
	// コンパイル済のソースコードを含むワークスペース全体を保存
	step(
		$class: 'JUnitResultArchiver',
		testResults: 'pitalium-explorer/target/work/test-reports/*.xml'
	)

    stage('成果物の保存')
    if(BACKUP_PATH != '') {
        bat("copy /Y pitalium-explorer\\build\\pitalium-explorer.war ${BACKUP_PATH} && exit 0")
    }
}