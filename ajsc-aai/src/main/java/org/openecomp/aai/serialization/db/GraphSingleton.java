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

import java.util.concurrent.atomic.AtomicInteger;

import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLine;

import com.thinkaurelius.titan.core.TitanGraph;


public class GraphSingleton {

	protected AAILogger aaiLogger = new AAILogger("AAIGraph");
	protected LogLine logline = new LogLine();
	protected LogLine vlogline = new LogLine();
	protected LogLine tlogline = new LogLine();
	
	protected static TitanGraph graph = null;
	protected AtomicInteger totalCount = new AtomicInteger();
	
	private static class Helper {
		private static final GraphSingleton INSTANCE = new GraphSingleton();
	}
	
	/**
	 * Gets the single instance of GraphSingleton.
	 *
	 * @return single instance of GraphSingleton
	 */
	public static GraphSingleton getInstance() {
		return Helper.INSTANCE;

	}
	
	/**
	 * Gets the count.
	 *
	 * @return the count
	 */
	public AtomicInteger getCount() {
		return totalCount;
	}
	
	/**
	 * Gets the logger.
	 *
	 * @return the logger
	 */
	public AAILogger getLogger() {
		return aaiLogger;
	}
	
	/**
	 * Gets the tx graph.
	 *
	 * @return the tx graph
	 */
	public TitanGraph getTxGraph() {
		return graph;
	}
}
