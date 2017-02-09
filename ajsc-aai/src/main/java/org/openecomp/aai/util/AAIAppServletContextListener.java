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

package org.openecomp.aai.util;

import java.util.UUID;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.activemq.broker.BrokerService;
import org.openecomp.aai.dbmap.AAIGraph;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.introspection.ModelInjestor;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.logging.ErrorObject;
import org.openecomp.aai.logging.LogLine;

public class AAIAppServletContextListener implements ServletContextListener {

	/**
 * Destroys Context
 * 
 * @param arg0 the ServletContextEvent
 */
	public void contextDestroyed(ServletContextEvent arg0) {

		AAILogger aaiLogger = new AAILogger(AAIAppServletContextListener.class.getName());
		LogLine logline = new LogLine();

		logline.init("aaigen", UUID.randomUUID().toString(), "AAI", "AAI Server shutdown");
		aaiLogger.debug(logline, "AAI graph shutdown");
		System.out.println("Shutting down graph database");
		AAIGraph.getInstance().graphShutdown();
		System.out.println("AAI Server stopped");
		aaiLogger.info(logline, true, "0");
	}

/**
 * Initializes Context
 * 
 * @param arg0 the ServletContextEvent
 */
	public void contextInitialized(ServletContextEvent arg0) {
		System.setProperty("org.openecomp.aai.serverStarted", "false");
		System.out.println("***AAI Server initialization started...");

		AAILogger aaiLogger = new AAILogger(AAIAppServletContextListener.class.getName());
		LogLine logline = new LogLine();

		try {
			String transId = UUID.randomUUID().toString();
			String fromAppId = "AAI-INIT";
			logline.init("aaigen", transId, fromAppId, "AAI Server initialization");

			aaiLogger.debug(logline, "Loading aaiconfig.properties");
			System.out.println("Loading aaiconfig.properties");
			AAIConfig.init(transId, fromAppId);

			aaiLogger.debug(logline, "Loading error.properties");
			System.out.println("Loading error.properties");
			ErrorLogHelper.loadProperties();

			aaiLogger.debug(logline, "Loading graph database");
			System.out.println("Loading graph database");

			AAIGraph.getInstance();
			ModelInjestor.getInstance();
			aaiLogger.info(logline, true, "0");

			// Jsm internal broker for aai events
			BrokerService broker = new BrokerService();
			broker.addConnector("tcp://localhost:61616");
			broker.setPersistent(false);
			broker.setUseJmx(false);
			broker.setSchedulerSupport(false);
			broker.start();

			System.out.println("***AAI Server initialization succcessful.");
		} catch (AAIException e) {
			aaiLogger.error(e.getErrorObject(), logline, e);
			aaiLogger.info(logline, false, e.getErrorObject().getErrorCodeString());
			System.out.println("***AAI Server initialization failed.");
		} catch (Exception e) {
			// log the error
			ErrorObject errorObject = new ErrorObject();
			errorObject.setDisposition("5");
			errorObject.setCategory("4");
			errorObject.setSeverity("FATAL");
			errorObject.setErrorCode("4000");
			errorObject.setErrorText("Internal Error");
			errorObject.setDetails(e.getMessage());

			aaiLogger.error(errorObject, logline, e);
			aaiLogger.info(logline, false, "AAI_4000");
			System.out.println("***AAI Server initialization failed.");
		}

		System.setProperty("org.openecomp.aai.serverStarted", "true");
	}
}
