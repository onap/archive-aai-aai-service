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

import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalHelper;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.openecomp.aai.db.AAIProperties;
import org.openecomp.aai.query.builder.QueryBuilder;
import org.openecomp.aai.serialization.engines.TransactionalGraphEngine;

import com.thinkaurelius.titan.core.TitanGraph;

public class GraphTraversalQueryEngine extends QueryEngine {

	/**
	 * Instantiates a new graph traversal query engine.
	 *
	 * @param graphEngine the graph engine
	 */
	public GraphTraversalQueryEngine(TransactionalGraphEngine graphEngine) {
		super(graphEngine);
	}

	/**
	 * {@inheritDoc} 
	 */
	@Override
	public List<Vertex> executeQuery(Graph g, QueryBuilder query) {
		List<Vertex> results = null;
		Vertex start = query.getStart();
		GraphTraversal<Vertex, Vertex> traversal = (GraphTraversal)query.getQuery();
		Traversal.Admin<Vertex, Vertex> admin = null;
		if (start != null) {
			results = ((GraphTraversal)query.getQuery()).toList();

		} else {
			admin = g.traversal().V().asAdmin();
			TraversalHelper.insertTraversal(admin.getEndStep(), traversal.asAdmin(), admin);

			results = admin.toList();
		}
		

		return results;
	}
	
	/**
	 * {@inheritDoc} 
	 */
	@Override
	public List<Vertex> executeParentQuery(Graph g, QueryBuilder query) {
		List<Vertex> results = null;
		Vertex start = query.getStart();
		GraphTraversal<Vertex, Vertex> traversal = (GraphTraversal)query.getParentQuery();
		Traversal.Admin<Vertex, Vertex> admin = null;
		if (start != null) {
			results = ((GraphTraversal)query.getParentQuery()).toList();
		} else {
			admin = g.traversal().V().asAdmin();
			TraversalHelper.insertTraversal(admin.getEndStep(), traversal.asAdmin(), admin);

			results = admin.toList();
		}
		

		return results;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Vertex> findParents(Vertex start) {
		GraphTraversal<Vertex, Vertex> pipe = start.graph().traversal().V(start).emit()
				.repeat(__.inE()
				.has("isParent", true).outV());
		
		List<Vertex> results = pipe.toList();
		//results.add(0, start);
		return results;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Vertex> findChildren(Vertex start) {
		
		GraphTraversal<Vertex, Vertex> pipe = start.graph().traversal().V(start).emit()
				.repeat(__.outE().has("isParent", true).inV());
		
		List<Vertex> results = pipe.toList();
		//results.add(0, start);
		return results;
		
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Vertex> findDeletable(Vertex start) {
		
		GraphTraversal<Vertex, Vertex> pipe = start.graph().traversal().V(start).emit()
				.repeat(__.outE().or(
						__.has("isParent", true),
						__.has("hasDelTarget", true)).inV());
		
		List<Vertex> results = pipe.toList();
		//results.add(0, start);
		return results;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Vertex> findRelatedVertices(Vertex start, Direction direction, String label, String nodeType) {
		GraphTraversal<Vertex, Vertex> pipe = start.graph().traversal().V(start);
		switch (direction) {
			case OUT:
				pipe.out(label);
				break;
			case IN:
				pipe.in(label);
				break;
			case BOTH:
				pipe.both(label);
				break;
			 default:
				break;
		}
		
		pipe.has(AAIProperties.NODE_TYPE, nodeType).dedup();
		List<Vertex> result = pipe.toList();
		return result;
	}
	
}

