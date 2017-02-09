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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.util.AAIConfig;

import com.thinkaurelius.titan.core.TitanTransaction;
import com.thinkaurelius.titan.core.TitanVertex;



/**
 * Database-level Search-Utility class that uses edge-tags to help it navigate the graph.   
 */
public class DbSearchWithTags{

	private static AAILogger aaiLogger = new AAILogger(DbSearchWithTags.class.getName());


	/**
	 * Identify top node set.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param edgeTag the edge tag
	 * @param topNodeType the top node type
	 * @param initialFilterHash the initial filter hash
	 * @param maxLevels the max levels
	 * @return List<titanVertex>
	 * @throws AAIException the AAI exception
	 */
	  public static HashMap <String, TitanVertex> identifyTopNodeSet( String transId, String fromAppId, TitanTransaction graph,
			  String edgeTag, String topNodeType, HashMap <String,Object> initialFilterHash, int maxLevels )   
					  throws AAIException {
			  
		  
		  LogLine logline = new LogLine();
		  logline.init("aaidbgen", transId, fromAppId, "identifyInitialNodeSet");
	    	
		  HashMap <String, TitanVertex> topVertHash = new HashMap <String, TitanVertex>();
		  if( graph == null ){
			  String emsg = "null graph object passed to identifyInitialNodeSet()\n";
			  logline.add("emsg", emsg);         	 
			  aaiLogger.info(logline, false, "AAI_6101");
			  throw new AAIException("AAI_6101", emsg); 
		  }
	  
		  
		  // Given the filter, we want to select all the nodes of the type the filter tells us that have the
		  // 	property they gave us.
		  // Then looping through those start points, we will look "up" and then "down" to find the set of target/top nodes.
		 
		  if( initialFilterHash == null || initialFilterHash.isEmpty() ){
			  String emsg = " initialFilterHash is required for identifyInitialNodeSet() call. \n";
			  logline.add("emsg", emsg);         	 
			  aaiLogger.info(logline, false, "AAI_6118");
			  throw new AAIException("AAI_6118", emsg); 
		  }
		  
		  // NOTE: we're expecting the filter to have a format like this: "nodeType.parameterName:parameterValue"
		  Iterator <?> it = initialFilterHash.entrySet().iterator();
		  // -- DEBUG -- for now we only deal with ONE initial filter parameter
		  //     it would be easy enough to deal with multiple parameters if they all
		  //     applied to the same nodeType.
		  String propNodeTypeDotName = "";
		  String initNodeType = "";
		  String initPropName = "";
		  HashMap <String,Object> initFilterTweakedHash = new HashMap <String,Object>();

		  HashMap <String,Object> initFilterHashUpper = new HashMap <String,Object>();
		  HashMap <String,Object> initFilterHashLower = new HashMap <String,Object>();
		  Boolean isKludgeCase = false;		 
		  String extraChecks = "";
		  
		  String propVal = "";
		  if( it.hasNext() ){
			  Map.Entry<?,?> propEntry = (Map.Entry<?,?>) it.next();
			  propNodeTypeDotName = (propEntry.getKey()).toString();
			  propVal = (propEntry.getValue()).toString();
		  }
		  
		  int periodLoc = propNodeTypeDotName.indexOf(".");
		  if( periodLoc <= 0 ){
			  String emsg = "Bad filter param key passed in: [" + propNodeTypeDotName + "].  Expected format = [nodeName.paramName]\n";
			  logline.add("emsg", emsg);         	 
			  aaiLogger.info(logline, false, "AAI_6120");
			  throw new AAIException("AAI_6120", emsg); 
		  }
		  else {
			  initNodeType = propNodeTypeDotName.substring(0,periodLoc);
			  initPropName = propNodeTypeDotName.substring(periodLoc + 1);
			  initFilterTweakedHash.put(initPropName, propVal);
			  
			  if( (initNodeType.equals("generic-vnf") && initPropName.equals("vnf-name"))
					  || (initNodeType.equals("vserver") && initPropName.equals("vserver-name")) ){
				  isKludgeCase = true;
				  String propValUpper = propVal.toUpperCase();
				  initFilterHashUpper.put(initPropName, propValUpper);
				  String propValLower = propVal.toLowerCase();
				  initFilterHashLower.put(initPropName, propValLower);
				  extraChecks = ", " + initFilterHashUpper.toString() + ", " + initFilterHashLower.toString();
			  }		  
		  }
	
		  ArrayList <TitanVertex> startVList = DbMeth.getNodes( transId, fromAppId, graph, initNodeType, initFilterTweakedHash, true, "v?"); 
		  if( startVList.isEmpty() && isKludgeCase ){
			  // We couldn't find any start nodes, but for two kludged cases, we will look again with all upper case 
			  // and then again with all Lower case if needed.
			  startVList = DbMeth.getNodes( transId, fromAppId, graph, initNodeType, initFilterHashUpper, true, "v?"); 
			  if( startVList.isEmpty() ){
				  // Still have no results, but since we're in the kludge case, we'll try again.
				  startVList = DbMeth.getNodes( transId, fromAppId, graph, initNodeType, initFilterHashLower, true, "v?");
			  }
		  }
		  
		  // To make sure we don't have duplicates, create a hash of the resulting vertices using their vertexId as the key
		  HashMap<String, TitanVertex> startVertHash = new HashMap<String, TitanVertex>();
		  Iterator<TitanVertex> iter = startVList.iterator(); 
		  while( iter.hasNext() ){ 
			  TitanVertex tvx = iter.next(); 
			  String vid = tvx.id().toString();
			  startVertHash.put(vid,tvx);
		  }
			  
		  if( startVertHash.isEmpty() ){
			   System.out.println("Probably want to log this -- but, No vertices found for inital search conditions: [ " +
					   initialFilterHash.toString() + "]" + extraChecks );
		  }
		  else {
			  for( Map.Entry<String, TitanVertex> entry : startVertHash.entrySet() ){
					// For each starting point vertex found, we need to look for 'top-level' vertices that correspond to it
					TitanVertex tv = entry.getValue();  
					HashMap <String,TitanVertex> targVHash = lookForTargetsUsingStartNodes( transId, fromAppId, graph, 
							edgeTag, topNodeType, tv, maxLevels );
					
					Iterator <?> vit = targVHash.entrySet().iterator();
					while( vit.hasNext() ){
						Map.Entry<?,?> propEntry = (Map.Entry<?,?>) vit.next();
						String foundVid = (propEntry.getKey()).toString();
						TitanVertex foundVtx = (TitanVertex) (propEntry.getValue());
						topVertHash.put(foundVid, foundVtx);
					}		
			  }
		  }
		  
		  if( topVertHash.isEmpty() ){
				// No Vertex was found  - throw a not-found exception
				String msg = "No Node of type " + topNodeType + " found for properties: " + initialFilterHash.toString() + extraChecks;
				logline.add("msg", msg);
				aaiLogger.info(logline, false, "AAI_6114");
				throw new AAIException("AAI_6114", msg);
		  }
		  else {
			  return topVertHash;
		  }
		  
	  }// End identifyInitialNodeSet()
  	  
		
	/**
	 * Look for targets using start nodes.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param edgeTag the edge tag
	 * @param topNodeType the top node type
	 * @param startVtx the start vtx
	 * @param maxLevels the max levels
	 * @return HashMap<vtxId,titanVertex>
	 * @throws AAIException the AAI exception
	 */
	  public static HashMap <String, TitanVertex> lookForTargetsUsingStartNodes( String transId, String fromAppId, TitanTransaction graph,
			  String edgeTag, String topNodeType, TitanVertex startVtx, int maxLevels )   
					  throws AAIException {
			
		  LogLine logline = new LogLine();
		  logline.init("aaidbgen", transId, fromAppId, "lookForTargetsUsingStartNodes");
	    	
		  if( graph == null ){
			  String emsg = "null graph object passed to lookForTargetsUsingStartNodes()\n";
			  logline.add("emsg", emsg);         	 
			  aaiLogger.info(logline, false, "AAI_6101");
			  throw new AAIException("AAI_6101", emsg); 
		  }
		  
		  // Walk the graph using tagged "IN" edges to try to find topNodeType vertices
		  HashMap <String, TitanVertex> targetVertHash = lookForTargetsInOneDirection( transId, fromAppId, graph,
				  edgeTag, topNodeType, startVtx, Direction.IN, 0, maxLevels );
		    
		  // Walk the graph using tagged "OUT" edges to find vertices of the topNodeType
		  HashMap <String, TitanVertex> foundOutVtxHash = lookForTargetsInOneDirection( transId, fromAppId, graph,
				  edgeTag, topNodeType, startVtx, Direction.OUT, 0, maxLevels );
		  
		  // Add the targetVertHash that was found for IN edges with the foundOutVtxHash 
  		  if( !foundOutVtxHash.isEmpty() ){
  			  Iterator <?> vit = foundOutVtxHash.entrySet().iterator();
  			  while( vit.hasNext() ){
  				  Map.Entry<?,?> propEntry = (Map.Entry<?,?>) vit.next();
  				  String foundVid = (propEntry.getKey()).toString();
  				  TitanVertex foundVtx = (TitanVertex) (propEntry.getValue());
  				  targetVertHash.put(foundVid, foundVtx);
  			  }
  		  }
		  
		  return targetVertHash;
		  
	  } // End of lookForTargetsUsingStartNodes()
	
	  
	/**
	 * Look for targets in one direction.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param edgeTag the edge tag
	 * @param topNodeType the top node type
	 * @param startVtx the start vtx
	 * @param dir the dir
	 * @param levelCounter the level counter
	 * @param maxLevels the max levels
	 * @return HashMap<vtxId,titanVertex>
	 * @throws AAIException the AAI exception
	 */
	  public static HashMap <String, TitanVertex> lookForTargetsInOneDirection( String transId, String fromAppId, TitanTransaction graph,
			  String edgeTag, String topNodeType, TitanVertex startVtx, Direction dir, int levelCounter, int maxLevels )   
					  throws AAIException {
		  
		  LogLine logline = new LogLine();
		  logline.init("aaidbgen", transId, fromAppId, "lookForTargetsInOneDirection");
		  levelCounter++;

		  //System.out.println(" DEBUG -- levelcount in lookForTargetsInOneDirection = " + levelCounter);

      	  if( levelCounter > maxLevels ) {
			  String emsg = "lookForTargetsInOneDirection() has looped across more levels than allowed: " + maxLevels + ". ";
			  logline.add("emsg", emsg);         	 
			  aaiLogger.info(logline, false, "AAI_6125");
			  throw new AAIException("AAI_6125", emsg); 
      	  }
      
		  HashMap <String, TitanVertex> targetVertHash = new HashMap <String, TitanVertex>();
		  
		  if( startVtx.property("aai-node-type").orElse(null).equals(topNodeType) ){
			  // We're starting on the kind of node we were looking for - so just return it, don't keep looking
			  // NOTE - this assumes that we will not find layers of the target-type node -- 
			  String vid = startVtx.id().toString();
			  targetVertHash.put(vid, startVtx);
			  return targetVertHash;
		  }
		
		  //System.out.println("DEBUG -- about to run the actual edge query (dir = " + dir + ", tag = " + edgeTag +  ", level = " + levelCounter );
		 
		  // Look for target nodes using results we got looking at normal edges in the "normal" direction
		  Iterable <?> verts = startVtx.query().direction(dir).has(edgeTag,true).vertices();
		  Iterator <?> vertI = verts.iterator();
		  while( vertI != null && vertI.hasNext() ){
        	  TitanVertex tmpVert = (TitanVertex) vertI.next();
        	  String vid = tmpVert.id().toString();
        	  String nType = tmpVert.<String>property("aai-node-type").orElse(null);
        	  //System.out.println("DEBUG -- going " + dir + ", found a node [" + nType + "], id = " + vid + " -- ");
          	  if( nType.equals(topNodeType) ){
          		  // We found a vertex that meets the input criteria - put it on our output hash.
          		  targetVertHash.put(vid, tmpVert);
          	  }
          	  else {
          		  // keep looking in the same direction
          		  //System.out.println("DEBUG1 -- do the actual recursive call staring from this node (vid = " + vid + "), direction = " + dir);
          		  HashMap <String, TitanVertex> tmpHash = lookForTargetsInOneDirection( transId, fromAppId, graph,
          				  edgeTag, topNodeType, tmpVert, dir, levelCounter, maxLevels );
          		  if( !tmpHash.isEmpty() ){
          			  // We found more vertexes to add to the return Hash
          			  Iterator <?> vit = tmpHash.entrySet().iterator();
          			  while( vit.hasNext() ){
          				  Map.Entry<?,?> propEntry = (Map.Entry<?,?>) vit.next();
          				  String foundVid = (propEntry.getKey()).toString();
          				  TitanVertex foundVtx = (TitanVertex) (propEntry.getValue());
          				  targetVertHash.put(foundVid, foundVtx);
          			  }
          		  }
          	  }
          }
		  
		  // Also walk the graph with "-REV" tags for edges that are opposite of the direction asked for
		  String revEdgeTag = edgeTag + "-REV";
		  Direction revDir = Direction.IN;
		  if( dir.equals(Direction.IN) ){
			  revDir = Direction.OUT;
		  }
		  //System.out.println("DEBUG -- about to run the REVERSE edge query (dir = " + revDir + ", tag = " + revEdgeTag +  ", level = " + levelCounter );
		  Iterable <?> vertsRev = startVtx.query().direction(revDir).has(revEdgeTag,true).vertices();
		  Iterator <?> vertIRev = vertsRev.iterator();
		  while( vertIRev != null && vertIRev.hasNext() ){
        	  TitanVertex tmpVertRev = (TitanVertex) vertIRev.next();
        	  String vid = tmpVertRev.id().toString();
        	  String nType = tmpVertRev.<String>property("aai-node-type").orElse(null);
        	  //System.out.println("DEBUG -- going REV: " + revDir + ", found a node [" + nType + "], id = " + vid + ")");
        	  
          	  if( nType.equals(topNodeType) ){
          		  // We found a vertex that meets the input criteria - put it on our output hash.
          		  targetVertHash.put(vid, tmpVertRev);
          	  }
          	  else {
          		  // keep looking in the same direction -- Ie. the same direction that this method was called with
          		  //System.out.println("DEBUG2 -- do the actual recursive call staring from this node (vid = " + vid + "), direction = " + dir + "----");
          		  HashMap <String, TitanVertex> tmpHash = lookForTargetsInOneDirection( transId, fromAppId, graph,
          				  edgeTag, topNodeType, tmpVertRev, dir, levelCounter, maxLevels );
          		  if( !tmpHash.isEmpty() ){
          			  // We found more vertexes to add to the return Hash
          			  Iterator <?> vit = tmpHash.entrySet().iterator();
          			  while( vit.hasNext() ){
          				  Map.Entry<?,?> propEntry = (Map.Entry<?,?>) vit.next();
          				  String foundVid = (propEntry.getKey()).toString();
          				  TitanVertex foundVtx = (TitanVertex) (propEntry.getValue());
          				  targetVertHash.put(foundVid, foundVtx);
          			  }
          		  }
          	  }
          }
  
		  return targetVertHash;
	}// End lookForTargetsInOneDirection()
		  
		    
	/**
	 * Collect result set.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param edgeTag the edge tag
	 * @param thisLevelVertex the this level vertex
	 * @param secFilterHash the sec filter hash
	 * @param retNodeType the ret node type
	 * @param levelCounter the level counter
	 * @param maxLevels the max levels
	 * @return resultSet
	 * @throws AAIException the AAI exception
	 */
	  public static ResultSet collectResultSet( String transId, String fromAppId, TitanTransaction graph,
			  String edgeTag, TitanVertex thisLevelVertex, HashMap <String,Object> secFilterHash, 
			  String retNodeType, int levelCounter, int maxLevels )   
					  throws AAIException {

		  // Note:  our return data set is everything on the OUT-edge side of the topVertex - or following
		  //        an IN-edge but with a "-REV" tagged edge
		  levelCounter++;
		  LogLine logline = new LogLine();
		  logline.init("aaidbgen", transId, fromAppId, "collectResultSet");
		  
		  if( graph == null ){
			  String emsg = "null graph object passed to collectResultSet()\n";
			  logline.add("emsg", emsg);         	 
			  aaiLogger.info(logline, false, "AAI_6101");
			  throw new AAIException("AAI_6101", emsg); 
		  }
		  
		  //String thisVid = thisLevelVertex.id().toString();
		  //String thisNt = thisLevelVertex.<String>property("aai-node-type").orElse(null);
		  //System.out.println(" DEBUG -- level counter in collectResultSet = " + levelCounter + " thisNodeType = " + thisNt + " this vid = " + thisVid);

      	  if( levelCounter > maxLevels ) {
			  String emsg = "collectResultSet() has looped across more levels than allowed: " + maxLevels + ". ";
			  logline.add("emsg", emsg);         	 
			  aaiLogger.info(logline, false, "AAI_6125");
			  throw new AAIException("AAI_6125", emsg); 
      	  }
		  
      	  ResultSet rs = new ResultSet();
		  rs.vert = thisLevelVertex;
		
		  // Look at the tagged "OUT" edges for this node	
		  //System.out.println("DEBUG -- about to run the collectResult query at level = " + levelCounter );
		  Iterable <?> verts = thisLevelVertex.query().direction(Direction.OUT).has(edgeTag,true).vertices();
		  Iterator <?> vertI = verts.iterator();
		  
		  ArrayList <TitanVertex> nodeList = new <TitanVertex> ArrayList ();
		  while( vertI != null && vertI.hasNext() ){
        	  TitanVertex tmpVert = (TitanVertex) vertI.next();
        	  nodeList.add(tmpVert);
		  }
		  
		  // Look at the reverse-tagged "IN" edges for this node
		  String reverseEdgeTag = edgeTag + "-REV";
		  Iterable <?> vertsRev = thisLevelVertex.query().direction(Direction.IN).has(reverseEdgeTag,true).vertices();
		  Iterator <?> vertIRev = vertsRev.iterator();
		  while( vertIRev != null && vertIRev.hasNext() ){
        	  TitanVertex tmpVert = (TitanVertex) vertIRev.next();
        	  nodeList.add(tmpVert);
		  }
		  
		  if( nodeList.isEmpty() ){
			  // There were no sub-vertices, so we can return this result set
			  //System.out.println("DEBUG -- no subVert found here - we will return the resultSet with no sub dudes. ");
		  }
		  else {
			  // For each sub-vertex found, need to get it's result set (recursively)
			  Iterator<?> nodeIter = nodeList.iterator();
			  while( nodeIter.hasNext() ){
	        	  TitanVertex tmpVert = (TitanVertex) nodeIter.next();
	          	  ResultSet tmpResSet = collectResultSet( transId, fromAppId, graph, edgeTag, tmpVert, 
	            		  secFilterHash, retNodeType, levelCounter, maxLevels );
	          	  rs.subResultSet.add(tmpResSet);
	          	  //System.out.println("DEBUG -- found this guy at level " + levelCounter + ", nodeType = " + tmpVert.property("aai-node-type").orElse(null));
			  }
		  }
		  
		  //System.out.println("DEBUG -- returning from a call to collectResultSet() ");
		  return rs;
		  
	  } // End of collectResultSet()
	
  
	/**
	 * Collect result set.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param edgeTag the edge tag
	 * @param thisLevelVertex the this level vertex
	 * @param secFilterHash the sec filter hash
	 * @param retNodeType the ret node type
	 * @param levelCounter the level counter
	 * @param fwdSearchDirection the fwd search direction
	 * @param revSearchDirection the rev search direction
	 * @param doPruning the do pruning
	 * @param pruneNodeType -- nodeType of nodes we want to stop collecting at
	 * @param pruneKeepId -- vertexId of a pruneType node that we DO want to keep
	 * @param trimList the trim list
	 * @param maxLevels the max levels
	 * @return resultSet
	 * @throws AAIException the AAI exception
	 */
	  public static ResultSet collectResultSet( String transId, String fromAppId, TitanTransaction graph,
			  String edgeTag, 
			  TitanVertex thisLevelVertex, 
			  HashMap <String,Object> secFilterHash, 
			  String retNodeType, 
			  int levelCounter,
			  Direction fwdSearchDirection,
			  Direction revSearchDirection,
			  Boolean doPruning, 
			  String pruneNodeType,
			  String pruneKeepId,
			  ArrayList trimList,
			  int maxLevels )   
					  throws AAIException {

		  // Note - our return data set is everything found starting at this vertex and found 
		  //     following edges that are either a) in the search-direction, or b) tagged the opposite of the 
		  //     search-Direction, but tagged to be followed in "-REV" direction.
		  
		  // Note - when pruning, if we hit a node of type pruneNodeType that does Not have the ID matching
		  //        pruneKeepId, then we do NOT add that vertex to our result set
		  
		  levelCounter++;
		  LogLine logline = new LogLine();
		  logline.init("aaidbgen", transId, fromAppId, "collectResultSet");
		  
		  boolean needToDoPruning = false;
		  if( doPruning && pruneKeepId != null && !pruneKeepId.equals("") && pruneNodeType != null && !pruneNodeType.equals("") ){
			  needToDoPruning = true;
		  }
		  
		  if( graph == null ){
			  String emsg = "null graph object passed to collectResultSet()\n";
			  logline.add("emsg", emsg);         	 
			  aaiLogger.info(logline, false, "AAI_6101");
			  throw new AAIException("AAI_6101", emsg); 
		  }
		  
		  //String thisVid = thisLevelVertex.id().toString();
		  //String thisNt = thisLevelVertex.<String>property("aai-node-type").orElse(null);
		  //System.out.println(" DEBUG -- level counter in collectResultSet = " + levelCounter + " thisNodeType = " + thisNt + " this vid = " + thisVid);

      	  if( levelCounter > maxLevels ) {
			  String emsg = "collectResultSet() has looped across more levels than allowed: " + maxLevels + ". ";
			  logline.add("emsg", emsg);         	 
			  aaiLogger.info(logline, false, "AAI_6125");
			  throw new AAIException("AAI_6125", emsg); 
      	  }
      	  
		  ResultSet rs = new ResultSet();
		  rs.vert = thisLevelVertex;
		
		  // Look at the FWD edges for this node	
		  //System.out.println("DEBUG -- about to run the collectResult query at level = " + levelCounter );
		  Iterable <?> verts = thisLevelVertex.query().direction(fwdSearchDirection).has(edgeTag,true).vertices();
		  Iterator <?> vertI = verts.iterator();
		  
		  //int levelNodeCount = 0;
		  ArrayList <TitanVertex> nodeList = new <TitanVertex> ArrayList ();
		  while( vertI != null && vertI.hasNext() ){
			  TitanVertex tmpVert = (TitanVertex) vertI.next();
			  String nodeType = tmpVert.<String>property("aai-node-type").orElse(null);
			  if( needToDoPruning ){
				  if( nodeType.equals(pruneNodeType) && 
						  !tmpVert.id().toString().equals(pruneKeepId) ){
					  // This node is the type we're pruning and it is not the ONE that we want to keep - so
					  // do not put it into the resultSet
				  }
				  else {
					  // Don't need to prune this one 
					  if( ! trimList.contains(nodeType) ){
						  // They're not trimming or pruning this kind of thing - so keep it.
						  nodeList.add(tmpVert);
					  }
				  }
			  }
			  else {
				  if( ! trimList.contains(nodeType) ){
					  // They're not trimming or pruning this kind of thing - so keep it.
					  nodeList.add(tmpVert);
				  }
			  }
		  }
		  
		  // Look at the reverse-tagged edges that are in the rev-direction for this node
		  String reverseEdgeTag = edgeTag + "-REV";
		  Iterable <?> vertsRev = thisLevelVertex.query().direction(revSearchDirection).has(reverseEdgeTag,true).vertices();
		  Iterator <?> vertIRev = vertsRev.iterator();
		  while( vertIRev != null && vertIRev.hasNext() ){
        	  TitanVertex tmpVert = (TitanVertex) vertIRev.next();
        	  String nodeType = tmpVert.<String>property("aai-node-type").orElse(null);
        	  if( needToDoPruning ){
				  if( nodeType.equals(pruneNodeType) && 
						  !tmpVert.id().toString().equals(pruneKeepId) ){
					  // This node is the type we're pruning and it is not the ONE that we want to keep - so
					  // do not put it into the resultSet
				  }
				  else {
					  if( ! trimList.contains(nodeType) ){
						  // They're not trimming or pruning this kind of thing - so keep it.
						  nodeList.add(tmpVert);
					  }
				  }
			  }
			  else {
				  if( ! trimList.contains(nodeType) ){
					  // They're not trimming or pruning this kind of thing - so keep it.
					  nodeList.add(tmpVert);
				  }
			  }
		  }
		  
		  if( nodeList.isEmpty() ){
			  // There were no sub-vertices, so we can return this result set
			  //System.out.println("DEBUG -- no subVert found here - we will return the resultSet with no sub dudes. ");
		  }
		  else {
			  // For each sub-vertex found, need to get it's result set (recursively)
			  Iterator<?> nodeIter = nodeList.iterator();
			  while( nodeIter.hasNext() ){
	        	  TitanVertex tmpVert = (TitanVertex) nodeIter.next();
	          	  ResultSet tmpResSet = collectResultSet( transId, fromAppId, graph, edgeTag, tmpVert, 
	            		  secFilterHash, retNodeType, levelCounter,
	            		  fwdSearchDirection, revSearchDirection, doPruning, 
	        			  pruneNodeType, pruneKeepId, trimList, maxLevels );
	          	  
	          	  rs.subResultSet.add(tmpResSet);
	          	  //System.out.println("DEBUG -- found this guy at level " + levelCounter + ", nodeType = " + tmpVert.property("aai-node-type").orElse(null));
			  }
		  }
		  
		  //System.out.println("DEBUG -- returning from a call to collectResultSet() ");
		  return rs;
		  
	  } // End of collectResultSet()
		
	  
	  
	  
	  
	  
	  /**
  	 * Prints the out result set.
  	 *
  	 * @param resSet the res set
  	 * @param levelCount the level count
  	 */
  	public static void printOutResultSet( ResultSet resSet, int levelCount ) {
            
		  levelCount++;
		  for( int i= 1; i <= levelCount; i++ ){
			  System.out.print("-");
		  }
		  String nt = resSet.vert.<String>property("aai-node-type").orElse(null);
		  
		  Iterator<VertexProperty<Object>> pI = resSet.vert.properties();
		  String propsStr = "";
		  while( pI.hasNext() ){
				VertexProperty<Object> tp = pI.next();
				String pkTop = tp.key();
				/***
				if( ! pkTop.toString().startsWith("aai") 
						&& ! pkTop.toString().equals("source-of-truth")
						&& ! pkTop.toString().equals("resource-version")
						&& ! pkTop.toString().startsWith("last-mod")
						){
				****/
				// For my testing - just want to see these:
				if( pkTop.equals("vserver-id")
						|| pkTop.toString().equals("vnf-name")
						|| pkTop.toString().equals("vnf-id")
						|| pkTop.toString().equals("vserver-name")
						|| pkTop.toString().equals("hostname")
					){
					propsStr = propsStr + " [" + tp.key() + " = " + tp.value() + "]";
				}
		  }
		 
		  System.out.println( levelCount + " " + nt + ", " + propsStr );
		  
		  if( !resSet.subResultSet.isEmpty() ){
			  ListIterator<ResultSet> listItr = resSet.subResultSet.listIterator();
			  while( listItr.hasNext() ){
				  printOutResultSet( listItr.next(), levelCount );
			  }
		  }
		  
	  }// end of printOutResultSet()
	  
	  
	  /**
  	 * Satisfies hash of filters.
  	 *
  	 * @param transId the trans id
  	 * @param fromAppId the from app id
  	 * @param resSet the res set
  	 * @param filterHash the filter hash
  	 * @return true, if successful
  	 * @throws AAIException the AAI exception
  	 */
  	public static boolean satisfiesHashOfFilters( String transId, String fromAppId, 
			  ResultSet resSet, HashMap <String,Object> filterHash )  throws AAIException {
		  
		  LogLine logline = new LogLine();
		  logline.init("aaidbgen", transId, fromAppId, "satisfiesHashOfFilters");
		  
		  if( filterHash.isEmpty() ){
			  // Nothing to match - so we're OK
			  //System.out.println("DEBUG ----- nothing to match for sec. filters - so we're ok ");
			  return true;
		  }
		  
		  Iterator <?> it = filterHash.entrySet().iterator();
		  while( it.hasNext() ){
			  Map.Entry<?,?> filtEntry = (Map.Entry<?,?>) it.next();
			  String propNodeTypeDotName = (filtEntry.getKey()).toString();
			  String fpv = (filtEntry.getValue()).toString();
			  
			  int periodLoc = propNodeTypeDotName.indexOf(".");
			  if( periodLoc <= 0 ){
				  String emsg = "Bad filter param key passed in: [" + propNodeTypeDotName + "].  Expected format = [nodeName.paramName]\n";
				  logline.add("emsg", emsg);         	 
				  aaiLogger.info(logline, false, "AAI_6120");
				  throw new AAIException("AAI_6120", emsg); 
			  }
			  else {
				  String fnt = propNodeTypeDotName.substring(0,periodLoc);
				  String fpn = propNodeTypeDotName.substring(periodLoc + 1);
				
				  if( !filterMetByThisSet( resSet, fnt, fpn, fpv ) ){
					  //System.out.println(" DEBUG -- FAILED to satisfy filter: [" + fnt + "|" + fpn + "|" + fpv + "].");
					  return false;
				  }
			  }
		  }
		  
		  //System.out.println("DEBUG ----- Made it PAST all sec. filters - so we're ok ");
		  // Made it through all the filters -- must be good to go.
		  return true;
		  
	  }// end of satisfiesHashOfFilters()
	  
	  
	  /**
  	 * Filter met by this set.
  	 *
  	 * @param resSet the res set
  	 * @param filtNodeType the filt node type
  	 * @param filtPropName the filt prop name
  	 * @param filtPropVal the filt prop val
  	 * @return true, if successful
  	 */
  	public static boolean filterMetByThisSet( ResultSet resSet, String filtNodeType, String filtPropName, String filtPropVal ) {
          // Note - we are just looking for a positive match for one filter for this resultSet
		  // NOTE: we're expecting the filter to have a format like this: "nodeType.parameterName:parameterValue"
	
		  TitanVertex vert = resSet.vert;
		  if( vert == null ){
			  return false;
		  }
		  else {
			  String nt = resSet.vert.<String>property("aai-node-type").orElse(null);
			  if( nt.equals( filtNodeType ) ){
				  if( filtPropName.equals("vertex-id") ){
					  // vertex-id can't be gotten the same way as other properties
					  String thisVtxId = vert.id().toString();
					  if( thisVtxId.equals(filtPropVal) ){
						  //System.out.println(" DEBUG -- filter [" + filtNodeType + "|" + filtPropName + "|" + filtPropVal + "] has been met.");
						  return true;
					  }
				  }
				  else {
					  Object thisValObj = vert.property(filtPropName).orElse(null);
					  if( thisValObj != null ){
						  String thisVal = thisValObj.toString();
						  if( thisVal.equals(filtPropVal) ){
							  //System.out.println(" DEBUG -- filter [" + filtNodeType + "|" + filtPropName + "|" + filtPropVal + "] has been met.");
							  return true;
						  }
					  }
				  }
			  }
		  }
			  
		  // Didn't find a match at the this level, so check the sets below it meet the criteria
		  if( !resSet.subResultSet.isEmpty() ){
			  ListIterator<ResultSet> listItr = resSet.subResultSet.listIterator();
			  while( listItr.hasNext() ){
				  if( filterMetByThisSet(listItr.next(), filtNodeType, filtPropName, filtPropVal) ){
					  return true;
				  }
			  }
		  }
		  
		  return false;
		  
	  }// end of filterMetByThisSet()
	  
		  
}





