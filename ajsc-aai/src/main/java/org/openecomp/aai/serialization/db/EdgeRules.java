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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.openecomp.aai.db.AAIProperties;
import org.openecomp.aai.dbmodel.DbEdgeRules;
import org.openecomp.aai.exceptions.AAIException;

import com.google.common.collect.Multimap;

public class EdgeRules {

	private Multimap<String, String> rules = DbEdgeRules.EdgeRules;
	private Multimap<String, String> deleteScope = 	DbEdgeRules.DefaultDeleteScope;
	private final int EDGE_NAME = 0;
	private final int DIRECTION = 1;
	private final int MULTIPLICITY_RULE = 2;
	private final int IS_PARENT = 3;
	private final int USES_RESOURCE = 4;
	private final int HAS_DEL_TARGET = 5;
	private final int SVC_INFRA = 6;
	
	/**
	 * Instantiates a new edge rules.
	 */
	private EdgeRules() {
	
	}
	private static class Helper {
		private static final EdgeRules INSTANCE = new EdgeRules();
		
	}
	
	/**
	 * Gets the single instance of EdgeRules.
	 *
	 * @return single instance of EdgeRules
	 */
	public static EdgeRules getInstance() {
		return Helper.INSTANCE;

	}
	
	/**
	 * Adds the tree edge.
	 *
	 * @param outVertex the out vertex
	 * @param inVertex the in vertex
	 * @return the edge
	 * @throws AAIException the AAI exception
	 */
	public Edge addTreeEdge(Vertex outVertex, Vertex inVertex) throws AAIException {
		return this.addEdge(EdgeType.TREE, outVertex, inVertex);
	}
	
	/**
	 * Adds the edge.
	 *
	 * @param outVertex the out vertex
	 * @param inVertex the in vertex
	 * @return the edge
	 * @throws AAIException the AAI exception
	 */
	public Edge addEdge(Vertex outVertex, Vertex inVertex) throws AAIException {
		return this.addEdge(EdgeType.COUSIN, outVertex, inVertex);
	}
	
	/**
	 * Adds the edge.
	 *
	 * @param type the type
	 * @param outVertex the out vertex
	 * @param inVertex the in vertex
	 * @return the edge
	 * @throws AAIException the AAI exception
	 */
	private Edge addEdge(EdgeType type, Vertex outVertex, Vertex inVertex) throws AAIException {

		EdgeRule rule = this.getEdgeRule(outVertex, inVertex);
		if (type.equals(EdgeType.COUSIN) && rule.getIsParent().equals("true")) {
			throw new AAIException("AAI_6145");
		}
		Edge e = null;
		if (this.validateMultiplicity(rule, outVertex, inVertex)) {
			if (rule.getDirection().equals(Direction.OUT)) {
				e = outVertex.addEdge(rule.getLabel(), inVertex);
			} else if (rule.getDirection().equals(Direction.IN)) {
				e = inVertex.addEdge(rule.getLabel(), outVertex);
			}
			
			this.addProperties(e, rule);
		}
		return e;
	}

	/**
	 * Adds the properties.
	 *
	 * @param edge the edge
	 * @param rule the rule
	 */
	public void addProperties(Edge edge, EdgeRule rule) {
		
		// In DbEdgeRules.EdgeRules -- What we have as "edgeRule" is a comma-delimited set of strings.
		// The first item is the edgeLabel.
		// The second in the list is always "direction" which is always OUT for the way we've implemented it.
		// Items starting at "firstTagIndex" and up are all assumed to be booleans that map according to 
		// tags as defined in EdgeInfoMap.
		// Note - if they are tagged as 'reverse', that means they get the tag name with "-REV" on it
		Map<String, String> propMap = rule.getEdgeProperties();
		
		for (String key : propMap.keySet()) {
			String revKeyname = key + "-REV";
			String triple = propMap.get(key);
			if(triple.equals("true")){
				edge.property(key, true);
				edge.property(revKeyname,false);
			} else if (triple.equals("false")) {
				edge.property(key, false);
				edge.property(revKeyname,false);
			} else if (triple.equals("reverse")) {
				edge.property(key, false);
				edge.property(revKeyname,true);
			}
		}
	}
	
	/**
	 * Checks for edge rule.
	 *
	 * @param outType the out type
	 * @param inType the in type
	 * @return true, if successful
	 */
	private boolean hasEdgeRule(String outType, String inType) {
		
		Collection<String> collection = rules.get(outType + "|" + inType);

		return !collection.isEmpty();
		
	}
	
	/**
	 * Checks for edge rule.
	 *
	 * @param outVertex the out vertex
	 * @param inVertex the in vertex
	 * @return true, if successful
	 */
	private boolean hasEdgeRule(Vertex outVertex, Vertex inVertex) {
		String outType = (String)outVertex.<String>property("aai-node-type").orElse(null);
		String inType = (String)inVertex.<String>property("aai-node-type").orElse(null);
		
		return this.hasEdgeRule(outType, inType);
		
	}
	
	/**
	 * Gets the edge rule.
	 *
	 * @param outType the out type
	 * @param inType the in type
	 * @return the edge rule
	 * @throws AAIException the AAI exception
	 */
	public EdgeRule getEdgeRule(String outType, String inType) throws AAIException {
		EdgeRule rule = new EdgeRule();
		Collection<String> collection = null;
		boolean isFlipped = false;
		if (this.hasEdgeRule(outType, inType)) {
			
		} else if (this.hasEdgeRule(inType, outType)) {
			//flip values
			String tempType = inType;
			inType = outType;
			outType = tempType;
			isFlipped = true;
		} else {
			String detail = "No EdgeRule found for passed nodeTypes: " + outType + ", " + inType + ".";
			throw new AAIException("AAI_6120", detail); 
		}
		collection = rules.get(outType + "|" + inType);
		
		String[] info = collection.iterator().next().split(",");
		rule.setLabel(info[this.EDGE_NAME]);
		rule.setMultiplicityRule(MultiplicityRule.valueOf(info[this.MULTIPLICITY_RULE].toUpperCase()));
		rule.setHasDelTarget(info[this.HAS_DEL_TARGET]);
		rule.setUsesResource(info[this.USES_RESOURCE]);
		rule.setIsParent(info[this.IS_PARENT]);
		rule.setServiceInfrastructure(info[this.SVC_INFRA]);
		Direction direction = Direction.valueOf(info[this.DIRECTION]);
		if (isFlipped && direction.equals(Direction.OUT)) {
			rule.setDirection(Direction.IN);
		} else if (isFlipped && direction.equals(Direction.IN)){
			rule.setDirection(Direction.OUT);
		} else {
			rule.setDirection(direction);
		}

		return rule;
	}
	
	/**
	 * Gets the edge rule.
	 *
	 * @param outVertex the out vertex
	 * @param inVertex the in vertex
	 * @return the edge rule
	 * @throws AAIException the AAI exception
	 */
	public EdgeRule getEdgeRule(Vertex outVertex, Vertex inVertex) throws AAIException {
		String outType = (String)outVertex.<String>property(AAIProperties.NODE_TYPE).orElse(null);
		String inType = (String)inVertex.<String>property(AAIProperties.NODE_TYPE).orElse(null);
		
		return this.getEdgeRule(outType, inType);

		
	}
	
	/**
	 * Gets the delete semantic.
	 *
	 * @param nodeType the node type
	 * @return the delete semantic
	 */
	public DeleteSemantic getDeleteSemantic(String nodeType) {
		Collection<String> semanticCollection = deleteScope.get(nodeType);
		String semantic = semanticCollection.iterator().next();
		
		return DeleteSemantic.valueOf(semantic);
		
	}
	
	/**
	 * Validate multiplicity.
	 *
	 * @param rule the rule
	 * @param outVertex the out vertex
	 * @param inVertex the in vertex
	 * @return true, if successful
	 * @throws AAIException the AAI exception
	 */
	private boolean validateMultiplicity(EdgeRule rule, Vertex outVertex, Vertex inVertex) throws AAIException {

		if (rule.getDirection().equals(Direction.OUT)) {
			
		} else if (rule.getDirection().equals(Direction.IN)) {
			Vertex tempV = inVertex;
			inVertex = outVertex;
			outVertex = tempV;
		}
				
		String outVertexType = outVertex.<String>property(AAIProperties.NODE_TYPE).orElse(null);
		String inVertexType =  inVertex.<String>property(AAIProperties.NODE_TYPE).orElse(null);
		String label = rule.getLabel();
		MultiplicityRule multiplicityRule = rule.getMultiplicityRule();
		List<Object> outEdges = outVertex.graph().traversal().V(outVertex).outE(label).as("outEdges").inV().has(AAIProperties.NODE_TYPE, inVertexType).select("outEdges").toList();
		List<Object> inEdges = inVertex.graph().traversal().V(inVertex).inE(label).as("inEdges").outV().has(AAIProperties.NODE_TYPE, outVertexType).select("inEdges").toList();
		String detail = "";
		if (multiplicityRule.equals(MultiplicityRule.ONE2ONE)) {
			if (inEdges.size() >= 1 || outEdges.size() >= 1 ) {
				detail = "multiplicity rule violated: only one edge can exist with label: " + label + " between " + outVertexType + " and " + inVertexType;
			}
		} else if (multiplicityRule.equals(MultiplicityRule.ONE2MANY)) {
			if (inEdges.size() >= 1) {
				detail = "multiplicity rule violated: only one edge can exist with label: " + label + " between " + outVertexType + " and " + inVertexType;
			}
		} else if (multiplicityRule.equals(MultiplicityRule.MANY2ONE)) {
			if (outEdges.size() >= 1) {
				detail = "multiplicity rule violated: only one edge can exist with label: " + label + " between " + outVertexType + " and " + inVertexType;
			}
		} else {
			
		}
		
		if (!detail.equals("")) {
			throw new AAIException("AAI_6140", detail);
		}
		
		return true;
		
	}
	
}
