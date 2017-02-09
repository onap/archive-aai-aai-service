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

package org.openecomp.aai.dmaap.aaiWorkload.consumer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.logging.Level;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.logging.ErrorObject;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIConstants;

import com.att.aft.dme2.api.DME2Exception;
import com.att.nsa.mr.client.MRClientFactory;
import com.att.nsa.mr.client.MRConsumer;

public class AAIWorkloadConsumer {

	private static String fromAppId = "AAIWorkloadConsumerScheduledTask";
	private static String COMPONENT = "DMAAP-AAI-WORKLOAD";
	private AAILogger aaiLogger = new AAILogger(AAIWorkloadConsumer.class.getName());

	private String preferredRouterFilePath;
	private String aaiWorkloadConsumerPropertiesFile;
	private String aaiWorkloadStatusPublisherPropertiesFile;
	private String aaiWorkloadPublisherPropertiesFile;
	private String dmaapPropertyHome = "";
	private String dmaapConusmerId = "";

	private MRConsumer aaiWorkloadConsumer;

	public AAIWorkloadConsumer() throws Exception {

		LogLine logline = new LogLine();
		logline.init(COMPONENT, "N/A", fromAppId, "AAIWorkloadConsumer");
		aaiLogger.debug(logline, "Initalize the AAIWorkloadConsumer");

		this.dmaapPropertyHome = AAIConstants.AAI_HOME_ETC_APP_PROPERTIES;
		if (this.dmaapPropertyHome == null) {
			ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000", "Property AAI_HOME_ETC_APP_PROPERTIES is not set. Stopping AAIWorkloadConsumer.");
			aaiLogger.error(errorObject, logline, null);
			throw new Exception("Property AAI_HOME_ETC_APP_PROPERTIES is not set. Stopping AAIWorkloadConsumer.");
		}

		if (System.getProperty("lrmHost") != null && !System.getProperty("lrmHost").isEmpty()) {
			this.dmaapConusmerId = System.getProperty("lrmHost");
		} else {
			this.dmaapConusmerId = UUID.randomUUID().toString();
		}

		processPropertyFiles();

		this.aaiWorkloadConsumer = MRClientFactory.createConsumer(this.aaiWorkloadConsumerPropertiesFile);
		aaiLogger.debug(logline, "Initalization completed.");

	}

	private void processPropertyFiles() throws FileNotFoundException, UnknownHostException, ConfigurationException {

		LogLine logline = new LogLine();
		logline.init(COMPONENT, "N/A", fromAppId, "processPropertyFiles");

		this.preferredRouterFilePath = this.dmaapPropertyHome + "preferredRoute.txt";
		this.aaiWorkloadConsumerPropertiesFile = this.dmaapPropertyHome + "aaiWorkloadConsumer.properties";
		this.aaiWorkloadPublisherPropertiesFile = this.dmaapPropertyHome + "aaiWorkloadPublisher.properties";
		this.aaiWorkloadStatusPublisherPropertiesFile = this.dmaapPropertyHome + "aaiWorkloadStatusPublisher.properties";

		aaiLogger.debug(logline, "Preferred router file path: " + this.preferredRouterFilePath);
		aaiLogger.debug(logline, "AAI Workload Consumer Properties path: " + this.aaiWorkloadConsumerPropertiesFile);
		aaiLogger.debug(logline, "AAI Workload Publisher Properties path: " + this.aaiWorkloadPublisherPropertiesFile);
		aaiLogger.debug(logline, "AAI Workload StatusPublisher Properties path: " + this.aaiWorkloadStatusPublisherPropertiesFile);

		File fo = new File(this.preferredRouterFilePath);
		if (!fo.exists()) {
			ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000", "Dmaap Route file " + preferredRouterFilePath + " does not exist.");
			aaiLogger.error(errorObject, logline, null);
			throw new FileNotFoundException("Dmaap Route file " + preferredRouterFilePath + " does not exist");
		}

		fo = new File(this.aaiWorkloadConsumerPropertiesFile);
		if (!fo.exists()) {
			ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000", "Dmaap consumer property file " + aaiWorkloadConsumerPropertiesFile + " does not exist.");
			aaiLogger.error(errorObject, logline, null);
			throw new FileNotFoundException("Dmaap consumer property file " + aaiWorkloadConsumerPropertiesFile + " does not exist.");
		}

		fo = new File(this.aaiWorkloadPublisherPropertiesFile);
		if (!fo.exists()) {
			ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000", "Dmaap publisher property file " + this.aaiWorkloadPublisherPropertiesFile + " does not exist.");
			aaiLogger.error(errorObject, logline, null);
			throw new FileNotFoundException("Dmaap publisher property file " + this.aaiWorkloadPublisherPropertiesFile + " does not exist.");
		}

		fo = new File(this.aaiWorkloadStatusPublisherPropertiesFile);
		if (!fo.exists()) {
			ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000",
					"Dmaap publisher property file " + this.aaiWorkloadStatusPublisherPropertiesFile + " does not exist");
			aaiLogger.error(errorObject, logline, null);
			throw new FileNotFoundException("Dmaap publisher property file " + this.aaiWorkloadStatusPublisherPropertiesFile + " does not exist");
		}

		modifyProperties();

	}

	private void modifyProperties() throws ConfigurationException {

		LogLine logline = new LogLine();
		logline.init(COMPONENT, "N/A", fromAppId, "modifyProperties");

		PropertiesConfiguration config = new PropertiesConfiguration(this.aaiWorkloadConsumerPropertiesFile);
		if (config.getProperty("id") == null || config.getProperty("id").equals("") || config.getProperty("id").equals("aaiConsumerId")) {
			config.setProperty("id", this.dmaapConusmerId);
			aaiLogger.debug(logline, "Updated " + this.aaiWorkloadConsumerPropertiesFile + " id property to this.dmaapConusmerId.");
		}
		config.setProperty("DME2preferredRouterFilePath", this.preferredRouterFilePath);
		config.save();
		aaiLogger.debug(logline, "Updated " + this.aaiWorkloadConsumerPropertiesFile + " DME2preferredRouterFilePath property to " + this.preferredRouterFilePath);

		config = new PropertiesConfiguration(this.aaiWorkloadPublisherPropertiesFile);
		config.setProperty("DME2preferredRouterFilePath", this.preferredRouterFilePath);
		config.save();
		aaiLogger.debug(logline, "Updated " + this.aaiWorkloadPublisherPropertiesFile + " DME2preferredRouterFilePath property to " + this.preferredRouterFilePath);

		config = new PropertiesConfiguration(this.aaiWorkloadStatusPublisherPropertiesFile);
		config.setProperty("DME2preferredRouterFilePath", this.preferredRouterFilePath);
		config.save();
		aaiLogger.debug(logline, "Updated " + this.aaiWorkloadStatusPublisherPropertiesFile + " DME2preferredRouterFilePath property to " + this.preferredRouterFilePath);

	}

	public void startProcessing() throws Exception {

		LogLine logline = new LogLine();
		logline.init(COMPONENT, "N/A", fromAppId, "startProcessing");

		int fetchFailCounter = 0;
		AAIWorkloadEventProcessor awep = null;
//		com.att.aft.dme2.api.util.configuration.DME2LoggingConfig.getInstance().initializeDME2Logger().setLevel(Level.SEVERE);
		while (AAIConfig.get("aai.dmaap.workload.enableEventProcessing").equals("true")) {

			try {
				if (System.getProperty("org.openecomp.aai.serverStarted") != null && System.getProperty("org.openecomp.aai.serverStarted").equals("true")) {
					Iterable<String> eventMessages = aaiWorkloadConsumer.fetch();
					for (String eventMessage : eventMessages) {
						String transId = UUID.randomUUID().toString();
						aaiLogger.debug(logline, "Processing new dmaap message from the aaiWorkload topic." + transId);
						aaiLogger.debug(logline, "Processing new dmaap message from the aaiWorkload topic: " + eventMessage);
						awep = new AAIWorkloadEventProcessor(this.aaiWorkloadPublisherPropertiesFile, this.aaiWorkloadStatusPublisherPropertiesFile, transId);
						awep.process(eventMessage);
					}
					fetchFailCounter = 0;
				}
			} catch (DME2Exception e) {
				if (e.getErrorCode().equals("AFT-DME2-0999")) {
					// do nothing as this is the standard http timeout
				} else {
					ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000", "Exiting due to dmaap consumer client throwing dme2 error.");
					aaiLogger.error(errorObject, logline, e);
					this.aaiWorkloadConsumer.close();
					throw e;
				}
			} catch (IOException e) {
				fetchFailCounter++;
				if (fetchFailCounter > 10) {
					ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000", "Exiting due to fetch throwing io exception. More than 10 times.");
					aaiLogger.error(errorObject, logline, e);
					this.aaiWorkloadConsumer.close();
					throw e;
				}
			} catch (Exception e) {
				ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000", "Exiting due to unknown exception.");
				aaiLogger.error(errorObject, logline, e);
				this.aaiWorkloadConsumer.close();
				throw e;
			}
		}
		this.aaiWorkloadConsumer.close();
	}

}
