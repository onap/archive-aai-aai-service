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

package org.openecomp.aai.ingestModel;

import java.util.ArrayList;
import java.util.UUID;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLine;

/**
 * The listener interface for receiving ingestModel events.
 * The class that is interested in processing a ingestModel
 * event implements this interface, and the object created
 * with that class is registered with a component using the
 * component's <code>addIngestModelListener<code> method. When
 * the ingestModel event occurs, that object's appropriate
 * method is invoked.
 *
 * @see IngestModelEvent
 */
public class IngestModelListener implements ServletContextListener {
	
	AAILogger aaiLogger = new AAILogger("org.openecomp.aai.ingestModel.IngestModelListener");
	LogLine logline = new LogLine();

/**
 * Destroys context.
 *
 * @param arg0 the ServletContextEvent
 */
//@Override
	public void contextDestroyed(ServletContextEvent arg0) {
		IngestModelMoxyOxm m = new IngestModelMoxyOxm();
		m.cleanup();
		System.out.println("AAI Auth Listener contextDestroyed() complete.");
	}

//Run this before web application is started
	/**
 * Initializaes the context.
 *
 * @param arg0 the ServletContextEvent
 */
//@Override
	public void contextInitialized(ServletContextEvent arg0)  {
		
		logline.init("aaigen", UUID.randomUUID().toString(), "AAI-INIT", "AAI Ingest Model initialization");
		
		System.out.println("IngestModel starts initialization...");
		try { 
			ArrayList<String> apiVersions = new ArrayList<String>();
			apiVersions.add("v8");
			IngestModelMoxyOxm m = new IngestModelMoxyOxm();
			m.init(apiVersions);
			logline.add("apiVersions", apiVersions.toString());
			aaiLogger.info(logline, true, "0");
		} catch (Exception e) { 
			aaiLogger.error(new AAIException("AAI_3000").getErrorObject(), logline, e);
			aaiLogger.info(logline, false, "AAI_3000");
		}
	}
}
