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

package org.openecomp.aai.dbmap;

import java.util.UUID;

import org.openecomp.aai.dbgen.SchemaGenerator;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIConstants;

import com.thinkaurelius.titan.core.TitanFactory;
import com.thinkaurelius.titan.core.TitanGraph;
import com.thinkaurelius.titan.core.schema.TitanManagement;

/**
 * Database Mapping class which acts as the middle man between the REST
 * interface objects and Titan DB objects. This class provides methods to commit
 * the objects received on the REST interface into the Titan graph database as
 * vertices and edges. Transactions are also managed here by using a TitanGraph
 * object to load, commit/rollback and shutdown for each request. The data model
 * rules such as keys/required properties are handled by calling DBMeth methods
 * which are driven by a specification file in json.
 * 
 
 */
public class AAIGraph {

	protected static AAILogger aaiLogger = new AAILogger(AAIGraph.class.getName());
	protected LogLine logline = new LogLine();
	protected LogLine vlogline = new LogLine();
	protected LogLine tlogline = new LogLine();

	protected TitanGraph graph = null;
	protected static final String COMPONENT = "aaidbmap";

	/**
	 * Instantiates a new AAI graph.
	 */
	private AAIGraph() {
		LogLine alogline = new LogLine();
		alogline.init(COMPONENT, UUID.randomUUID().toString(), "AAI-INIT", "AAI Graph loading");
		try {
			graph = TitanFactory.open(AAIConstants.AAI_CONFIG_FILENAME);

			if (AAIConfig.get("storage.backend", "hbase").equals("inmemory")) { 
				// Load the propertyKeys, indexes and edge-Labels into the DB
				TitanManagement graphMgt = graph.openManagement();

				System.out.println("-- loading schema into Titan");
				SchemaGenerator.loadSchemaIntoTitan( graph, graphMgt );
			}

			if (graph != null) {
				aaiLogger.info(alogline, true, "0");
			} else {
				aaiLogger.info(alogline, false, "AAI_5102");
				throw new AAIException("AAI_5102");
			}
		} catch (Exception e) {
			aaiLogger.info(alogline, false, "AAI_5101");
			//throw new AAIException("AAI_5101", e);
		}
	}
	
	private static class Helper {
		private static final AAIGraph INSTANCE = new AAIGraph();
	}
	
	/**
	 * Gets the single instance of AAIGraph.
	 *
	 * @return single instance of AAIGraph
	 */
	public static AAIGraph getInstance() {
		return Helper.INSTANCE;

	}
	
	/**
	 * Graph commit.
	 *
	 * @throws AAIException the AAI exception
	 */
	public void graphCommit() throws AAIException {
		try {
			graph.tx().commit();
		} catch (Exception e) {
			throw new AAIException("AAI_5103", e);
		}
	}

	/**
	 * Graph rollback.
	 *
	 * @throws AAIException the AAI exception
	 */
	public void graphRollback() throws AAIException {
		try {
			graph.addVertex();
			graph.tx().rollback();
		} catch (Exception e) {
			throw new AAIException("AAI_5104", e);
		}
	}

	/**
	 * Graph shutdown.
	 */
	public void graphShutdown() {
		graph.close();
	}

	/**
	 * Gets the graph.
	 *
	 * @return the graph
	 */
	public TitanGraph getGraph() {
		return graph;
	}
}
