/*-
 * ============LICENSE_START=======================================================
 * org.openecomp.aai
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
 */

package org.openecomp.aai.tasks;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.comparator.LastModifiedFileComparator;
import org.openecomp.aai.dmaap.aaiWorkload.consumer.AAIWorkloadConsumer;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.logging.ErrorObject;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIConstants;
import org.openecomp.aai.util.AAIPrimaryHost;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTasks {

	protected static AAILogger aaiLogger = new AAILogger(ScheduledTasks.class.getName());
	protected LogLine logline = new LogLine();
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

	protected String COMPONENT = "Scheduler";
	private String transId = null;
	private String fromAppId = "CronApp";

	final static String INSTAR_CRON_ENTRY = "0 40 * 1/1 * ?";
	// for now to test, every minute, should be something like "0 */5 * * *
	// MON-FRI" i.e. every 5 minutes
	final static long INSTAR_READ_MIN_INTERVAL = 300000; // every 5 minutes
	static long instar_read_count = 0;
	static long instarams_read_count = 0;
	final static long PROPERTY_READ_INTERVAL = 60000; // every minute

	private String GlobalPropFileName = AAIConstants.AAI_CONFIG_FILENAME;

	


	// for read and possibly reloading aaiconfig.properties and other
	/**
	 * Load AAI properties.
	 */
	// configuration properties files
	@Scheduled(fixedRate = PROPERTY_READ_INTERVAL)
	public void loadAAIProperties() {
		String methodName = "loadAAIProperties()";
		transId = UUID.randomUUID().toString();
		logline.init(COMPONENT, transId, fromAppId, "loadAAIProperties()");

		String dir = FilenameUtils.getFullPathNoEndSeparator(GlobalPropFileName);
		if (dir == null || dir.length() < 3) {
			dir = "/opt/aai/etc";
		}

		File pdir = new File(dir);
		File[] files = pdir.listFiles();
		Arrays.sort(files, LastModifiedFileComparator.LASTMODIFIED_REVERSE);
		String fn;

		// leave this loop here since we may want to check other configurable
		// property files in the SAME directory
		for (File file : files) {
			fn = file.getName();
			if (fn.equals("aaiconfig.properties")) {
				Date lastMod = new Date(file.lastModified());
				long lastModTm = lastMod.getTime();
				Date curTS = new Date();
				long curTSTm = curTS.getTime();

				if (curTSTm - lastModTm < PROPERTY_READ_INTERVAL + 1000) {
					AAIConfig.reloadConfig();
					logline.add(methodName, "reloaded from aaiconfig.properties");
					aaiLogger.info(logline, true, "0");

				}
				break;
			}
			/*
			 * This is not needed now since we are using AJSC and logback.xml
			 * else if (fn.equals("log4j.properties")) { //Code changes
			 * 
			 * Date lastMod = new Date(file.lastModified()); long lastModTm =
			 * lastMod.getTime(); Date curTS = new Date(); long curTSTm =
			 * curTS.getTime(); if ((curTSTm - lastModTm) <
			 * PROPERTY_READ_INTERVAL + 1000) { System.out.println(
			 * "log4j.properties has been changed in the past minute, reloading it now..."
			 * ); aaiLogger.debug(logline,
			 * "going to reload from log4j.properties");
			 * AAIConfig.reconfigAAILog4jProps(); aaiLogger.debug(logline,
			 * "After reloading from log4j.properties"); aaiLogger.info(logline,
			 * true, "0"); } }
			 */
		}
	}


	/**
	 * Starts the aaiWorkloadConsumer 5 mins after task bean init.
	 * Will restart 5 mins after the previous one ended.
	 */
	@Scheduled(fixedRate = 300000, initialDelay = 300000)
	public void dmaapAAIWorkloadProcessor() {
		
		String methodName = "dmaapAAIWorkloadProcessor()";
		LogLine ll = new LogLine();
		ll.init(COMPONENT, transId, fromAppId, methodName);
		
		ll.add(methodName, "Started fixed rate job dmaapAAIWorkloadProcessor");
		aaiLogger.info(logline, true, "0");
		
		System.out.println("Started fixed rate job dmaapAAIWorkloadProcessor");
		try {
			if (AAIConfig.get("aai.dmaap.workload.enableEventProcessing").equals("true")) {
				System.out.println("aai.dmaap.workload.enableEventProcessing set to true, starting AAIWorkloadConsumer.");
				ll.add(methodName, "aai.dmaap.workload.enableEventProcessing set to true, starting AAIWorkloadConsumer.");
				aaiLogger.info(logline, true, "0");
				AAIWorkloadConsumer awc = new AAIWorkloadConsumer();
				awc.startProcessing();
			} else {
				System.out.println("aai.dmaap.workload.enableEventProcessing set to false no start on AAIWorkloadConsumer.");
				ll.add(methodName, "aai.dmaap.workload.enableEventProcessing set to false no start on AAIWorkloadConsumer.");
				aaiLogger.info(logline, true, "0");
			}
		} catch (Exception e) {
			ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000", e.getMessage());
			aaiLogger.error(errorObject, logline, e);
			aaiLogger.info(logline, false, "AAI_4000");
		}
	}

}
