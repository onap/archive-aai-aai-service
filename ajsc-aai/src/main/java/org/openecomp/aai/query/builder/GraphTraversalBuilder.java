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

package org.openecomp.aai.query.builder;

import java.nio.channels.Pipe;
import java.util.LinkedHashMap;
import java.util.List;

import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.openecomp.aai.db.AAIProperties;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.introspection.Loader;
import org.openecomp.aai.serialization.db.EdgeRule;
import org.openecomp.aai.serialization.db.EdgeRules;

/**
 * The Class GraphTraversalBuilder.
 */
public abstract class GraphTraversalBuilder extends QueryBuilder {

	private GraphTraversal<Vertex, Vertex> traversal = null;
	
	private EdgeRules edgeRules = EdgeRules.getInstance();
	
	private int parentStepIndex = 0;
	
	private int stepIndex = 0;
	
	/**
	 * Instantiates a new graph traversal builder.
	 *
	 * @param loader the loader
	 */
	public GraphTraversalBuilder(Loader loader) {
		super(loader);
		
		traversal = __.start();
		
	}
	
	/**
	 * Instantiates a new graph traversal builder.
	 *
	 * @param loader the loader
	 * @param start the start
	 */
	public GraphTraversalBuilder(Loader loader, Vertex start) {
		super(loader, start);
		
		traversal = __.__(start);
		
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	public QueryBuilder getVerticesByIndexedProperty(String key, Object value) {
	
		return this.getVerticesByProperty(key, value);
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	public QueryBuilder getVerticesByProperty(String key, Object value) {
		
		//this is because the index is registered as an Integer
		if (value != null && value.getClass().equals(Long.class)) {
			traversal.has(key,new Integer(value.toString()));
		} else {
			traversal.has(key, value);
		}
		stepIndex++;
		return this;
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	public QueryBuilder getChildVerticesFromParent(String parentKey, String parentValue, String childType) {
		traversal.has(parentKey, parentValue).has(AAIProperties.NODE_TYPE, childType);
		stepIndex++;
		return this;
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	public QueryBuilder getTypedVerticesByMap(String type, LinkedHashMap<String, String> map) {
		
		for (String key : map.keySet()) {
			traversal.has(key, map.get(key));
			stepIndex++;
		}
		traversal.has(AAIProperties.NODE_TYPE, type);
		stepIndex++;
		return this;
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	public QueryBuilder createDBQuery(Introspector obj) {
		this.createKeyQuery(obj);
		this.createContainerQuery(obj);
		return this;
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	public QueryBuilder createKeyQuery(Introspector obj) {
		List<String> keys = obj.getKeys();
		Object val = null;
		for (String key : keys) {
			val = obj.getValue(key);
			//this is because the index is registered as an Integer
			if (val != null && val.getClass().equals(Long.class)) {
				traversal.has(key,new Integer(val.toString()));
			} else {
				traversal.has(key, val);
			}
			stepIndex++;
		}
		return this;
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	
	public QueryBuilder createContainerQuery(Introspector obj) {
		String type = obj.getChildDBName();
		String abstractType = obj.getMetadata("abstract");
		if (abstractType != null) {
			String[] inheritors = obj.getMetadata("inheritors").split(",");
			Traversal<Vertex, Vertex>[] traversals = new Traversal[inheritors.length];
			for (int i = 0; i < inheritors.length; i++) {
				traversals[i] = __.has(AAIProperties.NODE_TYPE, inheritors[i]);
			}
			traversal.or(traversals);
		} else {
			traversal.has(AAIProperties.NODE_TYPE, type);
		}
		stepIndex++;
		return this;
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	public QueryBuilder createEdgeTraversal(Introspector parent, Introspector child) {
		String parentName = parent.getDbName();
		String childName = child.getDbName();
		if (parent.isContainer()) {
			parentName = parent.getChildDBName();
		}
		if (child.isContainer()) {
			childName = child.getChildDBName();
		}
		this.edgeQuery(parentName, childName);
		return this;
			
	}
	
	/**
	 * @{inheritDoc}
	 */
	@Override
	public QueryBuilder createEdgeTraversal(Vertex parent, Introspector child) {
		
		String nodeType = parent.<String>property(AAIProperties.NODE_TYPE).orElse(null);
		this.edgeQuery(nodeType, child.getDbName());
		return this;
			
	}
	
	/**
	 * Edge query.
	 *
	 * @param outType the out type
	 * @param inType the in type
	 */
	private void edgeQuery(String outType, String inType) {
		formBoundary();
		EdgeRule rule;
		String label = "";
		try {
			rule = edgeRules.getEdgeRule(outType, inType);
			label = rule.getLabel();
		} catch (AAIException e) {
			// TODO Auto-generated catch block
		}
		traversal = traversal.out(label);
		stepIndex++;
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	public Object getQuery() {
		return this.traversal;
	}
	
	/**
	 * @{inheritDoc}
	 */
	@Override
	public Object getParentQuery() {
		if (parentStepIndex == 0) {
			parentStepIndex = stepIndex;
		}
		GraphTraversal<Vertex, Vertex> clone = this.traversal.asAdmin().clone();
		GraphTraversal.Admin<Vertex, Vertex> cloneAdmin = clone.asAdmin();
		List<Step> steps = cloneAdmin.getSteps();

		//add two for the garbage identity pipes
		for (int i = steps.size()-1; i >= parentStepIndex; i--) {
			cloneAdmin.removeStep(i);
		}

		return cloneAdmin;
	}
	
	/**
	 * @{inheritDoc}
	 */
	@Override
	public void formBoundary() {
		parentStepIndex = stepIndex;
	}
	
	
	/**
	 * @{inheritDoc}
	 */
	@Override
	public Vertex getStart() {
		return this.start;
	}
	

}
