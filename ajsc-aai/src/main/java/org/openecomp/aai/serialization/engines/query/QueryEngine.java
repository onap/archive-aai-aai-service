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

package org.openecomp.aai.serialization.engines.query;

import java.util.List;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.openecomp.aai.query.builder.QueryBuilder;
import org.openecomp.aai.serialization.engines.TransactionalGraphEngine;

public abstract class QueryEngine {

	protected TransactionalGraphEngine dbEngine = null;

	/**
	 * Instantiates a new query engine.
	 *
	 * @param graphEngine the graph engine
	 */
	public QueryEngine (TransactionalGraphEngine graphEngine) {
		this.dbEngine = graphEngine;
	}

	/**
	 * Execute query.
	 *
	 * @param g the g
	 * @param query the query
	 * @return the list
	 */
	public abstract List<Vertex> executeQuery(Graph g, QueryBuilder query);
	
	/**
	 * Execute parent query.
	 *
	 * @param g the g
	 * @param query the query
	 * @return the list
	 */
	public abstract List<Vertex> executeParentQuery(Graph g, QueryBuilder query);
	
	/**
	 * Find parents.
	 *
	 * @param start the start
	 * @return the list
	 */
	public abstract List<Vertex> findParents(Vertex start);
	
	/**
	 * Find children.
	 *
	 * @param start the start
	 * @return the list
	 */
	public abstract List<Vertex> findChildren(Vertex start);
	
	/**
	 * Find deletable.
	 *
	 * @param start the start
	 * @return the list
	 */
	public abstract List<Vertex> findDeletable(Vertex start);
	
	/**
	 * Find related vertices.
	 *
	 * @param start the start
	 * @param direction the direction
	 * @param label the label
	 * @param nodeType the node type
	 * @return the list
	 */
	public abstract List<Vertex> findRelatedVertices(Vertex start, Direction direction, String label, String nodeType);


}
