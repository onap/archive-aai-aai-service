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

package org.openecomp.aai.serialization.db;

import org.openecomp.aai.dbmap.AAIGraph;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.logging.LogLine;

import com.thinkaurelius.titan.core.TitanGraph;

public class TitanGraphSingleton extends GraphSingleton {

	
	/**
	 * Instantiates a new titan graph singleton.
	 */
	private TitanGraphSingleton() {
	
		LogLine alogline = new LogLine();
		alogline.init("aaidbmap", "Init-000", "AAI", "AAI Graph loading");
		try {
			//graph = TitanFactory.open(AAIConstants.AAI_CONFIG_FILENAME);
			/* just grab the one from the A&AI start up */
			graph = AAIGraph.getInstance().getGraph();
			if (graph != null) {
				aaiLogger.info(alogline, true, "0");
			} else {
				aaiLogger.info(alogline, false, "AAI_5102");
				throw new AAIException("AAI_5102");
			}
		} catch (Exception e) {
			aaiLogger.info(alogline, false, "AAI_5102");
		}
	}
	
	private static class Helper {
		private static final TitanGraphSingleton INSTANCE = new TitanGraphSingleton();
	}
	
	/**
	 * Gets the single instance of TitanGraphSingleton.
	 *
	 * @return single instance of TitanGraphSingleton
	 */
	public static TitanGraphSingleton getInstance() {
		return Helper.INSTANCE;
	}
	
	/**
	 * {@inheritDoc} 
	 */
	public TitanGraph getTxGraph() {
		return graph;
	}
	
}
