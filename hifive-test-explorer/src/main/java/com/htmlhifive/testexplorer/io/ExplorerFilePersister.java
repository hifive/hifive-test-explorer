/*
 * Copyright (C) 2015 NS Solutions Corporation, All Rights Reserved.
 */
package com.htmlhifive.testexplorer.io;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.htmlhifive.testexplorer.entity.Area;
import com.htmlhifive.testexplorer.entity.Screenshot;
import com.htmlhifive.testexplorer.entity.Target;
import com.htmlhifive.testexplorer.entity.TestEnvironment;
import com.htmlhifive.testexplorer.entity.TestExecution;
import com.htmlhifive.testexplorer.response.TestExecutionResult;
import com.htmlhifive.testlib.core.config.FilePersisterConfig;
import com.htmlhifive.testlib.core.io.FilePersister;
import com.htmlhifive.testlib.core.io.PersistMetadata;
import com.htmlhifive.testlib.core.model.ScreenAreaResult;
import com.htmlhifive.testlib.core.model.ScreenshotResult;
import com.htmlhifive.testlib.core.model.TargetResult;
import com.htmlhifive.testlib.core.model.TestResult;
import com.htmlhifive.testlib.core.selenium.MrtCapabilities;

public class ExplorerFilePersister extends FilePersister implements ExplorerPersister {

	private static Logger log = LoggerFactory.getLogger(ExplorerFilePersister.class);

	private Map<Integer, Screenshot> screenshotMap;
	private Map<Integer, List<Screenshot>> screenshotListMap;
	private Map<Integer, Target> targetMap;
	
	public ExplorerFilePersister() {
		// FIXME 独自のConfigに差し替える必要があるかも
		super();
	}

	public ExplorerFilePersister(FilePersisterConfig config) {
		// FIXME 独自のConfigに差し替える必要があるかも
		super(config);
	}

	@Override
	public Page<TestExecutionResult> findTestExecution(String searchTestMethod, 
			String searchTestScreen, int page, int pageSize) {
		// TODO:検索には未対応
		File root = super.getResultDirectoryFile();
		if (!root.exists() || !root.isDirectory()) {
			log.error("Directory(" + root.getAbsolutePath() + ") Not Found.");
			return new PageImpl<>(new ArrayList<TestExecutionResult>());
		}

		IOFileFilter filter = FileFilterUtils.nameFileFilter(getTestResultFileName());
		Collection<File> collection = 
				FileUtils.listFiles(root, filter, TrueFileFilter.INSTANCE);

		screenshotMap = new HashMap<>();
		screenshotListMap = new HashMap<>();
		targetMap = new HashMap<>();

		// 後続のページング処理用
		List<TestExecution> testExecutionList = new ArrayList<>();
		// Screenshotの関連を貼るための処理用
		Map<String, List<Screenshot>> workScreenshotListMap = new HashMap<>();
		Map<ScreenshotResult, Screenshot> workScreenshotMap = new HashMap<>();
		
		int executionId = 0;
		int screenshotId = 0;
		int targetId = 0;
		int areaId = 0;
		
		File[] files = collection.toArray(new File[collection.size()]);
		for (int i = 0, len = files.length; i < len; i++) {
			String executionDate = files[i].getParentFile().getParentFile().getName();
			TestExecution testExecution = new TestExecution();
			testExecution.setId(executionId);
			DateTime dateTime = DateTimeFormat.forPattern("yyyy_MM_dd_HH_mm_ss")
					.parseDateTime(executionDate);
			testExecution.setTime(new Timestamp(dateTime.getMillis()));
			
			// 重複チェック
			boolean exists = false;
			for (TestExecution exec : testExecutionList) {
				if (StringUtils.equals(exec.getTimeString(), testExecution.getTimeString())) {
					exists = true;
					testExecution = exec;
					break;
				}
			}

			if (!exists) {
				testExecutionList.add(testExecution);
				executionId++;
			}

			PersistMetadata metadata = new PersistMetadata(
					executionDate, files[i].getParentFile().getName());
			TestResult testResult = super.loadTestResult(metadata);
			
			List<Screenshot> screenshotList = new ArrayList<>();
			for (ScreenshotResult screenshotResult : testResult.getScreenshotResults()) {
				Screenshot screenshot = createScreenshot(screenshotId, screenshotResult);
				screenshot.setTestExecution(testExecution);
				
				// Capability
				Map<String, ?> capabilities = screenshotResult.getCapabilities();
				TestEnvironment testEnvironment = createTestEnvironment(capabilities);
				screenshot.setTestEnvironment(testEnvironment);

				// Target
				List<TargetResult> targetResultList = screenshotResult.getTargetResults();
				List<Target> targetList = new ArrayList<>();
				for (TargetResult targetResult : targetResultList) {
					Target target = createTarget(targetId, screenshotId, targetResult, screenshotResult);
					targetList.add(target);

					// 比較対象の情報
					ScreenAreaResult screenAreaResult = targetResult.getTarget();
					Area area = createArea(areaId, targetId, screenAreaResult, false);
					areaId++;
					target.setArea(area);

					// 比較除外対象の情報
					List<ScreenAreaResult> screenAreaResultList = targetResult.getExcludes();
					List<Area> exculdeAreaList = new ArrayList<>();
					for (ScreenAreaResult excludeScreenAreaResult : screenAreaResultList) {
						Area excludeArea = createArea(areaId, targetId, excludeScreenAreaResult, true);
						exculdeAreaList.add(excludeArea);
						areaId++;
					}
					target.setExcludeAreas(exculdeAreaList);
					targetMap.put(targetId, target);
					targetId++;
				}
				screenshot.setTargets(targetList);
				
				screenshotMap.put(screenshotId, screenshot);
				screenshotList.add(screenshot);
				screenshotId++;

				workScreenshotMap.put(screenshotResult, screenshot);
			}
			// TestExecution.id をキーとして格納する。
			List<Screenshot> list = screenshotListMap.get(testExecution.getId());
			if (list == null) {
				list = new ArrayList<>();
				screenshotListMap.put(testExecution.getId(), list);
			}
			list.addAll(screenshotList);
			// 実行日時をキーとして格納する。
			workScreenshotListMap.put(testExecution.getTimeString(), list);
		}
		
		// Screenshot同士の関連付けを行う。
		for (ScreenshotResult result : workScreenshotMap.keySet()) {
			String expectedId = result.getExpectedId();
			if (expectedId == null) {
				continue;
			}
			Screenshot screenshot = workScreenshotMap.get(result);
			
			// 期待値の実行日時と一致するScreenshotのリストを取得し、関連付けを行う。
			List<Screenshot> screenshotList = workScreenshotListMap.get(expectedId);
			for (Screenshot expectedScreenshot : screenshotList) {
				if (StringUtils.equals(expectedScreenshot.getTestClass(), screenshot.getTestClass()) &&
						StringUtils.equals(expectedScreenshot.getFileName(), screenshot.getFileName())) {
					screenshot.setExpectedScreenshot(expectedScreenshot);
					break;
				}
			}
		}

		int size = testExecutionList.size();
		
		// 最新から並ぶようにソートする。
		List<TestExecution> tempTestExecutionList = new ArrayList<>();
		for (int i = size - 1; i >= 0 ; i--) {
			tempTestExecutionList.add(testExecutionList.get(i));
		}
		testExecutionList = tempTestExecutionList;

		
		if (pageSize == 0) {
			pageSize = defaultPageSize;
		} else if (pageSize == -1) {
			pageSize = (int) Math.min(size, Integer.MAX_VALUE);
		}

		// 表示ページ番号、ページ表示数に合わせてリストを作成する。
		List<TestExecutionResult> resultList = new ArrayList<TestExecutionResult>();
		for (int i = (page - 1) * pageSize; i < Math.min(page * pageSize, size); i++) {
			TestExecution execution = testExecutionList.get(i);
			List<Screenshot> list = screenshotListMap.get(execution.getId());

			int passedCount = 0;
			int totalCount = 0;
			for (Screenshot s : list) {
				totalCount++;
				if (s.getComparisonResult() != null && s.getComparisonResult().booleanValue()) {
					passedCount++;
				}
			}
			TestExecutionResult testExecutionResult = new TestExecutionResult(
					execution, Long.valueOf(passedCount), Long.valueOf(totalCount));
			resultList.add(testExecutionResult);
		}

		PageRequest pageable = new PageRequest(page - 1, pageSize);
		return new PageImpl<TestExecutionResult>(resultList, pageable, size);
	}

	private Screenshot createScreenshot(Integer screenshotId, ScreenshotResult screenshotResult) {
		Screenshot screenshot = new Screenshot();
		screenshot.setId(screenshotId);
		screenshot.setComparisonResult(screenshotResult.getResult() != null ? screenshotResult.getResult().isSuccess() : null);
		screenshot.setTestClass(screenshotResult.getTestClass());
		screenshot.setTestMethod(screenshotResult.getTestMethod());
		screenshot.setTestScreen(screenshotResult.getScreenshotId());

		PersistMetadata screenshotMetadata = new PersistMetadata(screenshotResult.getExpectedId(), 
				screenshotResult.getTestClass(), screenshotResult.getTestMethod(), 
				screenshotResult.getScreenshotId(), new MrtCapabilities(screenshotResult.getCapabilities()));
		screenshot.setFileName(getScreenshotImageFileName(screenshotMetadata));
		return screenshot;
	}
	
	private TestEnvironment createTestEnvironment(Map<String, ?> capabilities) {
		TestEnvironment testEnvironment = new TestEnvironment();
		testEnvironment.setBrowserName((String)capabilities.get("browserName"));
		testEnvironment.setBrowserVersion((String)capabilities.get("version"));
		testEnvironment.setDeviceName((String)capabilities.get("deviceName"));
		testEnvironment.setId(null);
		testEnvironment.setLabel(null);
		testEnvironment.setPlatform((String)capabilities.get("platform"));
		testEnvironment.setPlatformVersion((String)capabilities.get("platformVersion"));
		return testEnvironment;
	}

	private Target createTarget(Integer targetId, Integer screenshotId, TargetResult targetResult, ScreenshotResult screenshotResult) {
		Target target = new Target();
		target.setTargetId(targetId);
		target.setScreenshotId(screenshotId);
		target.setComparisonResult(targetResult.getResult() != null ? targetResult.getResult().isSuccess() : null);

		// 比較対象の情報
		ScreenAreaResult screenAreaResult = targetResult.getTarget();

		PersistMetadata targetMetadata = new PersistMetadata(screenshotResult.getExpectedId(), 
				screenshotResult.getTestClass(), screenshotResult.getTestMethod(), 
				screenshotResult.getScreenshotId(), screenAreaResult.getSelector(),
				screenAreaResult.getRectangle() ,new MrtCapabilities(screenshotResult.getCapabilities()));
		target.setFileName(getScreenshotImageFileName(targetMetadata));
		return target;
	}
	
	private Area createArea(Integer areaId, Integer targetId, ScreenAreaResult screenAreaResult, boolean excluded) {
		Area area = new Area();
		area.setAreaId(areaId);
		area.setTargetId(targetId);
		area.setSelectorType(screenAreaResult.getSelector().getType().name());
		area.setSelectorValue(screenAreaResult.getSelector().getValue());
		area.setX(screenAreaResult.getRectangle().getX());
		area.setY(screenAreaResult.getRectangle().getY());
		area.setWidth(screenAreaResult.getRectangle().getWidth());
		area.setHeight(screenAreaResult.getRectangle().getHeight());
		area.setExcluded(excluded);
		return area;
	}
	
	@Override
	public List<Screenshot> findScreenshot(Integer testExecutionId, String searchTestMethod, 
			String searchTestScreen) {
		return screenshotListMap != null ? 
				screenshotListMap.get(testExecutionId) : new ArrayList<Screenshot>(); 
	}

	@Override
	public Screenshot getScreenshot(Integer screenshotId) {
		return screenshotMap != null ? screenshotMap.get(screenshotId) : null;
	}

	@Override
	public File getImage(Integer screenshotId, Integer targetId) throws IOException {
		Screenshot screenshot = getScreenshot(screenshotId);

		if (screenshot == null) {
			return null;
		}

		Target target = null;
		for (Target t : screenshot.getTargets()) {
			if (t.getTargetId().intValue() == targetId.intValue()) {
				target = t;
				break;
			}
		}
		
		// FIXME データの持ち方を再検討する必要があるかも。
		// targetIdはシーケンシャルにふっているため、
		// 引数でわたってきたtargetIdと期待値となる画像のScreenshotクラスから取得したTargetクラスのIDは一致しない。
		// そのために以下の処理を必要とする。
		if (target == null) {
			Area area = targetMap.get(targetId).getArea();
			for (Target t : screenshot.getTargets()) {
				if (StringUtils.equals(t.getArea().getSelectorType(), area.getSelectorType()) && 
						StringUtils.equals(t.getArea().getSelectorValue(), area.getSelectorValue())) {
					target = t;
					break;
				}
			}
		}

		// FIXME MetaDataを使用する方法にするか？
		String child = screenshot.getTestExecution().getTimeString() 
				+ File.separatorChar + screenshot.getTestClass() 
				+ File.separatorChar + target.getFileName();
		File image = new File(super.getResultDirectoryFile(), child);

		// Send PNG image
		return image;
	}

	@Override
	public File searchProcessedImageFile(Integer screenshotId, String algorithm) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Screenshot> findNotProcessedEdge() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean exsitsProcessedImage(Integer screenshotId, String algorithm) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getEdgeFileName(Integer screenshotId, String algorithm) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void saveProcessedImage(Integer screenshotId, String algorithm, String edgeFileName) {
		throw new UnsupportedOperationException();
	}

}