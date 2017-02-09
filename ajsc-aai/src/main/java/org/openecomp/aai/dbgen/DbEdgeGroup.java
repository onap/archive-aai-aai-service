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

package org.openecomp.aai.dbgen;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.map.MultiValueMap;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.openecomp.aai.dbmodel.DbEdgeRules;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.exceptions.AAIExceptionWithInfo;
import org.openecomp.aai.ingestModel.DbMaps;
import org.openecomp.aai.ingestModel.IngestModelMoxyOxm;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLine;

import com.thinkaurelius.titan.core.TitanEdge;
import com.thinkaurelius.titan.core.TitanTransaction;
import com.thinkaurelius.titan.core.TitanVertex;

public class DbEdgeGroup {
	
	private static AAILogger aaiLogger = new AAILogger(DbEdgeGroup.class.getName());
	
	/**
	 * Replace edge group.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param startVert the start vert
	 * @param scope the scope
	 * @param relatedNodesMultiMap the related nodes multi map
	 * @param apiVersion the api version
	 * @throws AAIException the AAI exception
	 */
	public static void replaceEdgeGroup( String transId, 
										String fromAppId, 
										TitanTransaction graph,  
										TitanVertex startVert, 
										String scope, 
										MultiValueMap relatedNodesMultiMap, // supports multiple entries for same nodetype
										String apiVersion ) throws AAIException{
	LogLine logline = new LogLine();
	logline.init("aaidbgen", transId, fromAppId, "replaceEdgeGroup");
	logline.add("scope", scope);

	// --------------------------------------------------------------------
	// NOTE --- This routine is only used for "cousin" relationships. 
	// --------------------------------------------------------------------

	/*
	 *  scope can be one of these:  
	 *    a) "ALL_COUSIN_REL"  
	 *    b) "ONLY_PASSED_COUSINS_REL" (only look at the edge types that are
	 *                                 represented in the passed list of relationships)
	 *        
	 *   Given a startNode and a list of relationshipInfo, we need to check the database and then, 
	 *   1) Delete any in-scope db relationships which are not represented in the relationshipList.
	 *      So, for ALL_COUSIN_REL, we would delete any db relationship that had an edge with
	 *         parentOf = false if it was not represented in the passed Relationship List.
	 *      For ONLY_PASSED_COUSINS_REL - we'd do the same as above, but only for edges that match the 
	 *         type in the passed relationshipList.  We'd leave any others alone.
	 *   2) Then, Persist (add/update) all the remaining passed-in relationships.        
	 */

	if( !scope.equals("ALL_COUSIN_REL") && !scope.equals("ONLY_PASSED_COUSINS_REL")  ){
		String detail = "Illegal scope parameter passed: [" + scope + "].";
		logline.add("emsg", detail);
		aaiLogger.info(logline, false, "AAI_6120");
		throw new AAIException("AAI_6120", detail); 
	}

	HashMap <String,String> vidToNodeTypeInDbHash = new HashMap <String,String>();
	HashMap <String,String> nodeTypeInReqHash = new HashMap <String,String>();
	HashMap <String,TitanEdge> vidToEdgeInDbHash = new HashMap <String,TitanEdge>();
	HashMap <String,TitanVertex> vidToTargetVertexHash = new HashMap <String,TitanVertex>();

	//------------------------------------------------------------------------------------------------------------
	// 1) First -- look what is currently in the db -- 
	//        "cousins" => grab all nodes connected to startVertex that have edges with param: isParent = false.
	//------------------------------------------------------------------------------------------------------------
	GraphTraversalSource conPipeTraversal = startVert.graph().traversal();
	GraphTraversal<Vertex, Edge> conPipe = conPipeTraversal.V(startVert).bothE().has("isParent",false);
	// Note - it's ok if nothing is found
	String msg1 = "Found connected cousin vid(s) in db: ";
	if( conPipe != null ){
		while( conPipe.hasNext() ){
			TitanEdge ed = (TitanEdge) conPipe.next();
			TitanVertex cousinV = ed.otherVertex(startVert);
			String vid = cousinV.id().toString();
			String noTy = cousinV.<String>property("aai-node-type").orElse(null);
			vidToNodeTypeInDbHash.put(vid, noTy);
			vidToEdgeInDbHash.put(vid, ed);

			msg1 = msg1 + "[" + cousinV.id().toString() + "]";
		}
	}
	logline.add("Note-1", msg1 );

	//------------------------------------------------------------------------------------------------------------
	//2) Get a List of the Titan nodes that the end-state list wants to be connected to		
	//------------------------------------------------------------------------------------------------------------
	String msg2 = "They request edges to these vids:";
	ArrayList <TitanVertex> targetConnectedToVertList = new ArrayList<TitanVertex>();		
	if( relatedNodesMultiMap != null ) {
		
        Set entrySet = relatedNodesMultiMap.entrySet();
        Iterator it = entrySet.iterator();
        //System.out.println("  Object key  Object value");
        while (it.hasNext()) {
            Map.Entry mapEntry = (Map.Entry) it.next();
			String rel2Nt = (String) mapEntry.getKey();
			int i = 0;
			ArrayList <HashMap<String, Object>> propList = ((ArrayList<HashMap<String, Object>>)relatedNodesMultiMap.get(rel2Nt));
			while (i < propList.size()) {
				HashMap<String, Object> propFilterHash = (HashMap<String, Object>) propList.get(i++);
				
				TitanVertex targetVert;
				
				try {
					targetVert = DbMeth.getUniqueNodeWithDepParams( transId, 
																	fromAppId, 
																	graph, 
																	rel2Nt, 
																	propFilterHash, 
																	apiVersion );
				} catch (AAIException e) {
					if (e.getErrorObject().getErrorCode().equals("6114"))
						throw new AAIExceptionWithInfo("AAI_6129", 
														e, 
														"Node of type " + rel2Nt + " not found for properties:" + propFilterHash.toString(),
														propFilterHash,
														rel2Nt);
					else 
						throw e;
				}
			
				targetConnectedToVertList.add(targetVert);
	
				String vid = targetVert.id().toString();
				String noTy = targetVert.<String>property("aai-node-type").orElse(null);
				nodeTypeInReqHash.put(noTy, "");
				vidToTargetVertexHash.put(vid, targetVert);
	
				msg2 = msg2 + "[" + targetVert.id().toString() + "]";
			}
        }
	}
	logline.add("Note-2", msg2 );

	//-------------------------------------------------------------------------------------------------------------------
	// 3) Compare what is in the DB with what they are requesting as an end-state. 
	//    If any are found in the db-list but not the new-list, delete them from the db (conditionally - based on scope)
	//-------------------------------------------------------------------------------------------------------------------
	String startVtxNT = startVert.<String>property("aai-node-type").orElse(null);
	String msg3 = "We will DELETE existing DB-edge to vids = ";
	for( Map.Entry<String, TitanEdge> entry : vidToEdgeInDbHash.entrySet() ){
		String vertId = entry.getKey();
		TitanEdge dbEd = entry.getValue();
		if( ! vidToTargetVertexHash.containsKey(vertId) ){    
			if( scope.equals("ALL_COUSIN_REL") ){
				msg3 = msg3 + "[" + entry.getKey() + "]";
				DbMeth.removeAaiEdge(transId, fromAppId, graph, dbEd);
			}
			else if( scope.equals("ONLY_PASSED_COUSINS_REL") ){
				// If they use "ONLY_PASSED_COUSINS_REL" scope, they want us to delete an edge ONLY if:
				//      a) this edge is the same type that they passed in (but goes to a different endpoint)
				//  AND b) this additional edge would break the multiplicity edge rule.  
				String ntInDb = vidToNodeTypeInDbHash.get(vertId);
				if( nodeTypeInReqHash.containsKey(ntInDb) && additionalEdgeWouldBreakMultEdgeRule(startVtxNT, ntInDb) ){
					//System.out.println( "DEBUG -- Edge to vid = " + entry.getKey() + " will be deleted. ");
					msg3 = msg3 + "[" + entry.getKey() + "]";
					DbMeth.removeAaiEdge(transId, fromAppId, graph, dbEd);
				}
			}
		}
		else {
			//System.out.println( "DEBUG -- Edge to vid = " + entry.getKey() + " is on request and in DB, so do not delete from the DB.");
		}
	}
	logline.add("Note-3", msg3 );

	//---------------------------------------------------------------
	// 4) add/update (persist) all the relations on the new-list
	//---------------------------------------------------------------
	String msg4 = "Call persistAaiEdge on edge(s) going to vids = ";
	for( Map.Entry<String, TitanVertex> entry : vidToTargetVertexHash.entrySet() ){
		msg4 = msg4 + "[" + entry.getKey() + "]";
		TitanVertex targV = entry.getValue();
		DbMeth.persistAaiEdge(transId, fromAppId, graph, startVert, targV, apiVersion, "cousin");
	}
	logline.add("Note-4", msg4 );

	aaiLogger.info(logline, true, "0");
	return;

}// End replaceEdgeGroup()


/**
 * Additional edge would break mult edge rule.
 *
 * @param startNodeType the start node type
 * @param endNodeType the end node type
 * @return the boolean
 * @throws AAIException the AAI exception
 */
private static Boolean additionalEdgeWouldBreakMultEdgeRule( String startNodeType, String endNodeType )
	throws AAIException {
	// Return true if a second edge from the startNodeType to the endNodeType would
	// break a multiplicity edge rule.
	// Ie.  Adding an edge to a second tenant (endNode) from a vserver (startNode) node would be flagged by this
	//   if we have our multiplicity rule set up for the "vserver-tenant" edge set up as "Many2One" or if
	//   it was set up the other way, "tenant-vserver" as "One2Many" or even if it was "One2One".  In any of 
	//   those scenarios, the addition of an edge from a particular vserver to an additional tenant node
	//   would break the rule.
	String ruleKey = startNodeType + "|" + endNodeType;
	boolean reverseRule = false;
	if( DbEdgeRules.EdgeRules.containsKey(ruleKey) ){
		// We can use the edge rule info in the order given
	}
	else {
		ruleKey = endNodeType + "|" + startNodeType;
		if( DbEdgeRules.EdgeRules.containsKey(ruleKey) ){
			// we can use the rule, but need to reverse it to apply to the two nodeTypes we're looking at
			reverseRule = true;
		}
		else {
			// Couldn't find a rule for this edge
			String detail = "No EdgeRule found for passed nodeTypes: " + startNodeType + ", " + endNodeType + ".";
			throw new AAIException("AAI_6120", detail); 
		}
	}
	
	String edRule = "";
	Collection <String> edRuleColl =  DbEdgeRules.EdgeRules.get(ruleKey);
	Iterator <String> ruleItr = edRuleColl.iterator();
	if( ruleItr.hasNext() ){
		// For the current database, there can only be one type of edge between any two particular node-types.
		edRule = ruleItr.next();
	}
	else {
		// No edge rule found for this
		String detail = "Could not find an EdgeRule for derived edgeRuleKey (nodeTypeA|nodeTypeB): " + ruleKey + ".";
		throw new AAIException("AAI_6120", detail); 
	}
	
	int tagCount = DbEdgeRules.EdgeInfoMap.size();
	String [] rules = edRule.split(",");
	if( rules.length != tagCount ){
		String detail = "Bad EdgeRule data (itemCount =" + rules.length + ") for key = [" + ruleKey  + "].";
		throw new AAIException("AAI_6121", detail); 
	}

	int multIndex = -1;
	for( int i = 0; i < tagCount; i++ ){
		Integer mapKey = new Integer(i);
		String ruleName = DbEdgeRules.EdgeInfoMap.get(mapKey);
		if( ruleName.equals("multiplicityRule") ){
			multIndex = i;
			break;
		}
	}
	
	if( multIndex == -1 ){
		String detail = "Bad EdgeRule data (multiplicityRule not found) for key = [" + ruleKey  + "].";
		throw new AAIException("AAI_6121", detail); 
	}			
	
	String multRule = rules[multIndex];
	if( multRule.equals("Many2Many") ){
    	return false;
    }
    else if( multRule.equals("One2One") ){
    	return true;
    }
    else if( !multRule.equals("One2Many") && !multRule.equals("Many2One") ){
    	String detail = "Bad EdgeRule data - unrecognized rule [" + multRule + "] for key = [" + ruleKey + "].";
		throw new AAIException("AAI_6121", detail); 
    }
     
    if( !reverseRule && multRule.equals("Many2One") ){
    	// We're looking at the rule in the "normal" direction as listed in EdgeRules
    	// Since there should only be one of the end-nodeType for this kind of source-nodeType and
    	// they want to add an additional edge from this source to a different node of this end-nodeType, 
    	// we will return TRUE since that would violate the rule.
    	return true;
    }
    else if( reverseRule && multRule.equals("One2Many") ){
    	// We're looking at the rule in the reverse direction
    	return true;
    }
    else {
    	// Multiplicity rule would not be violated by adding an edge from this source to another end-nodeType node
    	return false;
    }
	
}// end of additionalEdgeWouldBreakMultEdgeRule()

/**
 * Delete edge group.
 *
 * @param transId the trans id
 * @param fromAppId the from app id
 * @param graph the graph
 * @param startVert the start vert
 * @param relatedNodesMultiMap the related nodes multi map
 * @param apiVersion the api version
 * @return void
 * @throws AAIException the AAI exception
 */
public static void deleteEdgeGroup( String transId, 
									String fromAppId, 
									TitanTransaction graph,  
									TitanVertex startVert, 
									MultiValueMap relatedNodesMultiMap, 
									String apiVersion ) throws AAIException{
	LogLine logline = new LogLine();
	logline.init("aaidbgen", transId, fromAppId, "deleteEdgeGroup");

	// --------------------------------------------------------------------
	// NOTE - This routine is only used for "cousin" relationships. 
	// ALSO - an edge deletion will fail if that edge was needed by
	//        the node on one of the sides for uniqueness.  We're just 
	//        being careful here - so far, I don't think a cousin-edge
	//        is ever used for this kind of dependency.
	// --------------------------------------------------------------------

	HashMap <String,TitanEdge> cousinVidToEdgeInDbHash = new HashMap <String,TitanEdge>();
	String startVertNT = startVert.<String>property("aai-node-type").orElse(null);
	String startVertVid = startVert.id().toString();
	DbMaps dbMaps = IngestModelMoxyOxm.dbMapsContainer.get(apiVersion);
	Collection <String> startVertDepNTColl =  dbMaps.NodeDependencies.get(startVertNT);

	//-----------------------------------------------------------------------------------------------------
	// Get a list of vertexes that are attached to the startVert as "cousins" and the connecting edges
	//-----------------------------------------------------------------------------------------------------
	GraphTraversalSource conPipeTraversal = startVert.graph().traversal();
	GraphTraversal<Vertex, Edge> conPipe = conPipeTraversal.V(startVert).bothE().has("isParent",false);
	if( conPipe != null ){
		while( conPipe.hasNext() ){
			TitanEdge ed = (TitanEdge) conPipe.next();
			TitanVertex cousinV = ed.otherVertex(startVert);
			String vid = cousinV.id().toString();
			cousinVidToEdgeInDbHash.put(vid, ed);
		}
	}

	//-------------------------------------------------------------
	// 	Look through the Relationship info passed in.
	//  Delete edges as requested if they check-out as cousins.
	//-------------------------------------------------------------
	Boolean isFirst = true;
	String msg = "Deleting edges from vid = " + startVertVid + "(" + startVertNT + "), to these: [";
	if( relatedNodesMultiMap != null ) {			
        Set entrySet = relatedNodesMultiMap.entrySet();
        Iterator it = entrySet.iterator();
        //System.out.println("  Object key  Object value");
        while (it.hasNext()) {
            Map.Entry mapEntry = (Map.Entry) it.next();
			String rel2Nt = (String) mapEntry.getKey();
			HashMap<String, Object> propFilterHash = (HashMap<String, Object>)((ArrayList) relatedNodesMultiMap.get(rel2Nt)).get(0);
			TitanVertex otherEndVert = DbMeth.getUniqueNodeWithDepParams( transId, fromAppId, graph, rel2Nt, propFilterHash, apiVersion ); 
			String otherNT = otherEndVert.<String>property("aai-node-type").orElse(null);
			String reqDelConnectedVid = otherEndVert.id().toString();
			if( !cousinVidToEdgeInDbHash.containsKey(reqDelConnectedVid) ){
				String detail = "COUSIN Edge between " + startVertVid + " (" + startVertNT + ") and " + reqDelConnectedVid +
						"(" + otherNT + ") not found. ";
				logline.add("emsg", detail);
				aaiLogger.info(logline, false, "AAI_6127");
				throw new AAIException("AAI_6127", detail); 
			}
			else {
				// This was a cousin edge.   But before we delete it, we will check to make
				// sure it doesn't have a unique-dependency issue (need to check in two directions)
				Iterator <String> ntItr1 = startVertDepNTColl.iterator();
				if( ntItr1.hasNext() ){
					while( ntItr1.hasNext() ){
						if( ntItr1.next().equals(otherNT) ){
							String detail = "Edge between " + startVertVid + " and " + reqDelConnectedVid +
									" cannot be deleted because of a uniqueness-dependancy between nodeTypes, " +
									startVertNT + " and " + otherNT;
							logline.add("emsg", detail);
							aaiLogger.info(logline, false, "AAI_6126");
							throw new AAIException("AAI_6126", detail); 
						}
					}
				}

				Collection <String> depNTColl =  dbMaps.NodeDependencies.get(otherNT);
				Iterator <String> ntItr2 = depNTColl.iterator();
				if( ntItr2.hasNext() ){
					while( ntItr2.hasNext() ){
						if( ntItr2.next().equals(startVertNT) ){
							String detail = "Edge between " + startVertVid + " and " + reqDelConnectedVid +
									" cannot be deleted because of a uniqueness-dependancy between nodeTypes: " +
									otherNT + " and " + startVertNT;
							logline.add("emsg", detail);
							aaiLogger.info(logline, false, "AAI_6126");
							throw new AAIException("AAI_6126", detail); 
						}
					}
				}

				// It's OK to delete this edge as requested.
				if( ! isFirst ){
					msg = msg + ", ";
				}
				isFirst = false;
				msg = msg + reqDelConnectedVid + "(" + otherNT + ")";
				TitanEdge targetDelEdge = cousinVidToEdgeInDbHash.get(reqDelConnectedVid);
				DbMeth.removeAaiEdge(transId, fromAppId, graph, targetDelEdge);
			}
		}
	}

	msg = msg + "]";
	logline.add("Note ", msg );
	aaiLogger.info(logline, true, "0");
	return;

}// End deleteEdgeGroup()


/**
 * Gets the edge group.
 *
 * @param transId the trans id
 * @param fromAppId the from app id
 * @param graph the graph
 * @param startVert the start vert
 * @param vidToNodeTypeHash the vid to node type hash
 * @param vidToVertexHash the vid to vertex hash
 * @param scope the scope
 * @param apiVersion the api version
 * @return void
 * @throws AAIException the AAI exception
 * @throws UnsupportedEncodingException the unsupported encoding exception
 */
public static void getEdgeGroup( String transId, 
								String fromAppId, 
								TitanTransaction graph,  
								TitanVertex startVert, 
								HashMap <String,String> vidToNodeTypeHash,
								HashMap <String,TitanVertex> vidToVertexHash,
								String scope, 
								String apiVersion ) throws AAIException, UnsupportedEncodingException{
	LogLine logline = new LogLine();
	logline.init("aaidbgen", transId, fromAppId, "getEdgeGroup");
	logline.add("scope", scope);
	logline.add("vid", startVert.id().toString());
	logline.add("nodetype", startVert.property("aai-node-type").orElse(null).toString());

	/*
	 *  scope can be one of these:  
	 *    1) "ONLY_COUSIN_REL"   <-- This is the only one supported for now
	 *    2) "ALL_COUSIN_AND_CHILDREN_REL"
	 *    3) "ONLY_CHILDREN" 
	 *    4) "USES_RESOURCE"
	 *        
	 *   Given a startNode and the scope, we need to return relationships that we find in the DB
	 */

	if( !scope.equals("ONLY_COUSIN_REL") ){
		String detail = "Illegal scope parameter passed: [" + scope + "].";
		logline.add("emsg", detail);
		aaiLogger.info(logline, false, "AAI_6120");
		throw new AAIException("AAI_6120", detail); 
	}

	//------------------------------------------------------------------------------------------------------------
	// Grab "first-layer" vertexes from the in the db -- 
	//        "cousins" => grab all nodes connected to startVertex that have edges with param: isParent = false.
	//        "children" => grab nodes via out-edge with isParent = true    (NOT YET SUPPORTED)
	//------------------------------------------------------------------------------------------------------------
	Iterable<Vertex> qResult = startVert.query().has("isParent",false).vertices();
	Iterator <Vertex> resultI = qResult.iterator();
	String msg1 = "Found connected cousin vid(s) in db: ";
	while( resultI.hasNext() ){
		TitanVertex cousinV = (TitanVertex)resultI.next();
		//showPropertiesForNode( transId, fromAppId, cousinV );

		String vid = cousinV.id().toString();
		String noTy = cousinV.<String>property("aai-node-type").orElse(null);
		vidToNodeTypeHash.put(vid, noTy);
		vidToVertexHash.put(vid, cousinV);

		msg1 = msg1 + "[" + cousinV.id().toString() + "]";
	}
	logline.add("Note-1", msg1 );		

	aaiLogger.info(logline, true, "0");

}// End getEdgeGroup()


}
