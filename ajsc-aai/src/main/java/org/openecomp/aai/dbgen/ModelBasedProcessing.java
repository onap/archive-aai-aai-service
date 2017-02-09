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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.openecomp.aai.dbmodel.DbEdgeRules;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.ingestModel.DbMaps;
import org.openecomp.aai.ingestModel.IngestModelMoxyOxm;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIConstants;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.thinkaurelius.titan.core.TitanTransaction;
import com.thinkaurelius.titan.core.TitanVertex;

/**
 * Utility class that uses Model/Named-Query definitions to navigate the graph.   
 */
public class ModelBasedProcessing{

	private static AAILogger aaiLogger = new AAILogger(ModelBasedProcessing.class.getName());
	private static int maxLevels = 50;  // max depth allowed for our model - to protect against infinite loop problems
	
	
	
	/**
	 * Gets the start nodes and models.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param passedModelNameVersionId the passed model name version id -- optional (unique id for a model)
	 * @param passedModelId the passed model id -- optional
	 * @param passedModelName the passed model name
	 * @param passedTopNodeType the passed top node type -- optional (needed if neither model-id nor model-name-version-id is passed)
	 * @param startNodeFilterArrayOfHashes the start node filter array of hashes -- optional (used to locate the first node(s) of instance data)
	 * @param apiVer the api ver
	 * @return List of TitanVertex's
	 * @throws AAIException the AAI exception
	 */
	public static HashMap <String,String> getStartNodesAndModels( String transId, String fromAppId, TitanTransaction graph,
			String passedModelNameVersionId, 
			String passedModelId,
			String passedModelName,
			String passedTopNodeType,
			ArrayList <HashMap <String,Object>> startNodeFilterArrayOfHashes, 
			String apiVer ) 
					throws AAIException{
	
		LogLine logline = new LogLine();
		logline.init("aaidbgen", transId, fromAppId, "getStartNodesAndModels");
		
		
		// ----------------------------------------------------------------------------------------------------
		// Get a hash for all start-nodes (key = vtxId, val = modelNameVersionId that applies)
		//     If no start-node-key info is passed, then use either the passed modelNameVersionId or 
		//         the passed model-id or model-name to collect them.
		//     If start-node-key info is given, use it instead to look for start-nodes. 
		//         Note: if ONLY start-node-key info is given, then it would have to map to nodes which 
		//         have persona data.  Otherwise we'd have no way to know what model to collect data with.
		// ----------------------------------------------------------------------------------------------------

		Iterable <?> startVerts = null;
		HashMap <String, String> startVertInfo = new HashMap <String,String> ();
		if( startNodeFilterArrayOfHashes.isEmpty() ){
			// Since they did not give any data to find start instances, we will have to find them
			// using whatever model-info they provided so we can use it to map to persona-data in the db.
			if( (passedModelNameVersionId == null || passedModelNameVersionId.equals(""))
					 && (passedModelId == null || passedModelId.equals(""))
					 && (passedModelName == null || passedModelName.equals(""))){
				String emsg = "ModelId or ModelName or ModelNameVersionId required if no startNodeFilter data passed. \n";
				logline.add("emsg", emsg);         	 
				aaiLogger.info(logline, false, "AAI_6118");
				throw new AAIException("AAI_6118", emsg); 
			}
			else {
				// Use whatever model info they pass to find start-node instances
				// Get the first/top named-query-element used by this query
				if( passedModelNameVersionId != null && !passedModelNameVersionId.equals("") ){
					// Need to look up the model-id and model-version to check against persona data
					TitanVertex modVtx = getModelUsingUUID( transId, fromAppId, graph, passedModelNameVersionId );
					String calcModId = modVtx.<String>property("model-id").orElse(null);
					String calcModVer = modVtx.<String>property("model-version").orElse(null);
					// Now we can look up instances that match this model's info
					startVerts = graph.query().has("persona-model-id",calcModId).has("persona-model-version",calcModVer).vertices();
				}	
				else if( passedModelId != null && !passedModelId.equals("") ){
					// They gave us the model-id
					startVerts = graph.query().has("persona-model-id",passedModelId).vertices();
				}
				else if( passedModelName != null && !passedModelName.equals("") ){
					ArrayList <TitanVertex> modelVtxList = getModelsUsingName(transId, fromAppId, graph, passedModelName);
					ArrayList <TitanVertex> startVtxList = new ArrayList <TitanVertex> ();
					// Need to look up the model-ids and model-versions to check against persona data
					if( !modelVtxList.isEmpty() ){
						for( int i = 0; i < modelVtxList.size(); i++ ){
							String calcModId = (modelVtxList.get(i)).<String>property("model-id").orElse(null);
							String calcModVer = (modelVtxList.get(i)).<String>property("model-version").orElse(null);
							// Now we can look up instances that match this model's info
							Iterable <?> tmpStartVerts = graph.query().has("persona-model-id",calcModId).has("persona-model-version",calcModVer).vertices();
							Iterator <?> tmpStartIter = tmpStartVerts.iterator();
							while( tmpStartIter.hasNext() ){
								TitanVertex tmpStartVert = (TitanVertex) tmpStartIter.next();
								startVtxList.add(tmpStartVert);
							}
						}
					}
					if( !startVtxList.isEmpty() ){
						startVerts = startVtxList;
					}
				}	
			}
			
			if( startVerts != null ){ 
				Iterator <?> startVertsIter = startVerts.iterator();
				while( startVertsIter.hasNext() ){
					TitanVertex tmpStartVert = (TitanVertex) startVertsIter.next();
					String vid = tmpStartVert.id().toString();
					String tmpModId =  tmpStartVert.<String>property("persona-model-id").orElse(null);
					String tmpModVers =  tmpStartVert.<String>property("persona-model-version").orElse(null);
					String calcModNameVersId = getModNameVerId( transId, fromAppId, graph, tmpModId, tmpModVers );
					startVertInfo.put(vid, calcModNameVersId);
				}
			}
			if( startVertInfo.isEmpty() ){
				String emsg = "Start Node(s) could not be found for model data passed.  " +
						"(modelNameVersionId = [" + passedModelNameVersionId + 
						"], modelId = [" + passedModelId +
						"], modelName = [" + passedModelName +
						"])\n";
				logline.add("emsg", emsg);         	 
				aaiLogger.info(logline, false, "AAI_6118");
				throw new AAIException("AAI_6118", emsg); 
			}
		}
		else {
			// Use start-node filter info to find start-node(s) - Note - there could also be model info passed that we'll need
			//     to use to trim down the set of start-nodes that we find based on the startNodeFilter data.
			String modTopNodeType ="";
			String modInfoStr = "";
			if( passedModelNameVersionId != null && !passedModelNameVersionId.equals("") ){
				modTopNodeType = getModelTopWidgetType( transId, fromAppId, graph, passedModelNameVersionId, "", "" );
				modInfoStr = "modelNameVersionId = (" + passedModelNameVersionId + ")"; 
			}
			else if( passedModelId != null && !passedModelId.equals("") ){
				modTopNodeType = getModelTopWidgetType( transId, fromAppId, graph,"", passedModelId, "" );
				modInfoStr = "modelId = (" + passedModelId + ")"; 
			}
			else if( passedModelName != null && !passedModelName.equals("") ){
				modTopNodeType = getModelTopWidgetType( transId, fromAppId, graph,"", "", passedModelName );
				modInfoStr = "modelName = (" + passedModelName + ")"; 
			}
			
			if( modTopNodeType.equals("") ){
				if( (passedTopNodeType == null) || passedTopNodeType.equals("") ){
					String msg = "Could not determine the top-node nodeType for this request. modelInfo: [" + modInfoStr + "]";
					throw new AAIException("AAI_6118", msg);
				}
				else {
					// We couldn't find a top-model-type based on passed in model info, but they
					// gave us a type to use -- so use it.
					modTopNodeType = passedTopNodeType;
				}
			}
			else {
				// we did get a topNode type based on model info - make sure it doesn't contradict 
				// the passsed-in one (if there is one)
				if( passedTopNodeType != null && !passedTopNodeType.equals("") 
						&& !passedTopNodeType.equals(modTopNodeType) ){
					String emsg = "topNodeType passed in [" + passedTopNodeType 
							+ "] does not match nodeType derived for model info passed in: ["
							+ modTopNodeType + "]\n";
					logline.add("emsg", emsg);         	 
					aaiLogger.info(logline, false, "AAI_6120");
					throw new AAIException("AAI_6120", emsg); 
				}
			}
				
			ArrayList <String> modelNameVersionIds2Check = new ArrayList <String> ();
			if( (passedModelName != null && !passedModelName.equals("")) ){
				// They passed a modelName, so find all the model UUIDs (model-name-version-id's) that map to this
				modelNameVersionIds2Check = getModelUuidsUsingName(transId, fromAppId, graph, passedModelName);
			}
			if( (passedModelNameVersionId != null && !passedModelNameVersionId.equals("")) ){
				// They passed in a modelNameVersionId
				if( modelNameVersionIds2Check.isEmpty() ){
					// There was no modelName passed, so we can use the passed modelNameVersionId
					modelNameVersionIds2Check.add(passedModelNameVersionId);
				}
				else if( modelNameVersionIds2Check.contains(passedModelNameVersionId) ){
					// The passed in uuid does not conflict with what we got using the passed-in modelName.
					// We'll just use the passed in uuid in this case.
					// Hopefully they would not be passing strange combinations like this, but we'll try to deal with it.
					modelNameVersionIds2Check = new ArrayList <String> ();  // Clear out what we had
					modelNameVersionIds2Check.add(passedModelNameVersionId);
				}
			}
			
			// We should now be OK with our topNodeType for this request, so we can look for the actual startNodes
			for( int i=0; i < startNodeFilterArrayOfHashes.size(); i++ ){
				// Locate the starting node which will be used to look which corresponds to this set of filter data
				TitanVertex startVtx = null;
				try {
					startVtx = DbMeth.getUniqueNodeWithDepParams( transId, fromAppId, graph, 
						modTopNodeType, startNodeFilterArrayOfHashes.get(i), apiVer );
				}
				catch( AAIException e ){
					String msg = "Could not find startNode of type = [" + modTopNodeType +  "], given these params: "  
							+ startNodeFilterArrayOfHashes.get(i) + ". msg # from getUniqueNode() = " + e.getMessage();
					throw new AAIException("AAI_6114", msg);
				}
			
				String vid = startVtx.id().toString();
				String personaModVersion = startVtx.<String>property("persona-model-version").orElse(null);
				String personaModId = startVtx.<String>property("persona-model-id").orElse(null);	
						
				// Either this start-node has persona info (which should not contradict any passed-in model info)
				//    or they should have passed in the model to use - so we'd just use that.
				if( personaModVersion != null && !personaModVersion.equals("") ){
					// There is persona data in this start-node.  So make sure it doesn't contradict any "passed" stuff
					// Find out what modelNameVersionId that maps to
					String personaModelNameVerId = getModNameVerId(transId, fromAppId, graph, personaModId, personaModVersion);
					
					if( modelNameVersionIds2Check.isEmpty()  
							&& (passedModelId == null || passedModelId.equals("")) ){
						// They didn't pass any model info, so use the persona one.
						startVertInfo.put(vid, personaModelNameVerId);
					}
					else if( modelNameVersionIds2Check.isEmpty() 
							&& (passedModelId != null && !passedModelId.equals("")) ){
						// They passed in just the modelId - so check it
						if( passedModelId.equals(personaModId) ){
							startVertInfo.put(vid, personaModelNameVerId);
						}
					}
					else if( !modelNameVersionIds2Check.isEmpty() 
							&& (passedModelId == null || passedModelId.equals("")) ){
						// They passed in  modelNameVersionId - so check
						if( modelNameVersionIds2Check.contains(personaModelNameVerId) ){
							startVertInfo.put(vid, personaModelNameVerId);
						}
					}	
					else if( !modelNameVersionIds2Check.isEmpty() 
							&& (passedModelId != null && !passedModelId.equals("")) ){
						// We have BOTH a modelNameVersionIds and a modelId to check 
						if( passedModelId.equals(personaModId) 
								&& modelNameVersionIds2Check.contains(personaModelNameVerId) ){
							startVertInfo.put(vid, personaModelNameVerId);
						}
					}
				}
				else {
					// This start node did not have persona info -- so we will use the passed in model info if they passed one
					if( passedModelNameVersionId.equals("") ){
						String emsg = "Found startNode but no model info passed in and no persona model info in the start node.";
						logline.add("emsg", emsg);         	 
						aaiLogger.info(logline, false, "AAI_6120");
						throw new AAIException("AAI_6120", emsg); 
					}
					else {
						startVertInfo.put(vid, passedModelNameVersionId);
					}
				}
			}
		}
		
		return startVertInfo;
		
	}//end of  getStartNodesAndModels()
		

	/**
	 * Query by model.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param modelNameVersionId the model name version id -- optional - (unique id for a model)
	 * @param modelId the model id -- optional
	 * @param modelName the model name
	 * @param topNodeType - optional (needed if neither model-id nor model-name-version-id is passed)
	 * @param startNodeFilterArrayOfHashes the start node filter array of hashes -- optional (used to locate the first node(s) of instance data)
	 * @param apiVer the api ver
	 * @return resultSet
	 * @throws AAIException the AAI exception
	 */
	public static ArrayList <ResultSet> queryByModel( String transId, String fromAppId, TitanTransaction graph,
			String modelNameVersionId, 
			String modelId,
			String modelName,
			String topNodeType,
			ArrayList <HashMap <String,Object>> startNodeFilterArrayOfHashes, 
			String apiVer ) 
					throws AAIException{
	
		final String transId_f = transId;
		final String fromAppId_f = fromAppId;
		final TitanTransaction graph_f = graph;
		final String modelNameVersionId_f = modelNameVersionId;
		final String modelId_f = modelId;
		final String modelName_f = modelName;
		final String topNodeType_f = topNodeType;
		final ArrayList <HashMap <String,Object>> startNodeFilterArrayOfHashes_f = startNodeFilterArrayOfHashes; 
		final String apiVer_f = apiVer; 
		
		// Find out what our time-limit should be
		int timeLimitSec = 0;
		String timeLimitString = AAIConfig.get("aai.model.query.timeout.sec");
		if( timeLimitString != null && !timeLimitString.equals("") ){
			try {
				timeLimitSec = Integer.parseInt(timeLimitString);
			}
			catch ( Exception nfe ){
				// Don't worry, we will leave the limit as zero - which tells us not to use it.
			}
		}
	
		if( timeLimitSec <= 0 ){
			// We will NOT be using a timer
			return queryByModel_Timed( transId, fromAppId, graph,
		  			modelNameVersionId, 
		  			modelId,
		  			modelName,
		  			topNodeType,
		  			startNodeFilterArrayOfHashes, 
		  			apiVer );
		}
		
		ArrayList <ResultSet> resultList = new ArrayList <ResultSet> ();
		TimeLimiter limiter = new SimpleTimeLimiter();
		try {
			resultList = limiter.callWithTimeout(new Callable <ArrayList <ResultSet>>() {
			    public ArrayList <ResultSet> call() throws AAIException {
			      return queryByModel_Timed( transId_f, fromAppId_f, graph_f,
			  			modelNameVersionId_f, 
			  			modelId_f,
			  			modelName_f,
			  			topNodeType_f,
			  			startNodeFilterArrayOfHashes_f, 
			  			apiVer_f );
			    }
			  }, timeLimitSec, TimeUnit.SECONDS, true);
		} 
		catch (AAIException ae) {
			// Re-throw AAIException so we get can tell what happened internally
			throw ae;
		}
		catch (UncheckedTimeoutException ute) {
			throw new AAIException("AAI_6140", "Query Processing Limit exceeded. (limit = " + timeLimitSec + " seconds)");
		}
		catch (Exception e) {
			throw new AAIException("AAI_6128", "Unexpected exception in queryByModel(): " + e.getMessage() );
		}


		return resultList;
	}
	
		
	/**
	 * Query by model timed.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param modelNameVersionId the model name version id
	 * @param modelId the model id
	 * @param modelName the model name
	 * @param topNodeType the top node type
	 * @param startNodeFilterArrayOfHashes the start node filter array of hashes
	 * @param apiVer the api ver
	 * @return the array list
	 * @throws AAIException the AAI exception
	 */
	public static ArrayList <ResultSet> queryByModel_Timed( String transId, String fromAppId, TitanTransaction graph,
			String modelNameVersionId, 
			String modelId,
			String modelName,
			String topNodeType,
			ArrayList <HashMap <String,Object>> startNodeFilterArrayOfHashes, 
			String apiVer ) 
					throws AAIException{
	
		DbMaps dbMaps = IngestModelMoxyOxm.dbMapsContainer.get(AAIConfig.get(AAIConstants.AAI_DEFAULT_API_VERSION_PROP));
				
		
		
		ArrayList <ResultSet> resultArray = new ArrayList <ResultSet> ();
		LogLine logline = new LogLine();
		logline.init("aaidbgen", transId, fromAppId, "queryByModel");
		
		// NOTE: this method can be used for different styles of queries:
		//   a) They could pass neither a modelNameVersionId or a modelId but just a set of data defining start-nodes.
		//      Note - with no model info, we need them to pass the startNodeType for us to be able to use the
		//      start-node-filter data.  We would look at each start node and ensure that each has persona-model info.  
		//      Use whatever model corresponds to each instance to pull that instance's data.
		//   b) They could pass a modelId, but no modelNameVersionId and no startNode info.   In this case, we
		//      Would look in the database for all nodes that have a persona-model-id that matches what was 
		//      passed, and then for each of those instances, pull the data based on the corresponding model.
		//   c) They could pass a model-name-version-id, but no startNode info. We'd make sure that if a 
		//      model-id was also passed, that it does not conflict - but it really should be null if they
		//      are passing a full model-name-version-id.   Like case -b-, we'd do a query for all nodes
		//      that have persona info that corresponds to the model-name-veersion-id passed and then 
		//      collect data for each one.
		//   d) They could pass either modelNameVersionId or modelId AND startNodeFilter info.  In this case we
		//      would look at the model info to figure out what the top-node-type is, then look at the 
		//      top-node instances based on the startNodeFilter.   We'd only collect data for each instance if
		//      it's persona model info matches what was passed in.
		
		
		// ----------------------------------------------------------------------------------------------------------
		// Get a Hash of all the start-nodes (top node for a model where we will start collecting data)
		//      for startNode2ModelHash:  key = vertex-id for the startNode, value = modelNameVersionType for model
		// ----------------------------------------------------------------------------------------------------------
		HashMap <String, String> startNode2ModelHash = getStartNodesAndModels( transId, fromAppId, graph,
				modelNameVersionId, modelId, modelName, topNodeType,
				startNodeFilterArrayOfHashes, apiVer );	
		
		//System.out.println("\nDEBUG -- Here's a dump of the startnodes/models: " + startNode2ModelHash.toString()); 
		
		
		// --------------------------------------------------------------------------------------------------------
		// Figure out what-all models we're gonna be dealing with 
		// Note - Instances must all use the same type of start-node, but do not have to all use the same model.
		// --------------------------------------------------------------------------------------------------------
		HashMap <String, TitanVertex> distinctModelsHash = new HashMap <String,TitanVertex> (); 
			// For distinctModelsHash:  key = modelTypeVersionId, val= modelVertex
		String startNodeType = "";
		if( topNodeType != null && !topNodeType.equals("") ){
			startNodeType = topNodeType;
		}

		Set <String> snKeySet = startNode2ModelHash.keySet();
		Iterator<String> startNodeIterator = snKeySet.iterator();
		while( startNodeIterator.hasNext() ){
			String vtxKey = (String) startNodeIterator.next();  
			String modKey = startNode2ModelHash.get(vtxKey);
			if( !distinctModelsHash.containsKey(modKey) ){
				// First time seeing this model
				TitanVertex modVtx = getModelUsingUUID(transId, fromAppId, graph, modKey);
				String tmpNodeType = getModelWidgetType( modVtx, "" );
				if( startNodeType.equals("") ){
					startNodeType = tmpNodeType;
				}
				else if( !startNodeType.equals(tmpNodeType) ){
					String msg = "Conflict between startNode types for models involved: [" + startNodeType
							+ "], [" + tmpNodeType + "]";
					throw new AAIException("AAI_6125", msg);
				}
				distinctModelsHash.put(modKey, modVtx);
			}
		}
		
		//System.out.println("\nDEBUG -- Here's a dump of the DISTINCT models hash: " + distinctModelsHash.toString() );
	
		// ---------------------------------------------------------------------------------------------------------------
		// Get the "valid-next-step" hash for each distinct model
		// While we're at it, get a mapping of model-id|model-version to model-name-version-id for the models being used
		// ---------------------------------------------------------------------------------------------------------------
		HashMap <String, Multimap<String, String>> validNextStepHash = new HashMap <String, Multimap<String, String>>(); 
			// validNextStepHash:   key = modelNameVerId, value = nextStepMap
		Set <String> keySet = distinctModelsHash.keySet();
		Iterator<String> modelIterator = keySet.iterator();
		while( modelIterator.hasNext() ){
			String modKey = (String) modelIterator.next();
			TitanVertex modelVtx = (TitanVertex)distinctModelsHash.get(modKey);
			Multimap<String, String> tmpTopoMap = genTopoMap4Model( transId, fromAppId, graph,
						modelVtx, modKey, dbMaps );
			validNextStepHash.put(modKey, tmpTopoMap);
		} 
		
		//System.out.println("\n\nDEBUG -- Here's a dump of the  validNextStepHash "+ validNextStepHash.toString() );
		
		// -------------------------------------------------------------------------------------------------
		// Figure out what the "start-node" for each instance will be (plus the info we will use to 
		//       represent that in our topology)
		// -------------------------------------------------------------------------------------------------
		ArrayList <String> failedPersonaCheckVids = new ArrayList <String> ();
		HashMap <String, String> firstStepInfoHash = new HashMap <String,String> (); 
			// For firstStepInfoHash:   key = startNodeVtxId, val=topNodeType plus personaData if applicable
			//                            ie. the value is what we'd use as the "first-step" for this model.
		if( !nodeTypeSupportsPersona( startNodeType, dbMaps) ){
			// This node type doesn't have persona info, so we just use startNodeType for the first-step-info 
			snKeySet = startNode2ModelHash.keySet();
			startNodeIterator = snKeySet.iterator();
			while( startNodeIterator.hasNext() ){
				String vtxKey = (String) startNodeIterator.next();
				firstStepInfoHash.put(vtxKey,startNodeType);
			}
		}
		else { 
			// Need to check that this node's persona data is good and if it is - use it for the first step info
			snKeySet = startNode2ModelHash.keySet();
			startNodeIterator = snKeySet.iterator();
			while( startNodeIterator.hasNext() ){
				String vtxKey = (String) startNodeIterator.next();
				Iterator<Vertex> vtxIterator = graph.vertices(vtxKey);
				TitanVertex tmpVtx = (TitanVertex)vtxIterator.next();
				String thisVtxModelUUID = startNode2ModelHash.get(vtxKey);
				TitanVertex modelVtx = (TitanVertex)distinctModelsHash.get(thisVtxModelUUID);
				String modId = modelVtx.<String>property("model-id").orElse(null);
				String modVersion = modelVtx.<String>property("model-version").orElse(null);
				String personaModId = tmpVtx.<String>property("persona-model-id").orElse(null);
				String personaModVersion = tmpVtx.<String>property("persona-model-version").orElse(null);
				
				if( modId.equals(personaModId) && modVersion.equals(personaModVersion) ){
					String tmpPersonaInfoStr = startNodeType + "," + personaModId + "," + personaModVersion;
					firstStepInfoHash.put(vtxKey, tmpPersonaInfoStr );
				}
				else { 
					// we won't use this start node below when we collect data because it should have
					// had persona data that matched it's model - but it did not.
					failedPersonaCheckVids.add(vtxKey);
				}
			}	
		}	

		//System.out.println("\nDEBUG -- Here's a dump of the firstStepInfoHash hash: " + firstStepInfoHash.toString() );
		
		// ------------------------------------------------------------------------------------------------
		// Loop through each start-node, collect it's data using collectInstanceData() and put the 
		//      resultSet onto the resultArray.
		// ------------------------------------------------------------------------------------------------
		
		// Make sure they're not bringing back too much data
		String maxString = AAIConfig.get("aai.model.query.resultset.maxcount");
		if( maxString != null &&  !maxString.equals("") ){
			int maxSets = 0;
			try {
				maxSets = Integer.parseInt(maxString);
			}
			catch ( Exception nfe ){
				// Don't worry, we will leave the max as zero - which tells us not to use it.
			}
			
			if( maxSets > 0 && (startNode2ModelHash.size() > maxSets) ){
				String msg = " Query returns " + startNode2ModelHash.size() + " resultSets.  Max allowed is: " + maxSets;
				throw new AAIException("AAI_6141", msg);
			}
		}
		
		snKeySet = startNode2ModelHash.keySet();
		startNodeIterator = snKeySet.iterator();
		while( startNodeIterator.hasNext() ){
			String topNodeVtxId  = (String) startNodeIterator.next();
			if( failedPersonaCheckVids.contains(topNodeVtxId) ){
				// Skip this vertex because it failed it's persona-data check above
				continue;
			}
			
			Iterator<Vertex> vtxIterator = graph.vertices(topNodeVtxId);
			TitanVertex tmpStartVtx = (TitanVertex)vtxIterator.next();
			String elementLocationTrail = firstStepInfoHash.get(topNodeVtxId); 
			String modelTypeNameVerId = startNode2ModelHash.get(topNodeVtxId);
			Multimap<String, String> validNextStepMap = validNextStepHash.get(modelTypeNameVerId);
			
			ArrayList <String> vidsTraversed = new ArrayList <String> ();
			HashMap <String,String> emptyDelKeyHash = new HashMap <String,String> ();
			HashMap <String,String> emptyNQElementHash = new HashMap <String,String> ();  // Only applies to Named Queries
			ResultSet tmpResSet = collectInstanceData( transId, fromAppId, graph, 
					tmpStartVtx, elementLocationTrail, 
					validNextStepMap, vidsTraversed, 0, emptyDelKeyHash, emptyNQElementHash, apiVer );
			
			resultArray.add(tmpResSet);
		}
		
		return resultArray;
		
	}// queryByModel()
	
			
	
	/**
	 * Run delete by model.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param modelNameVersionId the model name version id -- unique id for a model
	 * @param topNodeTypeVal the top node type val -- required if no model-name-version-id is passed
	 * @param startNodeFilterHash the start node filter hash -- used to locate the first node of instance data
	 * @param apiVer the api ver
	 * @param resVersion the res version -- resourceVersion of the top/first widget in the model instance
	 * @return HashMap (keys = vertexIds that were deleted)
	 * @throws AAIException the AAI exception
	 */
	public static HashMap <String,String> runDeleteByModel( String transId, String fromAppId, TitanTransaction graph,
			String modelNameVersionId, String topNodeTypeVal, HashMap <String,Object> startNodeFilterHash, String apiVer, String resVersion ) 
					throws AAIException{
		
		HashMap <String,String> retHash = new HashMap <String,String> (); 
		LogLine logline = new LogLine();
		logline.init("aaidbgen", transId, fromAppId, "runDeleteByModel");
  
		if( graph == null ){
			String emsg = "null graph object passed to runDeleteByModel()\n";
			logline.add("emsg", emsg);         	 
			aaiLogger.info(logline, false, "AAI_6101");
			throw new AAIException("AAI_6101", emsg); 
		}
		
		DbMaps dbMaps = IngestModelMoxyOxm.dbMapsContainer.get(AAIConfig.get(AAIConstants.AAI_DEFAULT_API_VERSION_PROP));
		
		// Locate the Model to be used 
		TitanVertex modelVtx = null;
		
		if( modelNameVersionId != null && !modelNameVersionId.equals("") ){
			HashMap <String,Object> propHash0 = new HashMap<String, Object>();
			propHash0.put("model-name-version-id", modelNameVersionId);
			modelVtx = DbMeth.getUniqueNode( transId, fromAppId, graph, "model", propHash0, null, apiVer );
			if( modelVtx == null ){
				String msg = "No model found for model-name-version-id = [" + modelNameVersionId + "]";
				logline.add("msg", msg);
				aaiLogger.info(logline, false, "AAI_6114");
				throw new AAIException("AAI_6114", msg);
			}
		}
		else {
			// if they didn't pass the modelNameVersionId, then we need to use the startNode to figure it out
			// Locate the starting node based on the start node params
			if( topNodeTypeVal == null || topNodeTypeVal.equals("") ){
				String msg = "If no model info is passed, then topNodeType is required. ";
				logline.add("msg", msg);
				aaiLogger.info(logline, false, "AAI_6118");
				throw new AAIException("AAI_6118", msg);
			}
			TitanVertex startVtx = DbMeth.getUniqueNodeWithDepParams( transId, fromAppId, graph, 
					topNodeTypeVal, startNodeFilterHash, apiVer );
			String startVertPersonaModId = startVtx.<String>property("persona-model-id").orElse(null);
			String startVertPersonaModVersion = startVtx.<String>property("persona-model-version").orElse(null);
			modelVtx = getModelUsingPersonaInfo( transId, fromAppId, graph,
					startVertPersonaModId, startVertPersonaModVersion );
		}
		
		if( modelVtx == null ){
			String msg = "Could not determine the model for the given input parameters. ";
			logline.add("msg", msg);
			aaiLogger.info(logline, false, "AAI_6114");
			throw new AAIException("AAI_6114", msg);
		}

		String topNType = "unknown";
		String modelType = getModelType( modelVtx, "" );
		if( modelType.equals("widget") ){
			// If they want to delete using a widget-level model..  That is just a delete of the one 
			//  	instance of one of our nodes.  
			String widgModNodeType = modelVtx.<String>property("model-name").orElse(null);
			if( (widgModNodeType == null) || widgModNodeType.equals("") ){
				String msg = "Could not find model-name for the widget model  [" + modelNameVersionId + "].";
				throw new AAIException("AAI_6132", msg);
			}
			TitanVertex widgetVtx = DbMeth.getUniqueNode( transId, fromAppId, graph, widgModNodeType, startNodeFilterHash, null, apiVer );
			String widgId = widgetVtx.id().toString();
			DbMeth.removeAaiNode( transId, fromAppId, graph, widgetVtx, "USE_DEFAULT", "v7", resVersion);
			retHash.put(widgId, widgModNodeType);
			return( retHash );
		}
		
		// ----------------------------------------------------------------------------
		// If we got to here, this must be either a service or resource model.
		// So, we'll need to get a Hash of which parts of the model to delete.
		// ----------------------------------------------------------------------------
		String chkFirstNodePersonaModelId = "";
		String chkFirstNodePersonaModelVersion = "";
		String personaData = "";
		TitanVertex firstModElementVertex = getTopElementForSvcOrResModel( modelVtx );
		topNType = getElementWidgetType( firstModElementVertex, "" );
		if( (topNType == null) || topNType.equals("") ){
			String msg = "Could not determine the top-node nodeType for model: [" + modelNameVersionId + "]";
			throw new AAIException("AAI_6132", msg);
		}
		if( nodeTypeSupportsPersona(topNType, dbMaps) ){
			chkFirstNodePersonaModelId = modelVtx.<String>property("model-id").orElse(null);
			chkFirstNodePersonaModelVersion = modelVtx.<String>property("model-version").orElse(null);
			personaData = "," + chkFirstNodePersonaModelId + "," + chkFirstNodePersonaModelVersion;
		}
		
		// Get the deleteKeyHash for this model
		String incomingTrail = "";
		HashMap <String, String> currentHash = new HashMap <String,String> ();
		HashMap <String, TitanVertex> modConHash = new HashMap <String,TitanVertex> ();
		ArrayList <String>  vidsTraversed = new ArrayList <String> ();
		HashMap <String, String> delKeyHash = collectDeleteKeyHash( transId, fromAppId, graph,
				  firstModElementVertex, incomingTrail, currentHash, vidsTraversed, 
				  0, dbMaps, modConHash, 
				  chkFirstNodePersonaModelId, chkFirstNodePersonaModelVersion ); 
	
		
		//System.out.println("\n ----DEBUG -----:  Delete Hash for model: [" + modelNameVersionId + "] looks like: ");
		//for( Map.Entry<String, String> entry : delKeyHash.entrySet() ){
		//	System.out.println("key = [" + entry.getKey() + "], val = [" + entry.getValue() + "]");
		//}
		//System.out.println("\n -----");
		
		
		// Locate the starting node that we'll use to start looking for instance data
		TitanVertex startVtx = DbMeth.getUniqueNodeWithDepParams( transId, fromAppId, graph, 
				topNType, startNodeFilterHash, apiVer );
		
		if( !chkFirstNodePersonaModelId.equals("") ){
			// NOTE:  For Service or Resource models, if this is a nodeType that supports persona's, then
			// 		we need to make sure that the start node matches the persona values.
			String startVertPersonaModId = startVtx.<String>property("persona-model-id").orElse(null);
			String startVertPersonaModVersion = startVtx.<String>property("persona-model-version").orElse(null);
			if( !chkFirstNodePersonaModelId.equals(startVertPersonaModId) 
					|| !chkFirstNodePersonaModelVersion.equals(startVertPersonaModVersion) ){
				String msg = "Persona-Model data mismatch for start node (" + topNType +  "), " +
						startNodeFilterHash ;
				throw new AAIException("AAI_6114", msg);
			}
		}
		String topVid = startVtx.id().toString();
	
		// Read the model into a Map for processing
		Multimap <String, String> validNextStepMap = genTopoMap4Model(transId, fromAppId, graph, 
				modelVtx, modelNameVersionId, dbMaps );
		
		logline.add("TopoMap", validNextStepMap.toString() ); 
		
		// Collect the data
		String elementLocationTrail = topNType + personaData;
		vidsTraversed = new ArrayList <String> ();
		HashMap <String,String> emptyHash = new HashMap <String,String> ();  
		
		// Pass emptyHash for the NQElement hash since that parameter only applies to Named Queries
		ResultSet retResSet = collectInstanceData( transId, fromAppId, graph, 
				startVtx, elementLocationTrail, 
				validNextStepMap, vidsTraversed, 0, delKeyHash, emptyHash, apiVer );
		
		// Note: the new ResultSet will have each element tagged with the del flag so we'll know if it
		// 		should be deleted or not - so loop through the results in a try-block since some things 
		//		will get auto-deleted by parents before we get to them --- and try to remove each one.
		String vidToResCheck = topVid;
		retHash = deleteAsNeededFromResultSet( transId, fromAppId, graph, retResSet, 
				vidToResCheck, apiVer, resVersion, emptyHash );
		String msgStr = "processed deletes for these vids: (\n"+ retHash.keySet().toString() + ").";
		
		logline.add("DeleteReturnVal", msgStr);
		return retHash;
		  
	}// End of runDeleteByModel()
				
				
	/**
	 * Delete as needed from result set.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param resSet the res set
	 * @param vidToResCheck -- this vertex will need to have its resource-version checked
	 * @param apiVer the api ver
	 * @param resVersion the res version
	 * @param hashSoFar the hash so far -- hash of what's been deleted so far
	 * @return String
	 * @throws AAIException the AAI exception
	 */
	public static HashMap <String,String> deleteAsNeededFromResultSet( String transId, String fromAppId, TitanTransaction graph,
			ResultSet resSet, String vidToResCheck, String apiVer, String resVersion, HashMap <String,String> hashSoFar ) 
					throws AAIException
	{
		LogLine logline = new LogLine();
		logline.init("aaidbgen", transId, fromAppId, "deleteAsNeededFromResultSet");
		HashMap <String,String> retHash = new HashMap <String,String> ();
		retHash.putAll( hashSoFar );
		Boolean deleteIt = false;
		
		if( graph == null ){
			String emsg = "null graph object passed to deleteAsNeededFromResultSet()\n";
			logline.add("emsg", emsg);         	 
			aaiLogger.info(logline, false, "AAI_6101");
			throw new AAIException("AAI_6101", emsg); 
		}
		  	
		if( resSet.vert == null ){
			return retHash;
		}
		
		TitanVertex thisVtx = resSet.vert;
		String thisGuyId = "";
		String thisNT = "";
		String thisGuyStr = "";
		
		try {
			thisGuyId = thisVtx.id().toString();
			thisNT = thisVtx.<String>property("aai-node-type").orElse(null);
			thisGuyStr = thisGuyId + "[" + thisNT + " found at:" + resSet.locationInModelSubGraph + "]";
		}
		catch (Exception ex) {
			// Sometimes things have already been deleted by the time we get to them - just log it.
			String warnMsg = "WARNING Exception when deleting " + thisGuyStr + ".  msg = " + ex.getMessage();
			logline.add("warnMsg", warnMsg);
		}
		
		if( thisGuyId.equals("") ){
			// The vertex must have already been removed.   Just return.
			return retHash;
		}
		else {
			if( resSet.getNewDataDelFlag() != null && resSet.getNewDataDelFlag().equals("T") ){
				String infoMsg = ">>  will try to delete this one >> " + thisGuyStr ;
				logline.add("infoMsg", infoMsg);		
				
				try {
					Boolean resVerOverRide = true;
					if( thisGuyId.equals(vidToResCheck) ){
						// This is the one vertex that we want to check the resourceId before deleting
						resVerOverRide = false;
					}
					DbMeth.removeAaiNode( transId, fromAppId, graph, thisVtx, "USE_DEFAULT", apiVer, resVersion, resVerOverRide );
				}
				catch (AAIException ae) {
					String errorCode = ae.getErrorObject().getErrorCode();
					if (  errorCode.equals("6130") ||  errorCode.equals("6131") ) {
						// They didn't pass the correct resource-version for the top node.
						throw ae;
					}
					else {
						String errText = ae.getErrorObject().getErrorText();
						String errDetail = ae.getErrorObject().getDetails();
						String warnMsg = "WARNING Exception when deleting " + thisGuyStr + ".  ErrorCode = " + errorCode + 
								", errorText = " + errText + ", details = " + errDetail;
						logline.add("warnMsg", warnMsg);
					}
				}
				catch( Exception e ){
					// We'd expect to get a "node not found" here sometimes depending on the order that 
					// the model has us finding / deleting nodes.
					// Ignore the exception - but log it so we can see what happened.
					String warnMsg = "WARNING Exception when deleting " + thisGuyStr + e.getMessage();
					//System.out.println(" \nDEBUG --- " + warnMsg );
					logline.add("warnMsg", warnMsg);
				}
				
				// We can't depend on a thrown exception to tell us if a node was deleted since it may
				// have been auto=deleted before this removeAaiNode() call.  
				// --- Not sure if we would want to check anything here -- because the graph.commit() is done outside of this call.
				
				deleteIt = true;
			}
			else {
				// --- DEBUG ---- 
				//System.out.println(">>>>>>> NOT DELETING THIS ONE >>>> " + thisGuyStr );
				//ArrayList <String> retArr = DbMeth.showPropertiesForNode(transId, fromAppId, thisVtx);
				//for( String info : retArr ){ System.out.println(info); }
				// --- DEBUG ----
			}
		}
		
		// Now call this routine for the sub-resultSets
		List <ResultSet> subResultSetList = resSet.getSubResultSet(); 
		Iterator <ResultSet> subResSetIter = subResultSetList.iterator();
		while( subResSetIter.hasNext() ){
			ResultSet tmpSubResSet = subResSetIter.next();
			retHash = deleteAsNeededFromResultSet( transId, fromAppId, graph, tmpSubResSet, 
					vidToResCheck, apiVer, resVersion, retHash );
		}
		
		if( deleteIt ){
			retHash.put(thisGuyId, thisGuyStr);
		}
		
		aaiLogger.info(logline, true, "0");
		return retHash;
				
	}// deleteAsNeededFromResultSet()
		

	/**
	 * Query by named query.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param namedQueryUuid the named query uuid
	 * @param startNodeFilterArrayOfHashes the start node filter array of hashes --used to locate the first nodes of instance data
	 * @param apiVer the api ver
	 * @return resultSet
	 * @throws AAIException the AAI exception
	 */
	public static ArrayList <ResultSet> queryByNamedQuery( String transId, String fromAppId, TitanTransaction graph,
			String namedQueryUuid,  
			ArrayList <HashMap <String,Object>> startNodeFilterArrayOfHashes, 
			String apiVer ) 
					throws AAIException{
	
		final String transId_f = transId;
		final String fromAppId_f = fromAppId;
		final TitanTransaction graph_f = graph;
		final String namedQueryUuid_f = namedQueryUuid;
		final ArrayList <HashMap <String,Object>> startNodeFilterArrayOfHashes_f = startNodeFilterArrayOfHashes; 
		final String apiVer_f = apiVer; 
		
		// Find out what our time-limit should be
		int timeLimitSec = 0;
		String timeLimitString = AAIConfig.get("aai.model.query.timeout.sec");
		if( timeLimitString != null && !timeLimitString.equals("") ){
			try {
				timeLimitSec = Integer.parseInt(timeLimitString);
			}
			catch ( Exception nfe ){
				// Don't worry, we will leave the limit as zero - which tells us not to use it.
			}
		}
	
		if( timeLimitSec <= 0 ){
			// We will NOT be using a timer
			return queryByNamedQuery_Timed( transId, fromAppId, graph,
					namedQueryUuid,
		  			startNodeFilterArrayOfHashes, 
		  			apiVer );
		}
		
		ArrayList <ResultSet> resultList = new ArrayList <ResultSet> ();
		TimeLimiter limiter = new SimpleTimeLimiter();
		try {
			resultList = limiter.callWithTimeout(new Callable <ArrayList <ResultSet>>() {
			    public ArrayList <ResultSet> call() throws AAIException {
			      return queryByNamedQuery_Timed( transId_f, fromAppId_f, graph_f,
							namedQueryUuid_f,
				  			startNodeFilterArrayOfHashes_f, 
				  			apiVer_f );
			    }
			}, timeLimitSec, TimeUnit.SECONDS, true);
			
		} 
		catch (AAIException ae) {
			// Re-throw AAIException so we get can tell what happened internally
			throw ae;
		}
		catch (UncheckedTimeoutException ute) {
			throw new AAIException("AAI_6140", "Query Processing Limit exceeded. (limit = " + timeLimitSec + " seconds)");
		}
		catch (Exception e) {
			throw new AAIException("AAI_6128", "Unexpected exception in queryByNamedQuery(): " + e.getMessage() );
		}

		return resultList;
	}
	
	
	/**
	 * Query by named query timed.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param namedQueryUuid the named query uuid
	 * @param startNodeFilterArrayOfHashes the start node filter array of hashes --used to locate the first nodes of instance data	
	 * @param apiVer the api ver
	 * @return resultSet
	 * @throws AAIException the AAI exception
	 */
	public static ArrayList <ResultSet> queryByNamedQuery_Timed( String transId, String fromAppId, TitanTransaction graph,
			String namedQueryUuid,  
			ArrayList <HashMap <String,Object>> startNodeFilterArrayOfHashes, 
			String apiVer ) 
					throws AAIException{
		  
		LogLine logline = new LogLine();
		logline.init("aaidbgen", transId, fromAppId, "queryByNamedQuery");
  
		if( graph == null ){
			String emsg = "null graph object passed to queryByNamedQuery()\n";
			logline.add("emsg", emsg);         	 
			aaiLogger.info(logline, false, "AAI_6101");
			throw new AAIException("AAI_6101", emsg); 
		}
  	
		// Locate the Query to be used
		HashMap <String,Object> propHash0 = new HashMap<String, Object>();
		propHash0.put("named-query-uuid", namedQueryUuid);
		TitanVertex queryVtx = DbMeth.getUniqueNode( transId, fromAppId, graph, "named-query", propHash0, null, apiVer );
		if( queryVtx == null ){
			String msg = "No named-query found for named-query-uuid = " + namedQueryUuid;
			logline.add("msg", msg);
			aaiLogger.info(logline, false, "AAI_6114");
			throw new AAIException("AAI_6114", msg);
		}	
	
		
		//System.out.println("\n DEBUG --- Found query vertex: " );
		//ArrayList <String> retArr = DbMeth.showPropertiesForNode("junkId", "junkApp", queryVtx);
		//for( String info : retArr ){ System.out.println(info); }
		
		
		// Get the first/top named-query-element used by this query
		Iterable <?> verts = queryVtx.query().direction(Direction.OUT).labels("startsWith").vertices();
		Iterator <?> vertI = verts.iterator();
		TitanVertex firstNqElementVert = null;
		int count = 0;
		String topNType = "";
		while( vertI != null && vertI.hasNext() ){
			firstNqElementVert = (TitanVertex) vertI.next();
			count++;
			topNType = getElementWidgetType( firstNqElementVert, "" );
		}
		
		if( count < 1 ){
			// A named query must start with a single top element
			String msg = "No top-node defined for named-query-uuid = [" + namedQueryUuid + "]";
			logline.add("msg", msg);
			aaiLogger.info(logline, false, "AAI_6133");
			throw new AAIException("AAI_6133", msg);
		}
		else if( count > 1 ){
			// A named query should start with a single top element
			String msg = "More than one top-node defined for named-query-uuid = [" + namedQueryUuid + "]";
			logline.add("msg", msg);
			aaiLogger.info(logline, false, "AAI_6133");
			throw new AAIException("AAI_6133", msg);
		}
		if( (topNType == null) || topNType.equals("") ){
			String msg = "Could not determine the top-node nodeType for Named Query: [" + namedQueryUuid + "]";
			throw new AAIException("AAI_6133", msg);
		}
		
		// Read the topology into a hash for processing
		Multimap <String, String> validNextStepMap = genTopoMap4NamedQ(transId, fromAppId, graph, queryVtx, namedQueryUuid);

		ArrayList <TitanVertex> startVertList = new ArrayList <TitanVertex>();
		if( startNodeFilterArrayOfHashes.size() == 1 ){
			// If there is only one set of startFilter info given, then allow it to possibly not be
			// defining just one start node.
			try {
				TitanVertex tmpVtx = DbMeth.getUniqueNodeWithDepParams( transId, fromAppId, graph, 
						topNType, startNodeFilterArrayOfHashes.get(0), apiVer );
				// Only found one, so just use it.
				startVertList.add(tmpVtx);
			}
			catch( AAIException ate ){
				// Either there is more than one node found using these parameters or these
				// were not the key params.    Either way, see if they can lead us to some start-nodes.
				
				// NOTE: for now, getNodes() is only taking parameters for the nodeType passed, so if
				//   any of the parameters have the nodeType appended, it should be stripped off.
				//   Ie. "customer.global-cust-id" should be passed just as, "global-cust-id"
				HashMap <String, Object> cleanHash = new HashMap <String,Object>();
				HashMap <String, Object> tmpHash = startNodeFilterArrayOfHashes.get(0);
				Set <String> propKeySet = tmpHash.keySet();
				Iterator<String> propIter = propKeySet.iterator();
				while( propIter.hasNext() ){
					String oldVtxKey = (String) propIter.next(); 
					String newKey = oldVtxKey;
					String [] parts = oldVtxKey.split("\\.");
					if( parts.length == 2 ){
						newKey = parts[1];
					}
					Object obVal = tmpHash.get(oldVtxKey);
					cleanHash.put(newKey,obVal);
				}
				
				startVertList = DbMeth.getNodes( transId, fromAppId, graph, topNType, 
						cleanHash, false, apiVer, true );
				
				if( startVertList.isEmpty() ){
					String msg = "No Node of type [" + topNType + "] found for properties: " + cleanHash.toString();
					logline.add("msg", msg);
					aaiLogger.info(logline, false, "AAI_6114");
					throw new AAIException("AAI_6114", msg);
				}
			}
		}
		else {
			// Since they give an array of startNodeFilterHash info, we expect each one
			// to just point to one node.
			for( int i = 0; i < startNodeFilterArrayOfHashes.size(); i++ ){
				// Locate the starting node for each set of data
				TitanVertex tmpVtx = DbMeth.getUniqueNodeWithDepParams( transId, fromAppId, graph, 
						topNType, startNodeFilterArrayOfHashes.get(i), apiVer );
				startVertList.add(tmpVtx);
			}
		}
		
		// Make sure they're not bringing back too much data
		String maxString = AAIConfig.get("aai.model.query.resultset.maxcount");
		if( maxString != null &&  !maxString.equals("") ){
			int maxSets = Integer.parseInt(maxString);
			if( startVertList.size() > maxSets ){
				String msg = " Query returns " + startVertList.size() + " resultSets.  Max allowed is: " + maxSets;
				throw new AAIException("AAI_6141", msg);
			}
		}
		
		// Loop through each start node and get its data
		ArrayList <ResultSet> resSetList = new ArrayList <ResultSet> ();
		for( int i = 0; i < startVertList.size(); i++ ){
			TitanVertex startVtx = startVertList.get(i);
			// Collect the data
			String elementLocationTrail = topNType;
			ArrayList <String>  vidsTraversed = new ArrayList <String> ();
			HashMap <String,String> emptyDelKeyHash = new HashMap <String,String> ();  // Does not apply to Named Queries
					
			// Get the mapping of namedQuery elements to our widget topology for this namedQuery
			String incomingTrail = "";
			HashMap <String, String> currentHash = new HashMap <String,String> ();
			
			HashMap <String,String> namedQueryElementHash = collectNQElementHash( transId, fromAppId, graph,
					  firstNqElementVert, incomingTrail, currentHash, vidsTraversed, 0 );
			
			vidsTraversed = new ArrayList <String> ();
			ResultSet tmpResSet = collectInstanceData( transId, fromAppId, graph, 
					startVtx, elementLocationTrail, 
					validNextStepMap, vidsTraversed, 0, emptyDelKeyHash, namedQueryElementHash, apiVer );
			resSetList.add(tmpResSet);
		}
		
		// Since a NamedQuery can mark some nodes as "do-not-display", we need to collapse our resultSet so 
		// does not display those nodes.
		ArrayList <ResultSet> collapsedResSetList = new ArrayList <ResultSet> ();
		if( resSetList != null && !resSetList.isEmpty() ){
			for( int i = 0; i < resSetList.size(); i++ ){
				// Note - a single resultSet could be collapsed into many smaller ones if they
				//    marked all the "top" node-elements as do-not-output.   Ie. the query may
				//    have had a top-node of "generic-vnf" which joins down to different l-interfaces.
				//    If they only want to see the l-interfaces, then a single result set
				//    would be "collapsed" into many separate resultSets - each of which is 
				//    just a single l-interface.
				ArrayList <ResultSet> tmpResSetList = collapseForDoNotOutput(resSetList.get(i));
				if( tmpResSetList != null && !tmpResSetList.isEmpty() ){
					for( int x = 0; x < tmpResSetList.size(); x++ ){
						collapsedResSetList.add(tmpResSetList.get(x));
					}
				}
			}
		}
		
		return collapsedResSetList;
		
		  	
	}// End of queryByNamedQuery()

	
	/**
	 * Collapse for do not output.
	 *
	 * @param resSetVal the res set val
	 * @return the array list
	 * @throws AAIException the AAI exception
	 */
	public static ArrayList <ResultSet> collapseForDoNotOutput( ResultSet resSetVal )
		throws AAIException {
		
		// Given a ResultSet -- if it is tagged to NOT be output, then replace it with 
		// it's sub-ResultSets if it has any. 
		ArrayList <ResultSet> colResultSet = new ArrayList <ResultSet> ();
		
		if( resSetVal.getDoNotOutputFlag().equals("true") ){
			// This ResultSet isn't to be displayed, so replace it with it's sub-ResultSets
			ArrayList <ResultSet> subResList = (ArrayList<ResultSet>) resSetVal.getSubResultSet();
			for( int k = 0; k < subResList.size(); k++ ){
				ArrayList <ResultSet> newSubResList =  collapseForDoNotOutput(subResList.get(k));
				colResultSet.addAll(newSubResList);
			}
		}
		else {
			// This set will be displayed
			colResultSet.add(resSetVal);
		}
		
		// For each result set now at this level, call this same routine to collapse their sub-resultSets
		for( int i = 0; i < colResultSet.size(); i++ ){
			ArrayList <ResultSet> newSubSet = new ArrayList <ResultSet> ();
			ArrayList <ResultSet> subResList = (ArrayList<ResultSet>) colResultSet.get(i).getSubResultSet();
			for( int n = 0; n < subResList.size(); n++ ){
				ArrayList <ResultSet> newSubResList =  collapseForDoNotOutput(subResList.get(n));
				newSubSet.addAll(newSubResList);
			}
			// Replace the old subResultSet with the collapsed set
			colResultSet.get(i).subResultSet = newSubSet;
		}
		
		return colResultSet;
		
	}// End collapseForDoNotOutput()
	
	
    
	/**
	 * Collect instance data.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param thisLevelVertex the this level vertex
	 * @param thisVertsTrail the this verts trail
	 * @param elementLocationTrail -- trail of nodeTypes that got us here (this vertex) from the top
	 * @param validNextStepMap the valid next step map -- hash of valid next steps (node types) for this model
	 * @param vidsTraversed the vids traversed -- ArrayList of vertexId's that we traversed to get to this point
	 * @param levelCounter the level counter
	 * @param delKeyHash -- hashMap of which spots on our topology should be deleted during a modelDelete
	 * @param namedQueryElementHash - hashMap which maps each spot in our widget topology to the NamedQueryElemment that it maps to
	 * @param apiVer the api ver
	 * @return resultSet
	 * @throws AAIException the AAI exception
	 */
	public static ResultSet collectInstanceData( String transId, String fromAppId, TitanTransaction graph,
		  TitanVertex thisLevelVertex, 
		  String thisVertsTrail, 
		  Multimap <String,String> validNextStepMap,
		  ArrayList <String> vidsTraversed, 
		  int levelCounter, 
		  HashMap <String,String> delKeyHash,  // only applies when collecting data using the default model for delete
		  HashMap <String,String> namedQueryElementHash,  // only applies to named-query data collecting
		  String apiVer
		  )   throws AAIException {
		
	  levelCounter++;
	  LogLine logline = new LogLine();
	  logline.init("aaidbgen", transId, fromAppId, "collectInstanceData");
	  
	  if( graph == null ){
		  String emsg = "null graph object passed to collectInstanceData()\n";
		  logline.add("emsg", emsg);         	 
		  aaiLogger.info(logline, false, "AAI_6101");
		  throw new AAIException("AAI_6101", emsg); 
	  }

	  String thisVid = thisLevelVertex.id().toString();
	   
	  if( levelCounter > maxLevels ) {
		  String emsg = "collectInstanceData() has looped across more levels than allowed: " + maxLevels + ". ";
		  logline.add("emsg", emsg);         	 
		  aaiLogger.info(logline, false, "AAI_6125");
		  throw new AAIException("AAI_6125", emsg); 
	  }
	  
	  ResultSet rs = new ResultSet();
	  if( namedQueryElementHash.containsKey(thisVertsTrail) ){
		  // We're collecting data for a named-query, so need to see if we need to do anything special
		  String nqElUuid = namedQueryElementHash.get(thisVertsTrail);
		  HashMap<String, Object> propHash = new HashMap<String, Object>();
		  propHash.put( "named-query-element-uuid", nqElUuid );
		  TitanVertex nqElementVtx = DbMeth.getUniqueNode( transId, fromAppId, graph, "named-query-element", propHash, null, apiVer);
		  if( nqElementVtx == null ){
			  String msg = " Could not find named-query-element with named-query-element-uuid = [" + nqElUuid + "].";
			  throw new AAIException("AAI_6114", msg);
		  }	
		 
		  String tmpDoNotShow = nqElementVtx.<String>property("do-not-output").orElse(null);
		  if( tmpDoNotShow != null && tmpDoNotShow.equals("true") ){
			  rs.doNotOutputFlag = "true";
		  }
		 		  
		  if( namedQueryConstraintSaysStop(transId, fromAppId, graph, nqElementVtx, thisLevelVertex, apiVer) ){
			  // There was a property constraint which says they do not want to collect this vertex or whatever 
			  // might be below it.  Just return the empty rs here.
			  return rs;
		  }
		  
		  String propLimDesc = nqElementVtx.<String>property("property-limit-desc").orElse(null);
		  if( (propLimDesc != null) && !propLimDesc.equals("") ){
			  rs.propertyLimitDesc = propLimDesc;
		  }

		  // Look to see if we need to use an Override of the normal properties
		  HashMap <String,Object> tmpPropertyOverRideHash = getNamedQueryPropOverRide(transId, fromAppId, graph, nqElementVtx, thisLevelVertex, apiVer);
		  //System.out.println(" DEBUG --- USING this propertyOverride data set on ResSet [" + tmpPropertyOverRideHash.toString() + "]");
		  rs.propertyOverRideHash = tmpPropertyOverRideHash;
		  
		  // See if we need to look up any "unconnected" data that needs to be associated with this result set
		  HashMap <String,Object> tmpExtraPropHash = getNamedQueryExtraDataLookup(transId, fromAppId, graph, nqElementVtx, thisLevelVertex, apiVer);
		  //System.out.println(" DEBUG --- ADDING this EXTRA Lookup data to the ResSet [" + tmpExtraPropHash.toString() + "]");
		  rs.extraPropertyHash = tmpExtraPropHash;
	  }
	  
	  rs.vert = thisLevelVertex;
	  rs.locationInModelSubGraph = thisVertsTrail;
	  if( delKeyHash.containsKey(thisVertsTrail) && delKeyHash.get(thisVertsTrail).equals("T") ){
		  rs.newDataDelFlag = "T";
	  }
	  else {
		  rs.newDataDelFlag = "F";
	  }
		
	  // Use Gremlin-pipeline to just look for edges that go to a valid "next-steps"
	  Collection <String> validNextStepColl = validNextStepMap.get(thisVertsTrail);
	  
	  // Because of how we process linkage-points, we may have duplicate node-types in our next-stepMap (for one step)
	  // So, to keep from looking (and bringing back) the same data twice, we need to make sure our next-steps are unique
	  HashSet <String> validNextStepHashSet = new HashSet <String> ();
	  Iterator <String> ntcItr = validNextStepColl.iterator();
	  while( ntcItr.hasNext() ){
		  String targetStepStr = ntcItr.next();
		  validNextStepHashSet.add(targetStepStr);
	  }
	  
	  ArrayList <String> tmpVidsTraversedList = new ArrayList <String> ();
	  tmpVidsTraversedList.addAll(vidsTraversed);
	  tmpVidsTraversedList.add(thisVid);
	  
	  Iterator <String> ntItr = validNextStepHashSet.iterator();
	  while( ntItr.hasNext() ){
		  String targetStep = ntItr.next();
		  // NOTE: NextSteps can either be just a nodeType, or can be a nodeType plus
		  //     persona-model-id and persona-model-version if those need to be checked also.
		  //     When the persona stuff is part of the step, it is a comma separated string.
		  //     Ie.  "nodeType,personaModelId,personaModeVersion"
		  String targetNodeType = "";
		  String pmid = "";
		  String pmv = "";
		  Boolean stepIsJustNT = true;
		  if( targetStep.contains(",") ){
			  stepIsJustNT = false;
			  String[] pieces = targetStep.split(",");
			  if( pieces.length != 3 ){
					String msg = "Unexpected format for nextStep in model processing = [" 
							  + targetStep + "]. ";
						logline.add("msg", msg);
					 	aaiLogger.info(logline, false, "AAI_6128");
					 	throw new AAIException("AAI_6128", msg);
			  }
			  else {
				  targetNodeType = pieces[0];
				  pmid = pieces[1];
				  pmv = pieces[2];
			  }
		  }
		  else {
			  // It's just the nodeType with no other info
			  targetNodeType = targetStep;
		  }
		  
		  GraphTraversal<Vertex, Vertex> modPipe = null;
		  if( stepIsJustNT ){
			  modPipe = graph.traversal().V(thisLevelVertex).both().has("aai-node-type", targetNodeType);
		  }
		  else {
			  modPipe = graph.traversal().V(thisLevelVertex).both().has("aai-node-type", targetNodeType).has("persona-model-id",pmid).has("persona-model-version",pmv);
		  }
		  
		  if( modPipe == null || !modPipe.hasNext() ){
			  //System.out.println("DEBUG - didn't find any [" + targetStep + "] connected to this guy (which is ok)");
		  }
		  else {
			  while( modPipe.hasNext() ){
				  TitanVertex tmpVert = (TitanVertex) modPipe.next();
				  String tmpVid = tmpVert.id().toString();
				  String tmpTrail = thisVertsTrail + "|" + targetStep;
				  if( !vidsTraversed.contains(tmpVid) ){
					  // This is one we would like to use - so we'll include the result set we get for it 
					  ResultSet tmpResSet  = collectInstanceData( transId, fromAppId, graph, 
								tmpVert, tmpTrail, 
								validNextStepMap, tmpVidsTraversedList, 
								levelCounter, delKeyHash, namedQueryElementHash, apiVer );
					  
					  rs.subResultSet.add(tmpResSet);
				  }
			  }
		  }
	  }
	
	  return rs;
	  
	} // End of collectInstanceData()


	/**
	 * Gen topo map 4 model.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param modelVertex the model vertex
	 * @param modelUuid the model uuid
	 * @param dbMaps the db maps
	 * @return MultiMap of valid next steps for each potential model-element
	 * @throws AAIException the AAI exception
	 */
	public static Multimap<String, String> genTopoMap4Model( String transId, String fromAppId, 
			TitanTransaction graph, TitanVertex modelVertex, String modelUuid, DbMaps dbMaps )   
				  throws AAIException {
	
		LogLine logline = new LogLine();
		logline.init("aaidbgen", transId, fromAppId, "genTopoMap4Model");

		if( graph == null ){
			String emsg = "null graph object passed to genTopoMap4Model()\n";
			logline.add("emsg", emsg);         	 
			aaiLogger.info(logline, false, "AAI_6101");
			throw new AAIException("AAI_6101", emsg); 
		}
	  
		if( modelVertex == null ){
			String msg = " null modelVertex passed to genTopoMap4Model() ";
			logline.add("msg", msg);
			aaiLogger.info(logline, false, "AAI_6114");
			throw new AAIException("AAI_6114", msg);
		}
  
		Multimap <String, String> initialEmptyMap = ArrayListMultimap.create();
		ArrayList <String> vidsTraversed = new ArrayList <String> ();
	  
		String modelType = getModelType( modelVertex, "" );
		if( modelType.equals("widget") ){
			// A widget model by itself does not have a topoplogy.  That is - it has no "model-elements" which
			// define how it is connected to other things.   All it has is a name which ties it to 
			// an aai-node-type
			Iterable <?> verts = modelVertex.query().direction(Direction.OUT).labels("startsWith").vertices();
			Iterator <?> vertI = verts.iterator();
			if( vertI != null && vertI.hasNext() ){
				String msg = "Bad Model Definition: Widget Model with a startsWith edge to a model-element.  " 
						+ " Model UUID = " + modelUuid;
				logline.add("msg", msg);
				aaiLogger.info(logline, false, "AAI_6132");
				throw new AAIException("AAI_6132", msg);
			}
			else {
				return initialEmptyMap;
			}
		}  
		  
		String firstModelId = modelVertex.<String>property("model-id").orElse(null);
		String firstModelVersion = modelVertex.<String>property("model-version").orElse(null);
		if( firstModelId == null || firstModelId.equals("") || firstModelVersion == null || firstModelVersion.equals("") ){
			String msg = "Bad Model Definition: Bad model-id or model-version.  Model UUID = " 
				  + modelUuid;
			logline.add("msg", msg);
		 	aaiLogger.info(logline, false, "AAI_6132");
		 	throw new AAIException("AAI_6132", msg);
		}
		
		TitanVertex firstElementVertex = getTopElementForSvcOrResModel( modelVertex );
		TitanVertex firstEleModVtx = getModelThatElementRepresents( firstElementVertex, "" );
		String firstElemModelType = getModelType( firstEleModVtx, "" );	  
		if( ! firstElemModelType.equals("widget") ){
			String msg = "Bad Model Definition: First element must correspond to a widget type model.  Model UUID = " 
				  + modelUuid;
			logline.add("msg", msg);
		 	aaiLogger.info(logline, false, "AAI_6132");
		 	throw new AAIException("AAI_6132", msg);
		}
	    
		Multimap <String, String> collectedMap = collectTopology4Model( transId, fromAppId, graph, 
				firstElementVertex, "", 
				initialEmptyMap, vidsTraversed, 0, dbMaps, null, firstModelId, firstModelVersion );
		
		//DEBUG -----------------
		//System.out.println("DEBUG -- go this topo map for this model: ");
		//Set keySet = collectedMap.keySet();
		//Iterator keyIterator = keySet.iterator();
		//while (keyIterator.hasNext() ) {
		//  String key = (String) keyIterator.next();
		//    System.out.println( "DEBUG -- for this key: [" + key + "], got these values: ");
		//       Collection <String> values = collectedMap.get(key) ;
		//       Iterator valIter = values.iterator();
		//       while( valIter.hasNext() ){
		//       	System.out.println(" >>> [" + valIter.next() + "]");
		//        }
		//}
		//DEBUG -------------------------
	  
		return collectedMap;
	  
	} // End of genTopoMap4Model()



	/**
	 * Gets the mod constraint hash.
	 *
	 * @param modelElementVtx the model element vtx
	 * @param currentHash -- the current ModelConstraint's that this routine will add to if it finds any.
	 * @return HashMap of model-constraints that will be looked at for this model-element and what's "below" it.
	 * @throws AAIException the AAI exception
	 */
	public static HashMap <String, TitanVertex> getModConstraintHash( TitanVertex modelElementVtx, HashMap <String, TitanVertex> currentHash )   
				  throws AAIException {
	
		// For a given model-element vertex, look to see if there are any "model-constraint" elements that is has 
		//   an OUT "uses" edge to.   If it does, then get any "constrained-element-set" nodes that are pointed to
		//   by the "model-constraint".   That will be the replacement "constrained-element-set".  The UUID of the
		//   "constrained-element-set" that it is supposed to replace is found in the property:
		//   model-constraint.constrained-element-set-uuid-to-replace   
		//
		//   For now, that is the only type of model-constraint allowed, so that is all we will look for.  
		//   Pass back any of these "constrained-element-set" nodes along with any that were passed in by 
		//   the "currentHash" parameter.
				
		if( modelElementVtx == null ){
			String msg = " null modelElementVtx passed to getModConstraintHash() ";
			throw new AAIException("AAI_6114", msg);
		}
	  
		String modelType = modelElementVtx.<String>property("aai-node-type").orElse(null);
		if( modelType == null || (!modelType.equals("model-element")) ){
			String msg = " getModConstraintHash() called with wrong type model: [" + modelType + "]. ";
			throw new AAIException("AAI_6114", msg);
		}
	  
		HashMap <String, TitanVertex> thisHash = new HashMap <String, TitanVertex> ();
		if( currentHash != null ){
			thisHash.putAll(currentHash);
		}
	 
		int count = 0;
		ArrayList <TitanVertex> modelConstraintArray = new ArrayList <TitanVertex> ();
		Iterable <?> verts = modelElementVtx.query().direction(Direction.OUT).labels("uses").vertices();
		Iterator <?> vertI = verts.iterator();
		while( vertI != null && vertI.hasNext() ){
			TitanVertex tmpVert = (TitanVertex) vertI.next();
			String connectToType = tmpVert.<String>property("aai-node-type").orElse(null);
			if( (connectToType != null) && connectToType.equals("model-constraint") ){
				// We need to find the constrained element set pointed to by this and add it to the Hash to return
				modelConstraintArray.add(tmpVert);
				count++;
			}
		}
	  
		if( count > 0 ) {
			for( int i = 0; i < count; i++ ){
				TitanVertex vtxOfModelConstraint = modelConstraintArray.get(i);
				String uuidOfTheOneToBeReplaced = vtxOfModelConstraint.<String>property("constrained-element-set-uuid-2-replace").orElse(null);
				// We have the UUID of the constrained-element-set that will be superseded, now find the
				// constrained-element-set to use in its place
				Iterable <?> mverts = vtxOfModelConstraint.query().direction(Direction.OUT).labels("uses").vertices();
				Iterator <?> mvertI = mverts.iterator();
				while( mvertI != null && mvertI.hasNext() ){
					// There better only be one...  
					TitanVertex tmpVert = (TitanVertex) mvertI.next();
					String connectToType = tmpVert.<String>property("aai-node-type").orElse(null);
					if( (connectToType != null) && connectToType.equals("constrained-element-set") ){
						// This is the "constrained-element-set" that we want to use as the Replacement
						thisHash.put(uuidOfTheOneToBeReplaced, tmpVert );
					}
				}
			}
			return thisHash;
		}
		else {
			// Didn't find anything to add, so just return what they passed in.
			return currentHash;
		}
	
	} // End of getModConstraintHash()
 
	
	/**
	 * Gets the top element for svc or res model.
	 *
	 * @param modelVertex the model vertex
	 * @return first element pointed to by this model
	 * @throws AAIException the AAI exception
	 */
	public static TitanVertex getTopElementForSvcOrResModel( TitanVertex modelVertex )   
				  throws AAIException {
	
	  // For a "resource" or "service" type model, return the "top" element in that model
	  if( modelVertex == null ){
		  String msg = " null modelVertex passed to getTopoElementForSvcOrResModel() ";
		  throw new AAIException("AAI_6114", msg);
	  }
	  
	  String modelType = modelVertex.<String>property("model-type").orElse(null);
	  if( modelType == null || (!modelType.equals("service")) && (!modelType.equals("resource")) ){
		  String msg = " getTopElementForSvcOrResModel() called with wrong type model: [" + modelType + "]. ";
		  throw new AAIException("AAI_6114", msg);
	  }
	  String modelUuid = modelVertex.<String>property("model-name-version-id").orElse(null);
	  
	  TitanVertex firstElementVertex = null;
	  Iterable <?> verts = modelVertex.query().direction(Direction.OUT).labels("startsWith").vertices();

	  Iterator <?> vertI = verts.iterator();
	  int elCount = 0;
	  while( vertI != null && vertI.hasNext() ){
		  elCount++;
		  firstElementVertex = (TitanVertex) vertI.next();
	  }
		
	  if( elCount > 1 ){
		  String msg = "Illegal model defined: More than one first element defined for = " + modelUuid;
		  throw new AAIException("AAI_6132", msg);
	  }
	 	  
	  if( firstElementVertex == null ){
		  String msg = "Could not find first model element = " + modelUuid;
		  throw new AAIException("AAI_6132", msg);
	  }
	  
	  return firstElementVertex;
	  
	} // End of getTopElementForSvcOrResModel()

	
	 	 
	/**
	 * Gets the named query prop over ride.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param namedQueryElementVertex the named query element vertex
	 * @param instanceVertex the instance vertex
	 * @param apiVer the api ver
	 * @return HashMap of alternate properties to return for this element
	 * @throws AAIException the AAI exception
	 */
	public static HashMap <String,Object> getNamedQueryPropOverRide( String transId, String fromAppId, TitanTransaction graph,
			TitanVertex namedQueryElementVertex, TitanVertex instanceVertex, String apiVer )   
				  throws AAIException {
		
		// If this model-element says that they want an alternative set of properties returned, then pull that
		// data out of the instance vertex.
		
		HashMap <String,Object> altPropHash = new HashMap <String,Object> ();
			
		if( namedQueryElementVertex == null ){
			String msg = " null namedQueryElementVertex passed to getNamedQueryPropOverRide() ";
			throw new AAIException("AAI_6114", msg);
		}
		
		ArrayList <String> propCollectList = new ArrayList <String> ();
		Iterator <VertexProperty<Object>> vpI = namedQueryElementVertex.properties("property-collect-list");
		while( vpI.hasNext() ){
			VertexProperty <Object> vpOb = vpI.next();
			// Some property-collect-lists are winding up in the database as toString() versions of
			// an arrayList instead of as ArrayLists or as multiple properties with the same name.  
			// Whichever it is, we need to handle it.
			ArrayList <String> propCollList = makeSureItsAnArrayList( vpOb );
			if( propCollList != null ){
				for( int i = 0; i < propCollList.size(); i++ ){
					String thisPropName = propCollList.get(i);
					Object instanceVal = instanceVertex.<Object>property(thisPropName).orElse(null);
					altPropHash.put(thisPropName, instanceVal);
				}
			}
		}
		
		return altPropHash;
	  
	} // End of getNamedQueryPropOverRide()

	
	/**
	 * Named query constraint says stop.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param namedQueryElementVertex the named query element vertex
	 * @param instanceVertex the instance vertex
	 * @param apiVer the api ver
	 * @return true - if a constraint was defined that has not been met by the passed instanceVertex
	 * @throws AAIException the AAI exception
	 */
	public static Boolean namedQueryConstraintSaysStop( String transId, String fromAppId, TitanTransaction graph,
			TitanVertex namedQueryElementVertex, TitanVertex instanceVertex, String apiVer )   
				  throws AAIException {
		
		// For each (if any) property-constraint defined for this named-query-element, we will evaluate if
		// the constraint is met or not-met.  if there are constraints and any are not-met, then
		// we return "true".
		
		if( namedQueryElementVertex == null ){
			String msg = " null namedQueryElementVertex passed to namedQueryConstraintSaysStop() ";
			throw new AAIException("AAI_6114", msg);
		}
		if( instanceVertex == null ){
			String msg = " null instanceVertex passed to namedQueryConstraintSaysStop() ";
			throw new AAIException("AAI_6114", msg);
		}
		
		GraphTraversal<Vertex, Vertex> constrPipe = graph.traversal()
				.V(namedQueryElementVertex).out("uses").has("aai-node-type","property-constraint");
			   
		if( constrPipe == null || !constrPipe.hasNext() ){
			// There's no "property-constraint" defined for this named-query-element.   No problem.
			return false;
		}
		
		while( constrPipe.hasNext() ){
			TitanVertex constrVtx = (TitanVertex) constrPipe.next();
			// We found a property constraint that we will need to check
			String conType = constrVtx.<String>property("constraint-type").orElse(null);
			if( (conType == null) || conType.equals("")){
				String msg = " Bad property-constraint (constraint-type) found in Named Query definition. ";
				throw new AAIException("AAI_6133", msg);
			}
			String propName = constrVtx.<String>property("property-name").orElse(null);
			if( (propName == null) || propName.equals("")){
				String msg = " Bad property-constraint (property-name) found in Named Query definition. ";
				throw new AAIException("AAI_6133", msg);
			}
			String propVal = constrVtx.<String>property("property-value").orElse(null);
			if( (propVal == null) || propVal.equals("")){
				String msg = " Bad property-constraint (propVal) found in Named Query definition. ";
				throw new AAIException("AAI_6133", msg);
			}
			
			// See if that constraint is met or not
			String val = instanceVertex.<String>property(propName).orElse(null);
			if( val == null ){
				val = "";
			}
			
			if( conType.equals("EQUALS") ){
				if( !val.equals(propVal) ){
					// This constraint was not met
					return true;
				}
			}
			else if( conType.equals("NOT-EQUALS") ){
				if( val.equals(propVal) ){
					// This constraint was not met
					return true;
				}
			}
			else {
				String msg = " Bad property-constraint (constraint-type) found in Named Query definition. ";
				throw new AAIException("AAI_6133", msg);
			}
		}
		 
	  	return false;
	  
	} // End of namedQueryConstraintSaysStop()

	
	/**
	 * Gets the named query extra data lookup.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param namedQueryElementVertex the named query element vertex
	 * @param instanceVertex the instance vertex
	 * @param apiVer the api ver
	 * @return HashMap of alternate properties to return for this element
	 * @throws AAIException the AAI exception
	 */
	public static HashMap <String,Object> getNamedQueryExtraDataLookup( String transId, String fromAppId, TitanTransaction graph,
			TitanVertex namedQueryElementVertex, TitanVertex instanceVertex, String apiVer )   
				  throws AAIException {
		
		// For each (if any) related-lookup defined for this named-query-element, we will go and
		// and try to find it.  All the related-lookup data will get put in a hash and returned.
		
		if( namedQueryElementVertex == null ){
			String msg = " null namedQueryElementVertex passed to getNamedQueryExtraDataLookup() ";
			throw new AAIException("AAI_6114", msg);
		}
		if( instanceVertex == null ){
			String msg = " null instanceVertex passed to getNamedQueryExtraDataLookup() ";
			throw new AAIException("AAI_6114", msg);
		}
		
		HashMap <String,Object> retHash = new HashMap <String,Object> ();
		
		GraphTraversal<Vertex, Vertex> lookPipe = graph.traversal()
				.V(namedQueryElementVertex).out("uses").has("aai-node-type","related-lookup");
			   
		if( lookPipe == null || !lookPipe.hasNext() ){
			// There's no "related-lookup" defined for this named-query-element.   No problem.
			return retHash;
		}
		
		while( lookPipe.hasNext() ){
			TitanVertex relLookupVtx = (TitanVertex) lookPipe.next();
			
			//System.out.println("DEBUG --- found a related-lookup record -- ");
			//ArrayList <String> retArr = DbMeth.showPropertiesForNode("junkId", "junkApp", relLookupVtx);
			//for( String info : retArr ){ System.out.println(info); }
						
			// We found a related-lookup record to try and use
			String srcProp = relLookupVtx.<String>property("source-node-property").orElse(null);
			if( (srcProp == null) || srcProp.equals("")){
				String msg = " Bad related-lookup (source-node-property) found in Named Query definition. ";
				throw new AAIException("AAI_6133", msg);
			}
			String targetNodeType = relLookupVtx.<String>property("target-node-type").orElse(null);
			if( (targetNodeType == null) || targetNodeType.equals("")){
				String msg = " Bad related-lookup (targetNodeType) found in Named Query definition. ";
				throw new AAIException("AAI_6133", msg);
			}
			String targetProp = relLookupVtx.<String>property("target-node-property").orElse(null);
			if( (targetProp == null) || targetProp.equals("")){
				String msg = " Bad related-lookup (target-node-property) found in Named Query definition. ";
				throw new AAIException("AAI_6133", msg);
			}

       	   /***
			ArrayList <String> propCollectList = new ArrayList <String>();
			Iterator <VertexProperty<Object>> vpI = relLookupVtx.properties("property-collect-list");
			while( vpI.hasNext() ){
				VertexProperty <Object> vpOb = vpI.next();
				// Some property-collect-lists are winding up in the database as toString() versions of
				// an arrayList instead of as ArrayLists or as multiple properties with the same name.  
				// Whichever it is, we need to handle it.
				ArrayList <String> propCList = makeSureItsAnArrayList( vpOb );
				if( propCList != null ){
					for( int i = 0; i < propCList.size(); i++ ){
						String thisPropName = propCList.get(i);
						propCollectList.add(thisPropName);
					}
				}
			}
			***/
			
			ArrayList <String> propCollectList = new ArrayList <String> ();
			Iterator <VertexProperty<Object>> vpI = relLookupVtx.properties("property-collect-list");
			while( vpI.hasNext() ){
				propCollectList.add((String)vpI.next().value());
			}



			
			// Use the value from the source to see if we can find ONE target record using the value from the source
			String valFromInstance = instanceVertex.<String>property(srcProp).orElse(null);
			if( valFromInstance == null ){
				valFromInstance = "";
			}
			
			HashMap <String,Object> propHash = new HashMap <String,Object>();
			propHash.put(targetProp, valFromInstance);
			ArrayList <TitanVertex> vertList = DbMeth.getNodes(transId, fromAppId, graph, targetNodeType, propHash, true, apiVer, true);
			int foundCount = vertList.size();
			
			if( foundCount == 0 ){
				//System.out.println("\n\n---------- FOUND NOTHING on the related lookup for: [" + targetNodeType 
				//		+ "], using propHash = [" + propHash + "]\n");
			}
			else if( foundCount > 1 ){
				//System.out.println("DEBUG -- can't use this because there are too many records found using targetNodeType = [" 
				//		+ targetNodeType + "], with property Hash = [" + propHash + "]\n"); 
			}
			else {
				TitanVertex tmpVtx = vertList.get(0); 
				//System.out.println("\nDEBUG -- Found a related vertex using our lookup  ");
				// Pick up the properties from the target vertex that they wanted us to get
				for( int j = 0; j < propCollectList.size(); j++ ){
					String tmpPropName = propCollectList.get(j);
					Object valObj = tmpVtx.<Object>property(tmpPropName).orElse(null);
					String lookupKey = targetNodeType + "." + tmpPropName;
					retHash.put(lookupKey, valObj);
				}
			}
		}
		 
	  	return retHash;
	  
	} // End of getNamedQueryExtraDataLookup()


	/**
	 * Collect NQ element hash.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param thisLevelVertex the this level vertex
	 * @param incomingTrail the incoming trail -- trail of nodeTypes that got us here (this nq-element vertex) from the top
	 * @param currentHash the current hash
     * @param Map that got us to this point (that we will use as the base of the map we will return)
	 * @param vidsTraversed the vids traversed -- ArrayList of vertexId's that we traversed to get to this point
	 * @param levelCounter the level counter
	 * @return HashMap of all widget-points on a namedQuery topology with the value being the "named-query-element-uuid" for that spot.
	 * @throws AAIException the AAI exception
	 */
	public static HashMap <String, String> collectNQElementHash( String transId, String fromAppId, TitanTransaction graph,
		  TitanVertex thisLevelVertex, String incomingTrail, 
		  HashMap <String,String> currentHash, ArrayList <String> vidsTraversed, 
		  int levelCounter )   throws AAIException {
	
	  levelCounter++;
	  LogLine logline = new LogLine();
	  logline.init("aaidbgen", transId, fromAppId, "collectNQElementHash");
	  
	  HashMap <String, String> thisHash = new HashMap <String,String> ();
	  thisHash.putAll(currentHash);
	 
	  if( levelCounter > maxLevels ) {
		  String emsg = "collectNQElementHash() has looped across more levels than allowed: " + maxLevels + ". ";
		  logline.add("emsg", emsg);         	 
		  aaiLogger.info(logline, false, "AAI_6125");
		  throw new AAIException("AAI_6125", emsg); 
	  }
	  String thisGuysTrail = "";
	  String thisVid = thisLevelVertex.id().toString();
	 
	  // Find out what widget-model (and thereby what aai-node-type) this element represents.
	   String thisElementNodeType = getElementWidgetType( thisLevelVertex, incomingTrail );
	  
	  if( incomingTrail.equals("") ){
		  // This is the first one
		  thisGuysTrail = thisElementNodeType;
	  }
	  else {
		  thisGuysTrail = incomingTrail + "|" + thisElementNodeType;
	  }
	  vidsTraversed.add(thisVid);
	  
	  String nqElementUuid = thisLevelVertex.<String>property("named-query-element-uuid").orElse(null);
	  if( nqElementUuid == null || nqElementUuid.equals("") ){
		  String msg = " named-query element UUID not found at trail = [" + incomingTrail + "].";
		  throw new AAIException("AAI_6133", msg);
	  }
	  thisHash.put(thisGuysTrail, nqElementUuid ); 
	  
	  //  Now go "down" and look at the sub-elements pointed to so we can get their data.
	  Iterable <?> verts = thisLevelVertex.query().direction(Direction.OUT).labels("connectsTo").vertices();
	  Iterator <?> vertI = verts.iterator();
	  while( vertI != null && vertI.hasNext() ){
		  TitanVertex tmpVert = (TitanVertex) vertI.next();
		  String vid = tmpVert.id().toString();
		  HashMap <String,Object> elementHash = new HashMap<String, Object>();
		  
		  String connectToType = tmpVert.<String>property("aai-node-type").orElse(null);
		  if( connectToType != null && connectToType.equals("named-query-element") ){
			  // This is what we would expect
			  elementHash.put(vid, tmpVert);
		  }
		  else {
			  String msg = " named query element has [connectedTo] edge to improper nodeType= [" 
					  + connectToType + "] trail = [" + incomingTrail + "].";
			  throw new AAIException("AAI_6133", msg);
		  }
		  for( Map.Entry<String, Object> entry : elementHash.entrySet() ){
			  TitanVertex elVert = (TitanVertex)(entry.getValue());
			  String tmpElVid = elVert.id().toString();
			  if( !vidsTraversed.contains(tmpElVid) ){
				  // This is one we would like to use - so we'll recursively get it's result set to add to ours
				  HashMap <String, String> tmpHash = collectNQElementHash( transId, fromAppId, graph, 
							elVert, thisGuysTrail, currentHash, vidsTraversed, levelCounter);
				  thisHash.putAll(tmpHash);
			  }
		  }		
	  }
	  return thisHash;
	  
	} // End of collectNQElementHash()
	

	/**
	 * Collect delete key hash.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param thisLevelVertex the this level vertex
	 * @param incomingTrail the incoming trail -- trail of nodeTypes that got us here (this vertex) from the top
	 * @param currentHash the current hash
     * @param Map that got us to this point (that we will use as the base of the map we will return)
	 * @param vidsTraversed the vids traversed ---- ArrayList of vertexId's that we traversed to get to this point
	 * @param levelCounter the level counter
	 * @param dbMaps the db maps
	 * @param modConstraintHash the mod constraint hash
	 * @param overRideModelId the over ride model id
	 * @param overRideModelVersion the over ride model version
	 * @return HashMap of all widget-points on a model topology with the value being the "newDataDelFlag" for that spot.
	 * @throws AAIException the AAI exception
	 */
	public static HashMap <String, String> collectDeleteKeyHash( String transId, String fromAppId, TitanTransaction graph,
		  TitanVertex thisLevelVertex, String incomingTrail, 
		  HashMap <String,String> currentHash, ArrayList <String> vidsTraversed, 
		  int levelCounter, DbMaps dbMaps, HashMap <String, TitanVertex> modConstraintHash,
		  String overRideModelId, String overRideModelVersion )   
				  throws AAIException {
	
	  levelCounter++;
	  LogLine logline = new LogLine();
	  logline.init("aaidbgen", transId, fromAppId, "collectDeleteKeyHash");
	  
	  HashMap <String, String> thisHash = new HashMap <String,String> ();
	  thisHash.putAll(currentHash);
	 
	  if( levelCounter > maxLevels ) {
		  String emsg = "collectDeleteKeyHash() has looped across more levels than allowed: " + maxLevels + ". ";
		  logline.add("emsg", emsg);         	 
		  aaiLogger.info(logline, false, "AAI_6125");
		  throw new AAIException("AAI_6125", emsg); 
	  }
	  String thisGuysTrail = "";
	  String thisVid = thisLevelVertex.id().toString();
	  HashMap <String, TitanVertex> modConstraintHash2Use = null;
	  
	  // If this element represents a resource or service model, then we will replace this element with 
	  // 	the "top" element of that resource or service model.  That model-element already points to its
	  // 	topology, so it will graft in that model's topology.   
	  // EXCEPT - if this element has "linkage-points" defined, then we need to do some extra
	  //     processing for how we join to that model and will not try to go any "deeper".
	
	  //ArrayList <String> linkagePtList = thisLevelVertex.<ArrayList>property("linkage-points").orElse(null);
	  Object objVal = thisLevelVertex.property("linkage-points").orElse(null);
	  ArrayList <String> linkagePtList = makeSureItsAnArrayList( objVal );
	  
	  if( linkagePtList != null && !linkagePtList.isEmpty() ){
		  // Whatever this element is - we are connecting to it via a linkage-point
		  // We will figure out what to do and then return without going any deeper
		  String elemFlag = thisLevelVertex.<String>property("new-data-del-flag").orElse(null);
		  
		  HashSet <String> linkageConnectNodeTypes = getLinkageConnectNodeTypes( linkagePtList );
		  Iterator <?> linkNtIter = linkageConnectNodeTypes.iterator();
		  String incTrail = "";
		  if( !incomingTrail.equals("") ){
			  incTrail = incomingTrail + "|";
		  }
		  
		  while( linkNtIter.hasNext() ){
			  // The 'trail' (or trails) for this element should just be the to the first-contact on the linkage point
			  String linkTrail = incTrail + linkNtIter.next();
			  Boolean alreadyTaggedFalse = false;
			  if( thisHash.containsKey(linkTrail) && thisHash.get(linkTrail).equals("F") ){
				  // some other path with a matching trail has the deleteFlag set to "F", so we do not want
				  // to override that since our model code only uses nodeTypes to know where it is - and we
				  // would rather do less deleting than needed instead of too much deleting.
				  alreadyTaggedFalse = true;
			  }
			  if( elemFlag != null && elemFlag.equals("T") && !alreadyTaggedFalse ){
				  // This trail should be marked with an "T"
				  thisHash.put(linkTrail, "T");
			  }
			  else {
				  thisHash.put(linkTrail, "F");
			  }
		  }
		  return thisHash;
	  }


	  
	  // ---------------------------------------------------------------
	  // If we got to here, then this was not an element that used a linkage-point 
	  // ---------------------------------------------------------------
	  
	  // Find out what widget-model (and thereby what aai-node-type) this element represents.
	  // Even if this element is pointing to a service or resource model, it must have a
	  // first element which is a single widget-type model.  
	  String thisElementNodeType = getElementWidgetType( thisLevelVertex, incomingTrail );
	  String firstElementModelInfo = "";

	  vidsTraversed.add(thisVid);
	  TitanVertex elementVtxForThisLevel = null;
	  TitanVertex thisElementsModelVtx = getModelThatElementRepresents( thisLevelVertex, incomingTrail );
	  String modType = getModelType( thisElementsModelVtx, incomingTrail );

	  String subModelFirstModId = thisElementsModelVtx.<String>property("model-id").orElse(null);
	  String subModelFirstVersion = thisElementsModelVtx.<String>property("model-version").orElse(null);
	  if( modType.equals("widget") ){
		  if( overRideModelId != null && !overRideModelId.equals("") ){
			  // Note - this is just to catch the correct model for the TOP node in a model since
			  //    it will have an element which will always be a widget even though the model
			  //    could be a resource or service model.
			  firstElementModelInfo = "," + overRideModelId + "," + overRideModelVersion;
		  }
	  }	
	  else if( nodeTypeSupportsPersona(thisElementNodeType, dbMaps) ){
		  firstElementModelInfo = "," + subModelFirstModId + "," + subModelFirstVersion;
	  }
			  
	  if( incomingTrail.equals("") ){
		  // This is the first one
		  thisGuysTrail = thisElementNodeType + firstElementModelInfo;
	  }
	  else {
		  thisGuysTrail = incomingTrail + "|" + thisElementNodeType  + firstElementModelInfo;
	  }
	  
	  String tmpFlag = "F";
	  Boolean stoppedByASvcOrResourceModelElement = false;
	  if( modType.equals("widget") ){
		  elementVtxForThisLevel = thisLevelVertex;
		  // For the element-model for the widget at this level, record it's delete flag 
		  tmpFlag = elementVtxForThisLevel.<String>property("new-data-del-flag").orElse(null);
	  }
	  else {
		  // For an element that is referring to a resource or service model, we replace this
	      // this element with the "top" element for that resource/service model so that the
		  // topology of that resource/service model gets included in this topology.
		  String modelUuid = thisElementsModelVtx.<String>property("model-name-version-id").orElse(null);
		  if( subModelFirstModId == null || subModelFirstModId.equals("") 
				  || subModelFirstVersion == null || subModelFirstVersion.equals("") ){
			  String msg = "Bad Model Definition: Bad model-id or model-version.  Model UUID = " + modelUuid;
			  logline.add("msg", msg);
			  aaiLogger.info(logline, false, "AAI_6132");
			  throw new AAIException("AAI_6132", msg);
		  }
		  
		  // BUT --  if the model-element HERE at the resource/service level does NOT have 
		  //    it's new-data-del-flag set to "T", then we do not need to go down into the 
		  //    sub-model looking for delete-able things.

		  tmpFlag = thisLevelVertex.<String>property("new-data-del-flag").orElse(null);
		  elementVtxForThisLevel = getTopElementForSvcOrResModel(thisElementsModelVtx); 
		  if( tmpFlag != null && tmpFlag.equals("T") ){
			  modConstraintHash2Use = getModConstraintHash( thisLevelVertex, modConstraintHash );
		  }
		  else {
			  stoppedByASvcOrResourceModelElement = true;
		  }
		  // For the element-model for the widget at this level, record it's delete flag 
		  tmpFlag = elementVtxForThisLevel.<String>property("new-data-del-flag").orElse(null);
	  }
	  
	  String flag2Use = "F";  // by default we'll use "F" for the delete flag
	  if( ! stoppedByASvcOrResourceModelElement ){
		  // Since we haven't been stopped by a resource/service level "F", we can look at the lower level flag
		  if( thisHash.containsKey(thisGuysTrail) ){
			  // We've seen this spot in the topology before - do not override the delete flag if the older one is "F"
			  // We will only over-ride it if the old one was "T" and the new one is "F" (anything but "T")
			  String oldFlag = thisHash.get(thisGuysTrail);
			  if( oldFlag.equals("T") && (tmpFlag != null) && tmpFlag.equals("T") ){
				  // The old flag was "T" and the new flag is also "T"
				  flag2Use = "T";
			  }
			  else {
				  // the old flag was not "F" - so don't override it
				  flag2Use = "F";
			  }
		  }
		  else if( (tmpFlag != null) && tmpFlag.equals("T") ){
			  // We have not seen this one, so we can set it to "T" if that's what it is.
			  flag2Use = "T";
		  }
	  }
	  
	  thisHash.put(thisGuysTrail, flag2Use); 
	  if( ! stoppedByASvcOrResourceModelElement ){
		  // Since we haven't been stopped by a resource/service level "F", we will continue to
		  //     go "down" and look at the elements pointed to so we can get their data.
		  Iterable <?> verts = elementVtxForThisLevel.query().direction(Direction.OUT).labels("connectsTo").vertices();
		  Iterator <?> vertI = verts.iterator();
		  
		   while( vertI != null && vertI.hasNext() ){
			  TitanVertex tmpVert = (TitanVertex) vertI.next();
			  String vid = tmpVert.id().toString();
			  HashMap <String,Object> elementHash = new HashMap<String, Object>();
			  
			  String connectToType = tmpVert.<String>property("aai-node-type").orElse(null);
			  if( connectToType != null && connectToType.equals("model-element") ){
				  // A nice, regular old model-element
				  elementHash.put(vid, tmpVert);
			  }
			  else if( (connectToType != null) && connectToType.equals("constrained-element-set") ){
				  // translate the constrained-element-set into a hash of model-element TitanVertex's
				  String constrainedElementSetUuid = tmpVert.<String>property("constrained-element-set-uuid").orElse(null);
				  if( (modConstraintHash2Use != null) && modConstraintHash2Use.containsKey(constrainedElementSetUuid) ){
					  // This constrained-element-set is being superseded by a different one
					  TitanVertex replacementConstraintVert = modConstraintHash.get(constrainedElementSetUuid);
					  elementHash = getNextStepElementsFromSet( replacementConstraintVert );
					  // Now that we've found and used the replacement constraint, we don't need to carry it along any farther
					  modConstraintHash.remove(constrainedElementSetUuid);
				  }
				  else {
					  elementHash = getNextStepElementsFromSet( tmpVert );    
				  }
			  }
			  else {
				  String msg = " model element has [connectedTo] edge to improper nodeType= [" 
						  + connectToType + "] trail = [" + incomingTrail + "].";
				  throw new AAIException("AAI_6132", msg);
			  }
			  
			  for( Map.Entry<String, Object> entry : elementHash.entrySet() ){
				  TitanVertex elVert = (TitanVertex)(entry.getValue());
				  String tmpElVid = elVert.id().toString();
				  String tmpElNT = getElementWidgetType( elVert, thisGuysTrail );
				  check4EdgeRule(tmpElNT, thisElementNodeType, dbMaps);
				  if( !vidsTraversed.contains(tmpElVid) ){
					  // This is one we would like to use - so we'll recursively get it's result set to add to ours
					  HashMap <String, String> tmpHash = collectDeleteKeyHash( transId, fromAppId, graph, 
								elVert, thisGuysTrail, 
								currentHash, vidsTraversed, levelCounter, dbMaps, modConstraintHash2Use,
								"", "" );
					  thisHash.putAll(tmpHash);
				  }
			  }		
		  }
	  }
	  return thisHash;
	  
	} // End of collectDeleteKeyHash()
	
	
	/**
	 * Gets the linkage connect node types.
	 *
	 * @param linkagePtList the linkage pt list
	 * @return the linkage connect node types
	 * @throws AAIException the AAI exception
	 */
	public static HashSet <String> getLinkageConnectNodeTypes( ArrayList <String> linkagePtList )
			  throws AAIException {
		// linkage points are a path from the top of a model to where we link in.  
		// This method wants to just bring back a list of distinct last items.  
		// Ie: for the input with these two:  "pserver|lag-link|l-interface" and "pserver|p-interface|l-interface"
		//   it would just return a single item, "l-interface" since both linkage points end in that same node-type.
		
		HashSet <String> linkPtSet = new HashSet <String> ();
		
		if( linkagePtList == null ){
			String detail = " Bad (null) linkagePtList passed to getLinkageConnectNodeTypes() ";
			throw new AAIException("AAI_6125", detail); 
		}
		
		for( int i = 0; i < linkagePtList.size(); i++ ){
			String [] trailSteps = linkagePtList.get(i).split("\\|");
			if( trailSteps == null || trailSteps.length == 0 ){
				String detail = " Bad incomingTrail passed to getLinkageConnectNodeTypes(): [" + linkagePtList + "] ";
				throw new AAIException("AAI_6125", detail); 
			}
			String lastStepNT = trailSteps[trailSteps.length - 1];
			linkPtSet.add(lastStepNT);
		}
		
		return linkPtSet;
	
	}// End getLinkageConnectNodeTypes()
	
	
	/**
	 * Collect topology 4 model.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param thisLevelVertex the this level vertex
	 * @param modelElement vertex to collect for
	 * @param incomingTrail the incoming trail -- trail of nodeTypes/personnaInfo that got us here (this vertex) from the top
	 * @param currentMap the current map -- map that got us to this point (that we will use as the base of the map we will return)
	 * @param vidsTraversed the vids traversed -- ArrayList of vertexId's that we traversed to get to this point
	 * @param levelCounter the level counter
	 * @param dbMaps the db maps
	 * @param modConstraintHash the mod constraint hash
	 * @param overRideModelId the over ride model id
	 * @param overRideModelVersion the over ride model version
	 * @return Map of the topology
	 * @throws AAIException the AAI exception
	 */
	public static Multimap<String, String> collectTopology4Model( String transId, String fromAppId, TitanTransaction graph,
		  TitanVertex thisLevelVertex, String incomingTrail, 
		  Multimap <String,String> currentMap, ArrayList <String> vidsTraversed, 
		  int levelCounter, DbMaps dbMaps, HashMap <String, TitanVertex> modConstraintHash,
		  String overRideModelId, String overRideModelVersion )   
				  throws AAIException {
	
	  levelCounter++;
	  LogLine logline = new LogLine();
	  logline.init("aaidbgen", transId, fromAppId, "collectTopology4Model");
	  
	  Multimap <String, String> thisMap = ArrayListMultimap.create();
	  thisMap.putAll(currentMap);
	 
	  if( levelCounter > maxLevels ) {
		  String emsg = "collectTopology4Model() has looped across more levels than allowed: " + maxLevels + ". ";
		  logline.add("emsg", emsg);         	 
		  aaiLogger.info(logline, false, "AAI_6125");
		  throw new AAIException("AAI_6125", emsg); 
	  }
	  String thisGuysTrail = "";
	  String thisVid = thisLevelVertex.id().toString();
	  HashMap <String, TitanVertex> modConstraintHash2Use = null;
 

	  // If this element represents a resource or service model, then we will replace this element with 
	  // the "top" element of that resource or service model.  That model-element already points to its
	  // topology, so it will graft in that model's topology.   
	  // EXCEPT - if this element defines "linkage-points" defined, then we need to do some extra
	  //     processing for how we join to that model.
	
	  // Find out what widget-model (and thereby what aai-node-type) this element represents.
	  // Even if this element is pointing to a service or resource model, it must have a
	  // first element which is a single widget-type model. 
	  String firstElementModelInfo = "";
	  String thisElementNodeType = getElementWidgetType( thisLevelVertex, incomingTrail );
	  if( nodeTypeSupportsPersona(thisElementNodeType, dbMaps) && overRideModelId != null && !overRideModelId.equals("") ){
		  firstElementModelInfo = "," + overRideModelId + "," + overRideModelVersion;
	  }
	  
	  vidsTraversed.add(thisVid);
	  TitanVertex elementVtxForThisLevel = null;
	  TitanVertex thisElementsModelVtx = getModelThatElementRepresents( thisLevelVertex, incomingTrail );
	  String subModelFirstModId = "";
	  String subModelFirstVersion = "";
	  String modInfo4Trail = "";
	  String modType = getModelType( thisElementsModelVtx, incomingTrail );
	  if( modType.equals("resource") || modType.equals("service") ){
		  // For an element that is referring to a resource or service model, we replace this
	      // this element with the "top" element for that resource/service model so that the
		  // topology of that resource/service model gets included in this topology.
		  // -- Note - since that top element of a service or resource model will point to a widget model, 
		  //    we have to track what modelId/version it really maps so we can make our recursive call
		  subModelFirstModId = thisElementsModelVtx.<String>property("model-id").orElse(null);
		  subModelFirstVersion = thisElementsModelVtx.<String>property("model-version").orElse(null);
		  modInfo4Trail = "," + subModelFirstModId + "," + subModelFirstVersion;
		  String modelUuid = thisElementsModelVtx.<String>property("model-name-version-id").orElse(null);
		  if( subModelFirstModId == null || subModelFirstModId.equals("") || subModelFirstVersion == null || subModelFirstVersion.equals("") ){
			  String msg = "Bad Model Definition: Bad model-id or model-version.  Model UUID = " + modelUuid;
			  logline.add("msg", msg);
			  aaiLogger.info(logline, false, "AAI_6132");
			  throw new AAIException("AAI_6132", msg);
		  }
		  elementVtxForThisLevel = getTopElementForSvcOrResModel(thisElementsModelVtx);  
		  modConstraintHash2Use = getModConstraintHash( thisLevelVertex, modConstraintHash );
	  }
	  else {
		  elementVtxForThisLevel = thisLevelVertex;
	  }
	  
	  if( incomingTrail.equals("") ){
		  // This is the first one
		  thisGuysTrail = thisElementNodeType + firstElementModelInfo;
	  }
	  else {
		  thisGuysTrail = incomingTrail + "|" + thisElementNodeType + modInfo4Trail;
	  }
	 
	  // Look at the elements pointed to at this level and add on their data
	  Iterable <?> verts = elementVtxForThisLevel.query().direction(Direction.OUT).labels("connectsTo").vertices();
	  Iterator <?> vertI = verts.iterator();
	  
	   while( vertI != null && vertI.hasNext() ){
		  TitanVertex tmpVert = (TitanVertex) vertI.next();
		  String vid = tmpVert.id().toString();
		  HashMap <String,Object> elementHash = new HashMap<String, Object>();
		  
		  String connectToType = tmpVert.<String>property("aai-node-type").orElse(null);
		  if( connectToType != null && connectToType.equals("model-element") ){
			  // A nice, regular old model-element
			  elementHash.put(vid, tmpVert);
		  }
		  else if( (connectToType != null) && connectToType.equals("constrained-element-set") ){
			  // translate the constrained-element-set into a hash of model-element TitanVertex's
			  String constrainedElementSetUuid = tmpVert.<String>property("constrained-element-set-uuid").orElse(null);
			  if( (modConstraintHash2Use != null) && modConstraintHash2Use.containsKey(constrainedElementSetUuid) ){
				  // This constrained-element-set is being superseded by a different one
				  TitanVertex replacementConstraintVert = modConstraintHash.get(constrainedElementSetUuid);
				  elementHash = getNextStepElementsFromSet( replacementConstraintVert );
				  // Now that we've found and used the replacement constraint, we don't need to carry it along any farther
				  modConstraintHash.remove(constrainedElementSetUuid);
			  }
			  else {
				  elementHash = getNextStepElementsFromSet( tmpVert );    
			  }
		  }
		  else {
			  String msg = " model element has [connectedTo] edge to improper nodeType= [" 
					  + connectToType + "] trail = [" + incomingTrail + "].";
			  throw new AAIException("AAI_6132", msg);
		  }
		  
		  for( Map.Entry<String, Object> entry : elementHash.entrySet() ){
			  TitanVertex elVert = (TitanVertex)(entry.getValue());
			  String tmpElVid = elVert.id().toString();
			  String tmpElNT = getElementWidgetType( elVert, thisGuysTrail );
			  String tmpElStepName = getElementStepName( elVert, thisGuysTrail, dbMaps );
			  
			  //ArrayList <String> linkagePtList = elVert.<ArrayList>property("linkage-points").orElse(null);
			  Object objVal = elVert.property("linkage-points").orElse(null);
			  ArrayList <String> linkagePtList = makeSureItsAnArrayList( objVal );

			  if( linkagePtList != null && !linkagePtList.isEmpty() ){
				  // This is as far as we can go, we will use the linkage point info to define the 
				  // rest of this "trail" 
				  for( int i = 0; i < linkagePtList.size(); i++ ){
					  Multimap<String, String> tmpMap = collectTopology4LinkagePoint( transId, fromAppId,  
								linkagePtList.get(i), thisGuysTrail, currentMap, dbMaps);
					  thisMap.putAll(tmpMap);
				  }
			  }
			  else {
				  check4EdgeRule(tmpElNT, thisElementNodeType, dbMaps);
				  thisMap.put(thisGuysTrail, tmpElStepName);
				  if( !vidsTraversed.contains(tmpElVid) ){
					  // This is one we would like to use - so we'll recursively get it's result set to add to ours
					  Multimap<String, String> tmpMap = collectTopology4Model( transId, fromAppId, graph, 
								elVert, thisGuysTrail, 
								currentMap, vidsTraversed, levelCounter, 
								dbMaps, modConstraintHash2Use, subModelFirstModId, subModelFirstVersion );
					  thisMap.putAll(tmpMap);
				  }
				  else {
					  String msg = "Bad Model Definition: looping model-elements found at: [" + tmpElStepName + "]." ;
					  System.out.println( msg );
					  throw new AAIException("AAI_6132", msg);
				  }	  
			  }  
		  }		
	  }
	  return thisMap;
	  
	} // End of collectTopology4Model()
	
	
	/**
	 * Check 4 edge rule.
	 *
	 * @param nodeTypeA the node type A
	 * @param nodeTypeB the node type B
	 * @param dbMaps the db maps
	 * @throws AAIException the AAI exception
	 */
	public static void check4EdgeRule( String nodeTypeA, String nodeTypeB, DbMaps dbMaps ) throws AAIException {
		// Throw an exception if there is no defined edge rule for this combination of nodeTypes in DbEdgeRules.
		String fwdRuleKey = nodeTypeA + "|" + nodeTypeB;
		String revRuleKey = nodeTypeB + "|" + nodeTypeA;
		if( !DbEdgeRules.EdgeRules.containsKey(fwdRuleKey) 
				&&  !DbEdgeRules.EdgeRules.containsKey(revRuleKey) ){
			// There's no EdgeRule for this -- find out if one of the nodeTypes is invalid or if
			// they are valid, but there's just no edgeRule for them.
			if( ! dbMaps.NodeProps.containsKey(nodeTypeA) ){
				String emsg = " Unrecognized nodeType aa [" + nodeTypeA + "]\n";
				throw new AAIException("AAI_6115", emsg); 
			}
			else if( ! dbMaps.NodeProps.containsKey(nodeTypeB) ){
				String emsg = " Unrecognized nodeType bb [" + nodeTypeB + "]\n";
				throw new AAIException("AAI_6115", emsg); 
			}
			else {
				String msg = " No Edge Rule found for this pair of nodeTypes (order does not matter) [" 
					  + nodeTypeA + "], [" + nodeTypeB + "].";
				throw new AAIException("AAI_6120", msg);
			}
		}
		
	}
	
    
	/**
	 * Collect topology 4 linkage point.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param linkagePointStr -- Note it is in reverse order from where we connect to it.
	 * @param incomingTrail -- trail of nodeTypes that got us here (this vertex) from the top
	 * @param currentMap the current map -- that got us to this point (that we will use as the base of the map we will return)
	 * @param dbMaps the db maps
	 * @return Map of the topology
	 * @throws AAIException the AAI exception
	 */
	public static Multimap<String, String> collectTopology4LinkagePoint( String transId, String fromAppId, 
		  String linkagePointStr, String incomingTrail, Multimap <String,String> currentMap, DbMaps dbMaps )   
				  throws AAIException {
	
	  LogLine logline = new LogLine();
	  logline.init("aaidbgen", transId, fromAppId, "collectTopology4LinkagePoint");
	  
	  Multimap <String, String> thisMap = ArrayListMultimap.create();
	  thisMap.putAll(currentMap);
	  String thisGuysTrail = incomingTrail;
	  // NOTE - "trails" can have multiple parts now since we track persona info for some.
	  //      We just want to look at the node type info - which would be the piece
	  //      before any commas (if there are any).
	  String [] trailSteps = incomingTrail.split("\\|");
	  if( trailSteps == null || trailSteps.length == 0 ){
		  String detail = " Bad incomingTrail passed to collectTopology4LinkagePoint(): [" + incomingTrail + "] ";
		  logline.add("emsg", detail);
		  aaiLogger.info(logline, false, "AAI_6125");
		  throw new AAIException("AAI_6125", detail); 
	  }
	  String lastStepString = trailSteps[trailSteps.length - 1];
	  String [] stepPieces = lastStepString.split(",");
	  String lastStepNT = stepPieces[0];
	  // It is assumed that the linkagePoint string will be a pipe-delimited string where each
	  // piece is an "aai-node-type".  For now, the first thing to connect to is what is on the farthest right.
	  // Example:  linkagePoint =  "pserver|p-interface|l-interface"   would mean that we're connecting to the l-interface
	  //      but that after that, we connect to a p-interface followed by a pserver.
	  // It might have been more clear to define it in the other direction, but for now, that is it. (16-07)
	  
	  String thisStepNT = "";
	  String [] linkageSteps = linkagePointStr.split("\\|");
	    
	  if( linkageSteps == null || linkageSteps.length == 0 ){
		  String detail = " Bad linkagePointStr passed to collectTopology4LinkagePoint(): [" + linkagePointStr + "] ";
		  logline.add("emsg", detail);
		  aaiLogger.info(logline, false, "AAI_6125");
		  throw new AAIException("AAI_6125", detail); 
	  }
	  
	  for( int i=(linkageSteps.length - 1); i >= 0; i-- ){
		  thisStepNT = linkageSteps[i];
		  check4EdgeRule(lastStepNT, thisStepNT, dbMaps);
		  thisMap.put(thisGuysTrail, thisStepNT);
		  thisGuysTrail = thisGuysTrail + "|" + thisStepNT;
		  lastStepNT = thisStepNT;
	  }
	  
	  return thisMap;
	  
	} // End of collectTopology4LinkagePoint()
	

   
	/**
	 * Gets the next step elements from set.
	 *
	 * @param constrElemSetVtx the constr elem set vtx
	 * @return Hash of the set of model-elements this set represents
	 * @throws AAIException the AAI exception
	 */
	public static HashMap <String,Object> getNextStepElementsFromSet( TitanVertex constrElemSetVtx )   
				  throws AAIException {
		// Take a constrained-element-set and figure out the total set of all the possible elements that it
		// represents and return them as a Hash.
	  
		HashMap <String,Object> retElementHash = new HashMap<String, Object>();
	
		if( constrElemSetVtx == null ){
			  String msg = " getNextStepElementsFromSet() called with null constrElemSetVtx ";
			  throw new AAIException("AAI_6125", msg);
		}
	
		String constrNodeType = constrElemSetVtx.<String>property("aai-node-type").orElse(null);
		String constrElemSetUuid = constrElemSetVtx.<String>property("constrained-element-set-uuid").orElse(null);
		if( constrNodeType == null || !constrNodeType.equals("constrained-element-set") ){
			  String msg = " getNextStepElementsFromSet() called with wrong type model: [" + constrNodeType + "]. ";
			  throw new AAIException("AAI_6125", msg);
		}
		
		ArrayList <TitanVertex>  choiceSetVertArray = new ArrayList<TitanVertex>();
		
		Iterable <?> choiceSetVerts = constrElemSetVtx.query().direction(Direction.OUT).labels("uses").vertices();
		Iterator <?> vertI = choiceSetVerts.iterator();
		int setCount = 0;
		while( vertI != null && vertI.hasNext() ){
			TitanVertex choiceSetVertex = (TitanVertex) vertI.next();
			String constrSetType = choiceSetVertex.<String>property("aai-node-type").orElse(null);
			if( constrSetType != null && constrSetType.equals("element-choice-set") ){
				choiceSetVertArray.add(choiceSetVertex);
				setCount++;
			}
		}
			
		if( setCount == 0 ){
			  String msg = "No element-choice-set found under constrained-element-set-uuid = " + constrElemSetUuid;
			  throw new AAIException("AAI_6132", msg);
		}
		
		// Loop through each choice-set and grab the model-elements
		for( int i = 0; i < setCount; i++ ){
			Iterable <?> modelElemVerts = (choiceSetVertArray.get(i)).query().direction(Direction.OUT).labels("has").vertices();
			Iterator <?> mVertI = modelElemVerts.iterator();
			int elCount = 0;
			while( mVertI != null && mVertI.hasNext() ){
				TitanVertex tmpElVertex = (TitanVertex) mVertI.next();
				String elNodeType = tmpElVertex.<String>property("aai-node-type").orElse(null);
				if( elNodeType != null && elNodeType.equals("model-element") ){
					String tmpVid = tmpElVertex.id().toString();
					retElementHash.put(tmpVid, tmpElVertex);
					elCount++;
				}
				else {
					// unsupported node type found for this choice-set
					String msg = "Unsupported nodeType (" + elNodeType 
							+ ") found under choice-set under constrained-element-set-uuid = " + constrElemSetUuid;
					throw new AAIException("AAI_6132", msg);
				}
			}
				
			if( elCount == 0 ){
				  String msg = "No model-elements found in choice-set under constrained-element-set-uuid = " + constrElemSetUuid;
				  throw new AAIException("AAI_6132", msg);
			}
			
		}
	  return retElementHash;
	  
	} // End of getNextStepElementsFromSet()
	
	
	
	/**
	 * Gen topo map 4 named Q.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param queryVertex the query vertex
	 * @param namedQueryUuid the named query uuid
	 * @return MultiMap of valid next steps for each potential query-element
	 * @throws AAIException the AAI exception
	 */
	public static Multimap<String, String> genTopoMap4NamedQ( String transId, String fromAppId, 
			TitanTransaction graph, TitanVertex queryVertex, String namedQueryUuid )   
				  throws AAIException {
	
	  LogLine logline = new LogLine();
	  logline.init("aaidbgen", transId, fromAppId, "genTopoMap4NamedQ");
	  
	  if( graph == null ){
		  String emsg = "null graph object passed to genTopoMap4NamedQ()\n";
		  logline.add("emsg", emsg);         	 
		  aaiLogger.info(logline, false, "AAI_6101");
		  throw new AAIException("AAI_6101", emsg); 
	  }
	  
	  if( queryVertex == null ){
		  String msg = " null queryVertex passed to genTopoMap4NamedQ() ";
		  logline.add("msg", msg);
		  aaiLogger.info(logline, false, "AAI_6125");
		  throw new AAIException("AAI_6125", msg);
	  }
	  
	  Multimap <String, String> initialEmptyMap = ArrayListMultimap.create();
	  ArrayList <String> vidsTraversed = new ArrayList <String> ();
	  
	  TitanVertex firstElementVertex = null;
	  Iterable <?> verts = queryVertex.query().direction(Direction.OUT).labels("startsWith").vertices();
	  Iterator <?> vertI = verts.iterator();
	  int elCount = 0;
	  while( vertI != null && vertI.hasNext() ){
		  elCount++;
		  firstElementVertex = (TitanVertex) vertI.next();
	  }
		
	  if( elCount > 1 ){
		  String msg = "Illegal query defined: More than one first element defined for = " + namedQueryUuid;
		  logline.add("msg", msg);
		  aaiLogger.info(logline, false, "AAI_6133");
		  throw new AAIException("AAI_6133", msg);
	  }
	  
	  if( firstElementVertex == null ){
		  String msg = "Could not find first query element = " + namedQueryUuid;
		  logline.add("msg", msg);
		  aaiLogger.info(logline, false, "AAI_6144");
		  throw new AAIException("AAI_6114", msg);
	  }
	
	  TitanVertex modVtx = getModelThatElementRepresents( firstElementVertex, "" );
	  String modelType = getModelType( modVtx, "" );	  
	  if( ! modelType.equals("widget") ){
		  String msg = "Bad Named Query Definition: First element must correspond to a widget type model.  Named Query UUID = " 
				  + namedQueryUuid;
		  logline.add("msg", msg);
		  aaiLogger.info(logline, false, "AAI_6133");
		  throw new AAIException("AAI_6133", msg);
	  }
	  
	  Multimap <String, String> collectedMap = collectTopology4NamedQ( transId, fromAppId, graph, 
				firstElementVertex, "", 
				initialEmptyMap, vidsTraversed, 0);
	  
	  return collectedMap;
	  
	} // End of genTopoMap4NamedQ()

	
    
	/**
	 * Collect topology 4 named Q.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param thisLevelVertex the this level vertex
	 * @param levelCounter the level counter
	 * @return resultSet
	 * @throws AAIException the AAI exception
	 */
	public static Multimap<String, String> collectTopology4NamedQ( String transId, String fromAppId, TitanTransaction graph,
		  TitanVertex thisLevelVertex, String incomingTrail, 
		  Multimap <String,String> currentMap, ArrayList <String> vidsTraversed, int levelCounter )   
				  throws AAIException {
	
	  levelCounter++;
	  LogLine logline = new LogLine();
	  logline.init("aaidbgen", transId, fromAppId, "collectTopology4NamedQ");
	  
	  Multimap <String, String> thisMap = ArrayListMultimap.create();
	  thisMap.putAll(currentMap);
	 
	  String thisVid = thisLevelVertex.id().toString();
	  if( levelCounter > maxLevels ) {
		  String emsg = "collectModelStructure() has looped across more levels than allowed: " + maxLevels + ". ";
		  logline.add("emsg", emsg);         	 
		  aaiLogger.info(logline, false, "AAI_6125");
		  throw new AAIException("AAI_6125", emsg); 
	  }
	  String thisGuysTrail = "";
	  
	  // find out what widget-model (and thereby what aai-node-type) this element represents
	  String thisElementNodeType = getElementWidgetType( thisLevelVertex, incomingTrail );
	  
	  if( incomingTrail.equals("") ){
		  // This is the first one
		  thisGuysTrail = thisElementNodeType;
	  }
	  else {
		  thisGuysTrail = incomingTrail + "|" + thisElementNodeType;
	  }
	  vidsTraversed.add(thisVid);
	 
	  // Look at the elements pointed to at this level and add on their data
	  Iterable <?> verts = thisLevelVertex.query().direction(Direction.OUT).labels("connectsTo").vertices();
	  Iterator <?> vertI = verts.iterator();
	  
	   while( vertI != null && vertI.hasNext() ){
		  TitanVertex tmpVert = (TitanVertex) vertI.next();
		  String tmpVid = tmpVert.id().toString();
		  String tmpElNT = getElementWidgetType( tmpVert, thisGuysTrail );
		  thisMap.put(thisGuysTrail, tmpElNT);
		  if( !vidsTraversed.contains(tmpVid) ){
			  // This is one we would like to use - so we'll recursively get it's result set to add to ours
			  Multimap<String, String> tmpMap = collectTopology4NamedQ( transId, fromAppId, graph, 
						tmpVert, thisGuysTrail, 
						currentMap, vidsTraversed, levelCounter);
			  thisMap.putAll(tmpMap);
		  }
	  }
	  
	  return thisMap;
	  
	} // End of collectTopology4NamedQ()


	/**
	 * Gets the model that element represents.
	 *
	 * @param elementVtx the element vtx
	 * @param elementTrail the element trail
	 * @return the model that element represents
	 * @throws AAIException the AAI exception
	 */
	public static TitanVertex getModelThatElementRepresents( TitanVertex elementVtx, String elementTrail )
		throws AAIException {
		  
		  // Get the model that an element represents
		  TitanVertex modVtx = null;
		  Iterable <?> mverts = elementVtx.query().direction(Direction.OUT).labels("isA").vertices();
		  Iterator <?> mvertI = mverts.iterator();
		  int modCount = 0;
		  while( mvertI != null && mvertI.hasNext() ){
			  modCount++;
			  modVtx = (TitanVertex) mvertI.next();
		  }
			
		  if( modCount > 1 ){
			  String msg = "Illegal element defined: More than one model pointed to by a single element at [" +
					  elementTrail + "].";
			  throw new AAIException("AAI_6125", msg);
		  }
		  
		  if( modVtx == null ){
			  String msg = "Bad model or named-query definition: Could not find model for element. ";
			  if( !elementTrail.equals("") ){
				  msg = "Bad model or named-query definition: Could not find model for element at [" + elementTrail + "].";
			  }
			  throw new AAIException("AAI_6132", msg);
		  }
		    
		  return modVtx; 
		
	}// getModelThatElementRepresents()
	

	/**
	 * Gets the model type.
	 *
	 * @param modelVtx the model vtx
	 * @param elementTrail the element trail
	 * @return the model type
	 * @throws AAIException the AAI exception
	 */
	public static String getModelType( TitanVertex modelVtx, String elementTrail )
		throws AAIException {
		  
		// Get the model-type for a vertex that should be pointing to a model.
		
		if( modelVtx == null ){
			 String msg = " null modelVtx passed to getModelType() ";
			 throw new AAIException("AAI_6114", msg);
		}		  
		 
		String modelType = modelVtx.<String>property("model-type").orElse(null);
		if( (modelType == null) || modelType.equals("") ){
			String msg = "Could not find model-type for model pointed to by element at [" + elementTrail + "].";
			throw new AAIException("AAI_6114", msg);
		}
		
		if( !modelType.equals("widget") && !modelType.equals("resource") && !modelType.equals("service") ){
			String msg = "Unrecognized model-type, [" + modelType + "] for model pointed to by element at [" + elementTrail + "].";
			throw new AAIException("AAI_6132", msg);
		}
		  
		return modelType; 
		
	}// getModelType()
		

	/**
	 * Gets the element step name.
	 *
	 * @param elementVtx the element vtx
	 * @param elementTrail the element trail
	 * @param dbMaps the db maps
	 * @return the element step name
	 * @throws AAIException the AAI exception
	 */
	public static String getElementStepName( TitanVertex elementVtx, String elementTrail, DbMaps dbMaps)
		throws AAIException {
		  
		// Get the "step name"  for either a model-element or a named-query-element.
		// Step names look like this for widget-models:   "aai-node-type"
		// Step names look like this for resource/service models: "aai-node-type,model-id,model-version"
		// NOTE -- if the element points to a resource or service model, then we'll return the
		//        widget-type of the first element (crown widget) for that model.
		String thisElementNodeType = "?";
		
		TitanVertex modVtx = getModelThatElementRepresents( elementVtx, elementTrail );
		String modelType = getModelType( modVtx, elementTrail );
		
		if( modelType == null ){
			String msg = " Null modelType passed to getElementWidgetType().  elementTrail = [" + elementTrail + "].";
			throw new AAIException("AAI_6114", msg);
		}
		  
		if( modelType.equals("widget") ){
			// NOTE: for models that have model-type = "widget", their "model-name" maps directly to aai-node-type 
			thisElementNodeType = modVtx.<String>property("model-name").orElse(null);
			if( (thisElementNodeType == null) || thisElementNodeType.equals("") ){
				String msg = "Could not find model-name for the widget model pointed to by element at [" + elementTrail + "].";
				throw new AAIException("AAI_6132", msg);
			}
			return thisElementNodeType;
		}
		else if( modelType.equals("resource") || modelType.equals("service") ){
			String modelId = modVtx.<String>property("model-id").orElse(null); 
			String modelVer = modVtx.<String>property("model-version").orElse(null);
			TitanVertex relatedTopElementModelVtx = getTopElementForSvcOrResModel( modVtx );
			TitanVertex relatedModelVtx = getModelThatElementRepresents( relatedTopElementModelVtx, elementTrail );
			thisElementNodeType = relatedModelVtx.<String>property("model-name").orElse(null);
			
			if( (thisElementNodeType == null) || thisElementNodeType.equals("") ){
				String msg = "Could not find model-name for the widget model pointed to by element at [" + elementTrail + "].";
				throw new AAIException("AAI_6132", msg);
			}
			
			String stepName = "";
			if( nodeTypeSupportsPersona(thisElementNodeType, dbMaps) ){
				// This nodeType that this resource or service model refers to does support persona-related fields, so
				// we will use model-id and model-version as part of the step name.
				stepName = thisElementNodeType + "," + modelId + "," + modelVer;
			}
			else {
				stepName = thisElementNodeType;
			}
			return stepName;
		}
		else {
			String msg = " Unrecognized model-type = [" + modelType + "] pointed to by element at [" + elementTrail + "].";
			throw new AAIException("AAI_6132", msg);
		}
		  
	}// getElementStepName()
	
	
	
	/**
	 * Node type supports persona.
	 *
	 * @param nodeType the node type
	 * @param dbMaps the db maps
	 * @return the boolean
	 * @throws AAIException the AAI exception
	 */
	public static Boolean nodeTypeSupportsPersona(String nodeType, DbMaps dbMaps)
			throws AAIException {
		
		if( nodeType == null || nodeType.equals("") ){
			return false;
		}
		
		// Return true if this type of node supports the properties: "persona-model-id" and "persona-model-version"
		if( ! dbMaps.NodeProps.containsKey(nodeType) ){
			String emsg = " Unrecognized nodeType [" + nodeType + "]\n";
			throw new AAIException("AAI_6115", emsg); 
		}  
		
		Collection <String> props4ThisNT = dbMaps.NodeProps.get(nodeType);
		if( !props4ThisNT.contains("persona-model-id") || !props4ThisNT.contains("persona-model-version") ){
			return false;
		}
		else {
			return true;
		}
		
	}// nodeTypeSupportsPersona()
	
	
	/**
	 * Gets the element widget type.
	 *
	 * @param elementVtx the element vtx
	 * @param elementTrail the element trail
	 * @return the element widget type
	 * @throws AAIException the AAI exception
	 */
	public static String getElementWidgetType( TitanVertex elementVtx, String elementTrail )
		throws AAIException {
		  
		// Get the associated node-type for the model pointed to by either a 
		//     model-element or a named-query-element.
		// NOTE -- if the element points to a resource or service model, then we'll return the
		//        widget-type of the first element (crown widget) for that model.
		TitanVertex modVtx = getModelThatElementRepresents( elementVtx, elementTrail );
		String thisElementNodeType = getModelWidgetType( modVtx, elementTrail );
		return thisElementNodeType;
			
	}// End  getElementWidgetType()
	
	
	/**
	 * Gets the mod name ver id.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param modId the mod id
	 * @param modVersion the mod version
	 * @return the mod name ver id
	 * @throws AAIException the AAI exception
	 */
	public static String getModNameVerId(String transId, String fromAppId, TitanTransaction graph,
			String modId, String modVersion)
		throws AAIException {
		
		String modelNameVersionId = "";
		
		// Given a "model-id" and "model-version", find the unique key ("model-name-version-id") for this model
		if( modId == null || modId.equals("") || modVersion == null || modVersion.equals("") ){
			String emsg = " Bad model-id or model-version passed to getModNameVerId(): [" 
					+ modId + "], [" + modVersion + "]\n";
			throw new AAIException("AAI_6118", emsg); 
		}
		
		Iterable <?> modVerts = null;
		modVerts = graph.query().has("aai-node-type","model").has("model-id",modId).has("model-version",modVersion).vertices();
		if( modVerts == null ){
			String emsg = "Model record(s) could not be found for model data passed.  (model-id = [" + modId +
					"], model-version = [" + modVersion + "]\n";
			throw new AAIException("AAI_6114", emsg); 
		}
		else { 
			int count = 0;
			Iterator <?> modVertsIter = modVerts.iterator();
			while( modVertsIter.hasNext() ){
				count++;
				TitanVertex tmpModVtx = (TitanVertex) modVertsIter.next();
				modelNameVersionId = tmpModVtx.<String>property("model-name-version-id").orElse(null);
				if( count > 1 ){
					String emsg = "More than one model-name-version-id found for passed params to getModNameVerId(): [" 
							+ modId + "], [" + modVersion + "]\n";
					throw new AAIException("AAI_6132", emsg); 
				}
			}
		}
		
		if( modelNameVersionId.equals("") ){
			String emsg = "Could not find a model-name-version-id for passed params to getModNameVerId(): [" 
					+ modId + "], [" + modVersion + "]\n";
			throw new AAIException("AAI_6132", emsg); 
		}
		
		return modelNameVersionId;
	}// End getModNameVerId()

	
	/**
	 * Gets the model using UUID.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param modelNameVersionId the model name version id
	 * @return the model using UUID
	 * @throws AAIException the AAI exception
	 */
	public static TitanVertex getModelUsingUUID( String transId, String fromAppId, TitanTransaction graph,
			String modelNameVersionId )
		throws AAIException {
		
		// Given a "model-name-version-id", find the model vertex that this uniquely maps to
		if( modelNameVersionId == null || modelNameVersionId.equals("")  ){
			String emsg = " Bad modelNameVersionId passed to getModNameVerId(): [" 
					+ modelNameVersionId + "]\n";
			throw new AAIException("AAI_6118", emsg); 
		}
		
		TitanVertex modelVtx = null;
		int count = 0;
		Iterable <?> modVerts = null;
		modVerts = graph.query().has("aai-node-type","model").has("model-name-version-id",modelNameVersionId).vertices();
		if( modVerts == null ){
			String emsg = "Model record(s) could not be found for model data passed.  model-name-version-id = [" + modelNameVersionId +
					"]\n";
			throw new AAIException("AAI_6114", emsg); 
		}
		else { 
			Iterator <?> modVertsIter = modVerts.iterator();
			while( modVertsIter.hasNext() ){
				count++;
				modelVtx = (TitanVertex) modVertsIter.next();
				if( count > 1 ){
					String emsg = "More than one model record found for model-name-version-id = [" 
							+ modelNameVersionId + "]\n";
					throw new AAIException("AAI_6132", emsg); 
				}
			}
		}
		
		if( count == 0 ){
			String emsg = "No Model record found for model-name-version-id = [" 
					+ modelNameVersionId + "]\n";
			throw new AAIException("AAI_6132", emsg); 
		}
		
		return modelVtx;
	}// End getModelUsingUUID()
	
	
	/**
	 * Gets the model using persona info.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param personaModId the persona mod id
	 * @param personaModVer the persona mod ver
	 * @return the model using persona info
	 * @throws AAIException the AAI exception
	 */
	public static TitanVertex getModelUsingPersonaInfo( String transId, String fromAppId, TitanTransaction graph,
			String personaModId, String personaModVer )
		throws AAIException {
		
		// Given a model-id and model-version, find the model vertex that this uniquely maps to
		if( personaModId == null || personaModId.equals("")  ){
			String emsg = " Bad personaModId passed to getModelUsingPersonaInfo(): [" 
					+ personaModId + "]\n";
			throw new AAIException("AAI_6118", emsg); 
		}
		if( personaModVer == null || personaModVer.equals("")  ){
			String emsg = " Bad personaModVer passed to getModelUsingPersonaInfo(): [" 
					+ personaModVer + "]\n";
			throw new AAIException("AAI_6118", emsg); 
		}
		
		TitanVertex modelVtx = null;
		int modCount = 0;
		Iterable <?> modVerts = null;
		modVerts = graph.query().has("aai-node-type","model").has("model-id",personaModId).has("model-version",personaModVer).vertices();
		if( modVerts == null ){
			String emsg = "Model record(s) could not be found for model data passed.  persona-model-id = [" + 
					personaModId + "], persona-model-version = [" + personaModVer + "]\n";
			throw new AAIException("AAI_6114", emsg); 
		}
		else { 
			Iterator <?> modVertsIter = modVerts.iterator();
			while( modVertsIter.hasNext() ){
				modCount++;
				modelVtx = (TitanVertex) modVertsIter.next();
				if( modCount > 1 ){
					String emsg = "More than one model record found for persona-model-id = [" + 
							personaModId + "], persona-model-version = [" + personaModVer + "]. ";
					throw new AAIException("AAI_6132", emsg); 
				}
			}
		}
		
		if( modCount == 0 ){
			String emsg = "No Model record found for persona-model-id = [" + 
				personaModId + "], persona-model-version = [" + personaModVer + "]. ";
			throw new AAIException("AAI_6132", emsg); 
		}
		
		return modelVtx;
	}// End getModelUsingPersonaInfo()
	
	
	
	/**
	 * Gets the models using name.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param modelName the model name
	 * @return the models using name
	 * @throws AAIException the AAI exception
	 */
	public static ArrayList <TitanVertex> getModelsUsingName( String transId, String fromAppId, TitanTransaction graph,
			String modelName )
		throws AAIException {
		
		// Given a "model-name", find the model vertices that this maps to
		if( modelName == null || modelName.equals("")  ){
			String emsg = " Bad modelName passed to getModelsUsingName(): [" 
					+ modelName + "]\n";
			throw new AAIException("AAI_6118", emsg); 
		}
		
		ArrayList <TitanVertex> retVtxArr = new ArrayList <TitanVertex> ();
		Iterable <?> modVerts = null;
		modVerts = graph.query().has("aai-node-type","model").has("model-name",modelName).vertices();
		if( modVerts == null ){
			String emsg = "Model record(s) could not be found for model data passed.  model-name = [" + 
					modelName + "]\n";
			throw new AAIException("AAI_6132", emsg); 
		}
		else { 
			Iterator <?> modVertsIter = modVerts.iterator();
			while( modVertsIter.hasNext() ){
				TitanVertex tmpModelVtx = (TitanVertex) modVertsIter.next();
				retVtxArr.add(tmpModelVtx);
			}
		}
		
		return retVtxArr;
		
	}// End getModelsUsingName()
	
	
	/**
	 * Gets the model uuids using name.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param modelName the model name
	 * @return the model uuids using name
	 * @throws AAIException the AAI exception
	 */
	public static ArrayList <String> getModelUuidsUsingName( String transId, String fromAppId, TitanTransaction graph,
			String modelName )
		throws AAIException {
		
		// Given a modelName find the models maps to
		if( modelName == null || modelName.equals("")  ){
			String emsg = " Bad modelName passed to getModelUuidsUsingName(): [" 
					+ modelName + "]\n";
			throw new AAIException("AAI_6118", emsg); 
		}
		
		ArrayList <String> retArr = new ArrayList <String> ();
		
		Iterable <?> modVerts = null;
		modVerts = graph.query().has("aai-node-type","model").has("model-name",modelName).vertices();
		if( modVerts == null ){
			String emsg = "Model record(s) could not be found for model data passed.  model-name = [" + 
					modelName + "]\n";
			throw new AAIException("AAI_6114", emsg); 
		}
		else { 
			Iterator <?> modVertsIter = modVerts.iterator();
			while( modVertsIter.hasNext() ){
				TitanVertex modelVtx = (TitanVertex) modVertsIter.next();
				String tmpUuid = modelVtx.<String>property("model-name-version-id").orElse(null);
				if( (tmpUuid != null) && !tmpUuid.equals("") && !retArr.contains(tmpUuid) ){
					retArr.add(tmpUuid);
				}
			}
		}
		
		if( retArr.isEmpty() ){
			String emsg = "No Model record found for model-name = [" 
					+ modelName + "]\n";
			throw new AAIException("AAI_6132", emsg); 
		}
		
		return retArr;
	}// End getModelUuidsUsingName()
	
	
	/**
	 * Gets the model top widget type.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param modelNameVersionId the model name version id
	 * @param modelId the model id
	 * @param modelName the model name
	 * @return the model top widget type
	 * @throws AAIException the AAI exception
	 */
	public static String getModelTopWidgetType( String transId, String fromAppId, TitanTransaction graph,
			String modelNameVersionId, String modelId, String modelName )
		throws AAIException {
		
		// Could be given a model's key info, OR, just a (non-unique) modelId.  
		//     Either way, they should only map to one single "top" node-type for the first element. 
		
		String nodeType = "?";
		Iterable <?> modVerts = null;
		if( modelNameVersionId != null && !modelNameVersionId.equals("") ){
			modVerts = graph.query().has("aai-node-type","model").has("model-name-version-id",modelNameVersionId).vertices();
		}
		else if( modelId != null && !modelId.equals("") ){
			modVerts = graph.query().has("aai-node-type","model").has("model-id",modelId).vertices();
		}
		else if( modelName != null && !modelName.equals("") ){
			modVerts = graph.query().has("aai-node-type","model").has("model-name",modelName).vertices();
		}
		else {
			String msg = "Neither modelNameVersionId, modelId, nor modelName passed to: getModelTopWidgetType() ";
			throw new AAIException("AAI_6120", msg);
		}
		
		if( modVerts == null ){
			String emsg = "Model record(s) could not be found for model data passed.  (modelId = [" + modelId +
					"], modelNameVersionId = [" + modelNameVersionId + "]\n";
			throw new AAIException("AAI_6114", emsg); 
		}
		else { 
			String lastNT = "";
			Iterator <?> modVertsIter = modVerts.iterator();
			while( modVertsIter.hasNext() ){
				TitanVertex tmpModVtx = (TitanVertex) modVertsIter.next();
				String tmpNT = getModelWidgetType( tmpModVtx, "" );
				if( !lastNT.equals("") ){
					if( !lastNT.equals(tmpNT) ){
						String emsg = "Different top-node-types (" + tmpNT + ", " + lastNT 
								+ ") found for model data passed.  (" +
								" modelNameVersionId = [" + modelNameVersionId + 
								"], modelId = [" + modelId +
								"], modelName = [" + modelName +
								"])\n";
						throw new AAIException("AAI_6114", emsg); 
					}
				}
				lastNT = tmpNT;
				nodeType = tmpNT;
			}
		}
		
		return nodeType;
		
	}// End getModelTopWidgetType()
	
			
	/**
	 * Gets the model widget type.
	 *
	 * @param modVtx the mod vtx
	 * @param elementTrail the element trail
	 * @return the model widget type
	 * @throws AAIException the AAI exception
	 */
	public static String getModelWidgetType( TitanVertex modVtx, String elementTrail )
				throws AAIException {
		// Get the associated node-type for a model.
		// NOTE -- if the element points to a resource or service model, then we'll return the
		//        widget-type of the first element (crown widget) for that model.	 
		
		String modelType = getModelType( modVtx, elementTrail );
		if( modelType == null ){
			String msg = " Null modelType passed to getElementWidgetType().  elementTrail = [" + elementTrail + "].";
			throw new AAIException("AAI_6114", msg);
		}
		  
		String thisElementNodeType = "?";
		if( modelType.equals("widget") ){
			// NOTE: for models that have model-type = "widget", their "model-name" maps directly to aai-node-type 
			thisElementNodeType = modVtx.<String>property("model-name").orElse(null);
			if( (thisElementNodeType == null) || thisElementNodeType.equals("") ){
				String msg = "Could not find model-name for the widget model pointed to by element at [" + elementTrail + "].";
				throw new AAIException("AAI_6132", msg);
			}
			
		}
		else if( modelType.equals("resource") || modelType.equals("service") ){
			TitanVertex relatedTopElementModelVtx = getTopElementForSvcOrResModel( modVtx );
			TitanVertex relatedModelVtx = getModelThatElementRepresents( relatedTopElementModelVtx, elementTrail );
			thisElementNodeType = relatedModelVtx.<String>property("model-name").orElse(null);
			if( (thisElementNodeType == null) || thisElementNodeType.equals("") ){
				String msg = "Could not find model-name for the widget model pointed to by element at [" + elementTrail + "].";
				throw new AAIException("AAI_6132", msg);
			}
		}
		else {
			String msg = " Unrecognized model-type = [" + modelType + "] pointed to by element at [" + elementTrail + "].";
			throw new AAIException("AAI_6132", msg);
		}
		 
		return thisElementNodeType;
		
	}// getModelWidgetType()
	
	
	/**
	 * Validate model.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param modelNameVersionId the model name version id
	 * @param apiVersion the api version
	 * @throws AAIException the AAI exception
	 */
	public static void validateModel(String transId, String fromAppId, TitanTransaction graph, String modelNameVersionId, String apiVersion ) 
				throws AAIException{
	
		// Note - this will throw an exception if the model either can't be found, or if 
		//     we can't figure out its topology map.
		HashMap<String, Object> propHash = new HashMap<String, Object>();
		propHash.put( "model-name-version-id", modelNameVersionId );
		
		DbMaps dbMaps = IngestModelMoxyOxm.dbMapsContainer.get(AAIConfig.get(AAIConstants.AAI_DEFAULT_API_VERSION_PROP));
  
		TitanVertex modelVtx = DbMeth.getUniqueNode( transId, fromAppId, graph, "model", propHash, null, apiVersion);
		if( modelVtx == null ){
			String msg = " Could not find model with modelNameVersionId = [" + modelNameVersionId + "].";
			throw new AAIException("AAI_6114", msg);
		}
		else {
			Multimap<String, String> topoMap = ModelBasedProcessing.genTopoMap4Model( transId, fromAppId, graph,
					modelVtx, modelNameVersionId, dbMaps );
			//String msg = " model [" + modelNameVersionId + "] topo multiMap looks like: \n[" + topoMap + "]";
			//System.out.println("INFO --  " + msg );
		}
		return;
		
	}// End validateModel()
	
	
	/**
	 * Validate named query.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param graph the graph
	 * @param namedQueryUuid the named query uuid
	 * @param apiVersion the api version
	 * @throws AAIException the AAI exception
	 */
	public static void validateNamedQuery(String transId, String fromAppId, TitanTransaction graph, String namedQueryUuid, String apiVersion ) 
				throws AAIException{
	
		// Note - this will throw an exception if the named query either can't be found, or if 
		//     we can't figure out its topology map.
		HashMap<String, Object> propHash = new HashMap<String, Object>();
		propHash.put( "named-query-uuid", namedQueryUuid );
		
		TitanVertex modelVtx = DbMeth.getUniqueNode( transId, fromAppId, graph, "model", propHash, null, apiVersion);
		if( modelVtx == null ){
			String msg = " Could not find model with namedQueryUuid = [" + namedQueryUuid + "].";
			throw new AAIException("AAI_6114", msg);
		}
		else {
			Multimap<String, String> topoMap = ModelBasedProcessing.genTopoMap4NamedQ( "junkTransId", "junkFromAppId", 
					graph, modelVtx, namedQueryUuid );
			//String msg = " namedQuery [" + namedQueryUuid + "] topo multiMap looks like: \n[" + topoMap + "]";
			//System.out.println("INFO --  " + msg );
		}
		return;
		
	}// End validateNamedQuery()
	

	public static ArrayList <String> makeSureItsAnArrayList( Object objVal ){
		// We're sometimes getting a String back on db properties that should be ArrayList<String>
		// Need to translate them into ArrayLists sometimes...
		
		ArrayList <String> retArrList = new ArrayList<String>();
		if( objVal != null ){
			String className = objVal.getClass().getSimpleName();
			if( className.equals("ArrayList") ){
				retArrList = (ArrayList<String>)objVal;
			}
			else if( className.equals("String") ){
				String listString = (String) objVal;
				listString = listString.replace(" ",  "");
				listString = listString.replace("[",  "");
				listString = listString.replace("]",  "");
				String [] pieces = listString.split(",");
				if( pieces != null && pieces.length > 0 ){
					for( int i = 0; i < pieces.length; i++ ){
						retArrList.add(pieces[i]);
					}
				}
			}
		}
		
		return retArrList;
	}

	
	  
	/**
	 * Show result set.
	 *
	 * @param resSet the res set
	 * @param levelCount the level count
	 */
	public static void showResultSet( ResultSet resSet, int levelCount ) {
		
		  levelCount++;
		  for( int i= 1; i <= levelCount; i++ ){
			  System.out.print("-");
		  }
		  if( resSet.vert == null ){
			  return;
		  }
		  String nt = resSet.vert.<String>property("aai-node-type").orElse(null);
		  System.out.print( "[" + nt + "] ");
		  String propsStr = "";
		  
		  //propsStr = propsStr + " newDataDelFlag = " + resSet.getNewDataDelFlag() + ", trail = " + resSet.getLocationInModelSubGraph();
		  //propsStr = propsStr + "limitDesc = [" + resSet.getPropertyLimitDesc() + "]";
		  propsStr = propsStr + " trail = " + resSet.getLocationInModelSubGraph();
		  
		  HashMap <String,Object> overrideHash = resSet.getPropertyOverRideHash();
		  if( overrideHash != null  &&  !overrideHash.isEmpty() ){
			  for( Map.Entry<String, Object> entry : overrideHash.entrySet() ){
				  String propName = entry.getKey();
				  Object propVal = entry.getValue();
				  propsStr = propsStr + " [" + propName + " = " + propVal + "]";
			  }
		  }
		  else {
			  Iterator<VertexProperty<Object>> pI = resSet.vert.properties();
			  while( pI.hasNext() ){
				  	VertexProperty<Object> tp = pI.next();
					if( ! tp.key().startsWith("aai") 
							&& ! tp.key().equals("source-of-truth")
							//&& ! tp.key().equals("resource-version")
							&& ! tp.key().startsWith("last-mod")
							)
					{
						propsStr = propsStr + " [" + tp.key() + " = " + tp.value() + "]";
					}
			  }
		  }
		  // Show the "extra" lookup values too
		  HashMap <String,Object> extraPropHash = resSet.getExtraPropertyHash();
		  if( extraPropHash != null && !extraPropHash.isEmpty() ){
			  for( Map.Entry<String, Object> entry : extraPropHash.entrySet() ){
				  String propName = entry.getKey();
				  Object propVal = entry.getValue();
				  propsStr = propsStr + " [" + propName + " = " + propVal.toString() + "]";
			  }
		  }
		  
		  System.out.println( propsStr );
		  
		  if( !resSet.subResultSet.isEmpty() ){
			  ListIterator<ResultSet> listItr = resSet.subResultSet.listIterator();
			  while( listItr.hasNext() ){
				  showResultSet( listItr.next(), levelCount );
			  }
		  }
		  
	  }// end of showResultSet()
	
		  
}

