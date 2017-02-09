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

package org.openecomp.aai.serialization.engines;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.openecomp.aai.introspection.Loader;
import org.openecomp.aai.query.builder.GremlinTraversal;
import org.openecomp.aai.query.builder.GremlinUnique;
import org.openecomp.aai.query.builder.QueryBuilder;
import org.openecomp.aai.query.builder.TraversalQuery;
import org.openecomp.aai.serialization.db.GraphSingleton;
import org.openecomp.aai.serialization.engines.query.GraphTraversalQueryEngine;
import org.openecomp.aai.serialization.engines.query.QueryEngine;

import com.thinkaurelius.titan.core.TitanGraph;

public abstract class TransactionalGraphEngine {
	
	protected Graph graph = null;
	protected GraphSingleton singleton = null;
	protected QueryEngine queryEngine = null;
	protected QueryBuilder queryBuilder = null;
	protected QueryStyle style = null;
	protected Loader loader = null;

	/**
	 * Instantiates a new transactional graph engine.
	 *
	 * @param style the style
	 * @param loader the loader
	 */
	public TransactionalGraphEngine (QueryStyle style, Loader loader) {
		this.loader = loader;
		this.style = style;
		if (style.equals(QueryStyle.GREMLIN_TRAVERSAL)) {
			//this.queryEngine = new GremlinQueryEngine(this);
		} else if (style.equals(QueryStyle.GREMLIN_UNIQUE)) {
			//this.queryEngine = new GremlinQueryEngine(this);
		} else if (style.equals(QueryStyle.GREMLINPIPELINE_TRAVERSAL)) {
			//this.queryEngine = new GremlinPipelineQueryEngine(this);
		} else if (style.equals(QueryStyle.TRAVERSAL)) {
			this.queryEngine = new GraphTraversalQueryEngine(this);
		} else {
			throw new IllegalArgumentException("Query Engine type not recognized");
		}
	}
	

	/**
	 * Sets the list property.
	 *
	 * @param v the v
	 * @param name the name
	 * @param obj the obj
	 * @return true, if successful
	 */
	public abstract boolean setListProperty(Vertex v, String name, List<?> obj);
	
	/**
	 * Gets the list property.
	 *
	 * @param v the v
	 * @param name the name
	 * @return the list property
	 */
	public abstract List getListProperty(Vertex v, String name);
	
	/**
	 * Gets the graph.
	 *
	 * @return the graph
	 */
	public TitanGraph getGraph() {
		return singleton.getTxGraph();
	}
	
	/**
	 * Gets the count.
	 *
	 * @return the count
	 */
	public AtomicInteger getCount() {
		return singleton.getCount();
	}
	
	/**
	 * Should shutdown.
	 *
	 * @return true, if successful
	 */
	public boolean shouldShutdown() {
		return false;
	}
	
	/**
	 * Gets the query engine.
	 *
	 * @return the query engine
	 */
	public QueryEngine getQueryEngine() {
		return this.queryEngine;
	}
	
	/**
	 * Gets the query builder.
	 *
	 * @return the query builder
	 */
	public QueryBuilder getQueryBuilder() {
		if (style.equals(QueryStyle.GREMLIN_TRAVERSAL)) {
			return new GremlinTraversal(loader);
		} else if (style.equals(QueryStyle.GREMLIN_UNIQUE)) {
			return new GremlinUnique(loader);
		} else if (style.equals(QueryStyle.GREMLINPIPELINE_TRAVERSAL)) {
			//return new GremlinPipelineTraversal(loader);
		} else if (style.equals(QueryStyle.TRAVERSAL)) {
			return new TraversalQuery(loader);
		}  else {
			throw new IllegalArgumentException("Query Builder type not recognized");
		}
		return queryBuilder;
	}
	
	/**
	 * Gets the query builder.
	 *
	 * @param start the start
	 * @return the query builder
	 */
	public QueryBuilder getQueryBuilder(Vertex start) {
		if (style.equals(QueryStyle.GREMLIN_TRAVERSAL)) {
			return new GremlinTraversal(loader,start);
		} else if (style.equals(QueryStyle.GREMLIN_UNIQUE)) {
			return new GremlinUnique(loader,start);
		} else if (style.equals(QueryStyle.GREMLINPIPELINE_TRAVERSAL)) {
			//return new GremlinPipelineTraversal(loader,start);
		} else if (style.equals(QueryStyle.TRAVERSAL)) {
			return new TraversalQuery(loader, start);
		} else {
			throw new IllegalArgumentException("Query Builder type not recognized");
		}
		return queryBuilder;
	}
}
