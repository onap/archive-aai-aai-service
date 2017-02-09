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

import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBException;
import javax.xml.transform.stream.StreamSource;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.eclipse.persistence.dynamic.DynamicEntity;
import org.eclipse.persistence.dynamic.DynamicType;
import org.eclipse.persistence.exceptions.DynamicException;
import org.eclipse.persistence.jaxb.JAXBMarshaller;
import org.eclipse.persistence.jaxb.JAXBUnmarshaller;
import org.eclipse.persistence.jaxb.MarshallerProperties;
import org.eclipse.persistence.jaxb.dynamic.DynamicJAXBContext;
import org.openecomp.aai.dbgen.DbMeth;
import org.openecomp.aai.dbgen.ModelBasedProcessing;
import org.openecomp.aai.dbgen.ResultSet;
import org.openecomp.aai.dbmodel.DbEdgeRules;
import org.openecomp.aai.domain.model.AAIResource;
import org.openecomp.aai.domain.model.AAIResourceKey;
import org.openecomp.aai.domain.model.AAIResourceKeys;
import org.openecomp.aai.domain.model.AAIResources;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.extensions.AAIExtensionMap;
import org.openecomp.aai.ingestModel.DbMaps;
import org.openecomp.aai.ingestModel.IngestModelMoxyOxm;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.util.AAIApiServerURLBase;
import org.openecomp.aai.util.AAIApiVersion;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIConstants;
import org.openecomp.aai.util.PojoUtils;
import org.openecomp.aai.util.RestURL;
import org.openecomp.aai.util.StoreNotificationEvent;

import com.google.common.base.CaseFormat;
import com.thinkaurelius.titan.core.TitanGraphQuery;
import com.thinkaurelius.titan.core.TitanTransaction;
import com.thinkaurelius.titan.core.TitanVertex;

/**
 * Database Mapping class which acts as the middle man between the REST interface objects 
 * for the Search namespace 
 
 */
public class SearchGraph {

	private LogLine logline = new LogLine();
	private final String COMPONENT = "aaidbmap";
	protected AAILogger aaiLogger = new AAILogger(SearchGraph.class.getName());
	
	
	/**
	 * Get the search result based on the includeNodeType and depth provided.
	 *
	 * @param fromAppId the from app id
	 * @param transId the trans id
	 * @param startNodeType the start node type
	 * @param startNodeKeyParams the start node key params
	 * @param includeNodeTypes the include node types
	 * @param depth the depth
	 * @param aaiExtMap the aai ext map
	 * @return Response
	 * @throws AAIException the AAI exception
	 */
	public Response runGenericQuery (String fromAppId, 
			String transId, 
			String startNodeType,
			List <String> startNodeKeyParams,
			List <String> includeNodeTypes,
			final int depth,
			AAIExtensionMap aaiExtMap) throws AAIException {
		DynamicEntity searchResults = null;
		Response response = null;
		boolean success = true;
		TitanTransaction g = null;
		try {			
			g = AAIGraph.getInstance().getGraph().newTransaction();

			logline.init(COMPONENT, transId, fromAppId, "runGenericQuery");

			if( startNodeType == null ){
				String emsg = "null start-node-type passed to the generic query\n";
				logline.add("emsg", emsg);         	 
				throw new AAIException("AAI_6120", emsg); 
			} else {
				logline.add("start-node-type", startNodeType);
			}

			if( startNodeKeyParams == null ){
				String emsg = "no key param passed to the generic query\n";
				logline.add("emsg", emsg);         	 
				throw new AAIException("AAI_6120", emsg); 
			} else {
				logline.add("key", startNodeKeyParams.toString());
			}

			if( includeNodeTypes == null ){
				String emsg = "no include params passed to the generic query\n";
				logline.add("emsg", emsg);         	 
				throw new AAIException("AAI_6120", emsg); 
			} else {
				logline.add("include", includeNodeTypes.toString());
			}

			if (depth > 6) {
				String emsg = "The maximum depth supported by the generic query is 6\n";
				logline.add("emsg", emsg);         	 
				throw new AAIException("AAI_6120", emsg);
			}	else {
				logline.add("depth", depth);
			}

			// Build a hash with keys to uniquely identify the start Node
			HashMap <String,Object> propHash = new HashMap<String, Object>();
			String keyName = null, keyValue = null;

			String keyNameString = "";
			for( String keyData : startNodeKeyParams ){ 
				int colonIndex = keyData.indexOf(":");
				if( colonIndex <= 0 ){
					String emsg = "Bad key param passed in: [" + keyData + "]\n";
					logline.add("emsg", emsg);         	 
					throw new AAIException("AAI_6120", emsg); 
				}
				else {
					keyName = keyData.substring(0, colonIndex);
					keyValue = keyData.substring(colonIndex + 1);
					propHash.put(keyName, keyValue);

					keyNameString += keyName.toLowerCase() + "|";		 		       	
				}
			}

			// there is an issue with service-instance - it is a unique node but still dependent
			// for now lets use getUniqueNode() to get the startNode if startNodeType is service-instance
			TitanVertex startNode = null;
			if (startNodeType.equalsIgnoreCase("service-instance")) {
				Iterable <?> verts = g.query().has(keyName.substring(keyName.indexOf('.') + 1), keyValue).vertices();
				Iterator <?> vertI = verts.iterator();
				if( vertI != null && vertI.hasNext()) {
					// We found a vertex that meets the input criteria. 
					startNode = (TitanVertex) vertI.next();

					if( vertI.hasNext() ){
						// Since this routine is looking for a unique node for the given input values, if  
						// more than one is found - it's a problem.
						String detail = "More than one Node found by getUniqueNode for params: " + startNodeKeyParams.toString() + "\n";
						throw new AAIException("AAI_6112", detail); 
					}
				} 
				else {
					// No Vertex was found for this key - throw a not-found exception
					String msg = "No Node of type " + "service-instance" + " found for properties: " + startNodeKeyParams.toString();
					logline.add("msg", msg);
					throw new AAIException("AAI_6114", msg);
				}

			} else {
				// Look for the start node based on the key params
				startNode = DbMeth.getUniqueNodeWithDepParams(transId, 
						fromAppId, 
						g, 
						startNodeType, 
						propHash,
						aaiExtMap.getApiVersion()); 
			}
			if( startNode == null ){
				String emsg = "No Node of type " + 
						startNodeType + 
						" found for properties: " + 
						startNodeKeyParams.toString();
				logline.add("emsg", emsg);         	 
				throw new AAIException("AAI_6114", emsg); 
			}	

			
			Collection <Vertex> ver = new HashSet <Vertex> ();

			if (includeNodeTypes.contains(startNodeType) || depth == 0 || includeNodeTypes.contains("all") )
				ver.add(startNode);

			if (depth != 0) {
				// Now look for a node of includeNodeType within a given depth
				startNode.graph().traversal().withSideEffect("x", ver).V(startNode)
				.repeat(__.both().store("x")).times(depth).iterate();
			}						       

			if( ver.isEmpty()){
				logline.add("msg", "No nodes found - apipe was null/empty");
			}
			else {			        		
				AAIResources aaiResources = org.openecomp.aai.ingestModel.IngestModelMoxyOxm.aaiResourceContainer
						.get(aaiExtMap.getApiVersion());
				DynamicJAXBContext jaxbContext = aaiResources.getJaxbContext();	 
				if (aaiExtMap.getApiVersion().equals("v2")) { 
					searchResults = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org.SearchResults");
				} else { 
					searchResults = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org." + 
							aaiExtMap.getApiVersion() + 
							".SearchResults");
				}
				ArrayList <DynamicEntity> resultDataList = new ArrayList<DynamicEntity>();
				for (Vertex thisNode: ver){
					String nodeType = thisNode.<String>property("aai-node-type").orElse(null);
					if (depth == 0 || includeNodeTypes.contains(nodeType) || includeNodeTypes.contains("all")) {
						// DbMeth.showPropertiesForNode( transId, fromAppId, thisNode );
						String thisNodeURL = RestURL.getSearchUrl(g, (TitanVertex)thisNode, aaiExtMap.getApiVersion());
						DynamicEntity resultData = null;
						if (aaiExtMap.getApiVersion().equals("v2")) { 
							resultData = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org.ResultData");
						} else { 
							resultData = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org." + 
									aaiExtMap.getApiVersion() + 
									".ResultData");
						}

						resultData.set("resourceType", nodeType);
						resultData.set("resourceLink", thisNodeURL);
						resultDataList.add(resultData);
					}
				}
				searchResults.set("resultData", resultDataList);
				response = getResponseFromDynamicEntity(searchResults, jaxbContext, aaiExtMap);

				logline.add("msg", ver.size() + " node(s) traversed, " + resultDataList.size() + " found");
			}		        			      
			success = true;
		} catch (AAIException e) { 
			success = false;
			aaiLogger.info(logline, false, e.getErrorObject().getErrorCodeString());
			throw e;
		} catch (Exception e) {
			success = false;
			aaiLogger.info(logline, false, "AAI_5105");
			throw new AAIException("AAI_5105", e);
		} finally {
			if (g != null) {
				if (success) {
					g.commit();
				} else {
					g.rollback();
				}
			}

		}
		aaiLogger.info(logline, true, "0");
		return response;	
	}	

	/**
	 * Run nodes query.
	 *
	 * @param fromAppId the from app id
	 * @param transId the trans id
	 * @param targetNodeType the target node type
	 * @param edgeFilterParams the edge filter params
	 * @param filterParams the filter params
	 * @param aaiExtMap the aai ext map
	 * @return Response
	 * @throws AAIException the AAI exception
	 */
	public Response runNodesQuery (String fromAppId, 
			String transId, 
			String targetNodeType,
			List <String> edgeFilterParams,
			List <String> filterParams,
			AAIExtensionMap aaiExtMap) throws AAIException {

		DynamicEntity searchResults = null;
		Response response = null;
		boolean success = true;
		TitanTransaction g = null;
		try {
			g = AAIGraph.getInstance().getGraph().newTransaction();

			logline.init(COMPONENT, transId, fromAppId, "runNodesQuery");

			DbMaps dbMaps = IngestModelMoxyOxm.dbMapsContainer.get(AAIConfig.get(AAIConstants.AAI_DEFAULT_API_VERSION_PROP));
			int resCount = 0;

			if( targetNodeType == null || targetNodeType == "" ){
				String emsg = "null or empty target-node-type passed to the node query\n";
				logline.add("emsg", emsg);         	 
				throw new AAIException("AAI_6120", emsg); 
			} 
			else {
				logline.add("start-node-type", targetNodeType);
			}

			if( ! dbMaps.NodeProps.containsKey(targetNodeType) ){
				String emsg = " Unrecognized nodeType [" + targetNodeType + "] passed to node query.\n";
				logline.add("emsg", emsg);
				throw new AAIException("AAI_6115", emsg); 
			}

			if( filterParams.isEmpty()  && edgeFilterParams.isEmpty()){
				// For now, it's ok to pass no filter params.  We'll just return ALL the nodes of the requested type.
				String wmsg = "No filters passed to the node query\n";
				logline.add("warn-msg", wmsg);  
				filterParams = new  ArrayList <String>();
				edgeFilterParams = new  ArrayList <String>();
			} 
			else {
				if( !filterParams.isEmpty())
					logline.add("filterParams", "[" + filterParams.toString() + "]");
				if( !edgeFilterParams.isEmpty())
					logline.add("edgeFilterParams", "[" + edgeFilterParams.toString() + "]");
			}

			Collection <TitanVertex> resultSetVertices = new ArrayList <TitanVertex> ();
			String queryStringForMsg = "";  
			TitanGraphQuery tgQ  = g.query().has("aai-node-type", targetNodeType);
			queryStringForMsg = "has(\"aai-node-type\"," + targetNodeType + ")";

			for( String filter : filterParams ) {
				if( (tgQ != null) && (tgQ instanceof TitanGraphQuery) ){
					String [] pieces = filter.split(":");
					if( pieces.length < 2 ){
						String emsg = "bad filter passed to node query: [" + filter + "]";
						logline.add("emsg", emsg);         	 
						throw new AAIException("AAI_6120", emsg); 
					}
					else {
						String propName = pieces[0];
						String filterType = pieces[1];
						if( filterType.equals("EQUALS")){
							if( pieces.length != 3 ){
								String emsg = "No value passed for filter: [" + filter + "]";
								logline.add("emsg", emsg);         	 
								throw new AAIException("AAI_6120", emsg); 
							}
							else {
								String value = pieces[2];
								queryStringForMsg = queryStringForMsg + ".has(" + propName + "," + value + ")";
								tgQ = tgQ.has(propName,value);
							}
						}
						else if( filterType.equals("DOES-NOT-EQUAL")){
							if( pieces.length != 3 ){
								String emsg = "No value passed for filter: [" + filter + "]";
								logline.add("emsg", emsg);         	 
								throw new AAIException("AAI_6120", emsg); 
							}
							else {
								String value = pieces[2];
								queryStringForMsg = queryStringForMsg + ".hasNot(" + propName + "," + value + ")";
								tgQ = tgQ.hasNot(propName,value);
							}
						}
						else if( filterType.equals("EXISTS")){
							queryStringForMsg = queryStringForMsg + ".has(" + propName + ")";
							tgQ = tgQ.has(propName);
						}
						else if( filterType.equals("DOES-NOT-EXIST")){
							queryStringForMsg = queryStringForMsg + ".hasNot(" + propName + ")";
							tgQ = tgQ.hasNot(propName);
						}
						else {
							String emsg = "bad filterType passed: [" + filterType + "]";
							logline.add("emsg", emsg);         	 
							throw new AAIException("AAI_6120", emsg); 
						}
					}
				}
			}
			if( (tgQ != null) && (tgQ instanceof TitanGraphQuery) ){
				Iterable <?> targetNodeIterable = (Iterable<TitanVertex>) tgQ.vertices();
				Iterator <?> targetNodeIterator = targetNodeIterable.iterator();

				if (!edgeFilterParams.isEmpty()) {
					// edge-filter=pserver:EXISTS: OR pserver:EXISTS:hostname:XXX
					// edge-filter=pserver:DOES-NOT-EXIST: OR pserver:DOES-NOT-EXIST:hostname:XXX
					String filter = edgeFilterParams.get(0); // we process and allow only one edge filter for now
					String [] pieces = filter.split(":");
					if( pieces.length < 2 || pieces.length == 3 || pieces.length > 4){
						String emsg = "bad edge-filter passed: [" + filter + "]";
						logline.add("emsg", emsg);         	 
						throw new AAIException("AAI_6120", emsg); 
					} else {
						String nodeType = pieces[0].toLowerCase();
						String filterType = pieces[1].toUpperCase();
						if (!filterType.equals("EXISTS") && !filterType.equals("DOES-NOT-EXIST")) {
							String emsg = "bad filterType passed: [" + filterType + "]";
							logline.add("emsg", emsg);         	 
							throw new AAIException("AAI_6120", emsg); 
						}
						String propName = null, propValue = null;
						if( pieces.length >= 3) {
							propName = pieces[2].toLowerCase();
							propValue = pieces[3];
						}
						String edgeLabel = getEdgeLabel(targetNodeType, nodeType);	 							        	
						Iterator<Vertex> relatedToIterable = null;
						while( targetNodeIterator.hasNext() ){
							TitanVertex targetNodeVertex = (TitanVertex)targetNodeIterator.next();
							relatedToIterable = targetNodeVertex.vertices(Direction.BOTH, edgeLabel);
							Iterator <?> relatedToIterator = relatedToIterable;
							if( filterType.equals("DOES-NOT-EXIST") && propName == null){
								if (!relatedToIterator.hasNext()) {
									resultSetVertices.add(targetNodeVertex);
								}
							} else {
								while (relatedToIterator.hasNext()) {
									TitanVertex relatedToVertex = (TitanVertex)relatedToIterator.next();		          	 		  
									if (filterType.equals("EXISTS")) {
										if (propName == null)
											resultSetVertices.add(targetNodeVertex);
										else {
											// check for matching property
											if (relatedToVertex.<String>property(propName).orElse(null) != null && relatedToVertex.<String>property(propName).equals(propValue))
												resultSetVertices.add(targetNodeVertex);
										}
									} else if (filterType.equals("DOES-NOT-EXIST")) {
										// check for matching property
										if (relatedToVertex.<String>property(propName).orElse(null) != null && !relatedToVertex.<String>property(propName).equals(propValue))
											resultSetVertices.add(targetNodeVertex);
									}
								} 
							}
						}	
					}
				} else {
					while( targetNodeIterator.hasNext() ){
						TitanVertex targetNodeVertex = (TitanVertex)targetNodeIterator.next();
						resultSetVertices.add(targetNodeVertex);		        		
					}
				}

				AAIResources aaiResources = org.openecomp.aai.ingestModel.IngestModelMoxyOxm.aaiResourceContainer
						.get(aaiExtMap.getApiVersion());
				DynamicJAXBContext jaxbContext = aaiResources.getJaxbContext();	 
				if (aaiExtMap.getApiVersion().equals("v2")) { 
					searchResults = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org.SearchResults");
				} else { 
					searchResults = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org." + 
							aaiExtMap.getApiVersion() + 
							".SearchResults");
				}	
				ArrayList <DynamicEntity> resultDataList = new ArrayList<DynamicEntity>();
				for (TitanVertex thisNode: resultSetVertices){
					resCount++;
					String nodeType = thisNode.<String>property("aai-node-type").orElse(null);
					String thisNodeURL = RestURL.getSearchUrl(g, thisNode, aaiExtMap.getApiVersion());	
					DynamicEntity resultData = null;
					if (aaiExtMap.getApiVersion().equals("v2")) { 
						resultData = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org.ResultData");

					} else { 
						resultData = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org." + 
								aaiExtMap.getApiVersion() + ".ResultData");
					}
					resultData.set("resourceType", nodeType);
					resultData.set("resourceLink", thisNodeURL);
					resultDataList.add(resultData);
				}
				searchResults.set("resultData", resultDataList);
				response = getResponseFromDynamicEntity(searchResults, jaxbContext, aaiExtMap);
			}
			logline.add("count", resCount);
			success = true;
		} catch (AAIException e) { 
			success = false;
			aaiLogger.info(logline, false, e.getErrorObject().getErrorCodeString());
			throw e;
		} catch (Exception e) {
			success = false;
			aaiLogger.info(logline, false, "AAI_5105");
			throw new AAIException("AAI_5105", e);
		} finally {
			if (g != null) {
				if (success) {
					g.commit();
				} else {
					g.rollback();
				}
			}
		}

		aaiLogger.info(logline, true, "0");
		return response;	
	}

	/**
	 * Map result.
	 *
	 * @param g the g
	 * @param logline the logline
	 * @param jaxbContext the jaxb context
	 * @param aaiExtMap the aai ext map
	 * @param resultDetail the result detail
	 * @param includeNodeTypes the include node types
	 * @param resultSet the result set
	 * @param invItemCount the inv item count
	 * @param levelCount the level count
	 * @return the dynamic entity
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 */
	public DynamicEntity mapResult(TitanTransaction g, LogLine logline,
			DynamicJAXBContext jaxbContext,
			AAIExtensionMap aaiExtMap,
			String resultDetail,
			ArrayList<String> includeNodeTypes,
			ResultSet resultSet, 
			int invItemCount,
			int levelCount ) 
					throws UnsupportedEncodingException, AAIException {

		ArrayList <String> retArr = new ArrayList <String> ();
		DynamicEntity item = null;

		if (aaiExtMap.getApiVersion().equals("v2")) {
			item = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org.InventoryItem");
		} else { 
			item = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org." + aaiExtMap.getApiVersion() + ".InventoryItem");
		}

		levelCount++;
		TitanVertex node = resultSet.getVert();
		if( node == null ){
			retArr.add("null Node object returned");
		}
		else {
			String nt = node.<String>property("aai-node-type").orElse(null);

			if ((includeNodeTypes.contains("all")) ||
					(includeNodeTypes.contains(nt.toLowerCase()))) {  // include this node in resultSet only if its one of 
				// the included node types or its include "all"

				retArr.add( levelCount + " " + nt + ", " + node.id().toString() + ";");
				item.set("inventoryItemType", nt);

				String thisNodeURL = node.<String>property("aai-unique-key").orElse(null);
				String permanentURL = aaiExtMap.getUriInfo().getQueryParameters().getFirst("permanenturl");
				if (thisNodeURL != null && !thisNodeURL.equals("")) {
					thisNodeURL = AAIApiServerURLBase.get() + AAIApiVersion.get() + "/" + thisNodeURL;
				} else if ("true".equals(permanentURL)) {
					thisNodeURL = RestURL.getSearchUrl(g, node, aaiExtMap.getApiVersion());
				}
				else {
					thisNodeURL = AAIApiServerURLBase.get() + AAIApiVersion.get() + "/resources/id/" + node.id();
				}
				item.set("inventoryItemLink", thisNodeURL); 

				DbMaps dbMaps = IngestModelMoxyOxm.dbMapsContainer.get(AAIConfig.get(AAIConstants.AAI_DEFAULT_API_VERSION_PROP));
				Collection <String> indexProperties = dbMaps.NodeMapIndexedProps.get(nt);

				ArrayList <DynamicEntity> itemDataList = new ArrayList<DynamicEntity>();

				Iterator<VertexProperty<Object>> pI = node.properties();

				ArrayList<String> defB = new ArrayList<String>();

				if (AAIConfig.getDefaultBools().containsKey(nt)) { 
					for (String db : AAIConfig.getDefaultBools().get(nt)) {
						defB.add(db);
					}
				}

				while( pI.hasNext() ){
					VertexProperty<Object> tp = pI.next();
					String propKey = tp.key();
					if (!propKey.equals("aai-node-type") && 
							(!dbMaps.ReservedPropNames.containsKey((String)propKey)) && // do not return internal properties
							((resultDetail.equalsIgnoreCase("all") ||       
									((resultDetail.equalsIgnoreCase("summary") && // only include indexed properties 
											// if summary detail required
											indexProperties.contains(propKey))) ))){
						if (defB.contains(propKey)) {
							defB.remove(propKey);
						}
						DynamicEntity invItemData = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org." + 
								aaiExtMap.getApiVersion() + 
								".InventoryItemData");
						invItemData.set("propertyName", tp.key());
						String propVal = "";
						if( tp.value() != null ){
							propVal = tp.value().toString();
						}
						invItemData.set("propertyValue", propVal);
						itemDataList.add(invItemData);
					}
				}

				for (String db : defB) {
					if ((resultDetail.equalsIgnoreCase("all") ||       
							((resultDetail.equalsIgnoreCase("summary") && // only include indexed properties 
									// if summary detail required
									indexProperties.contains(db))) )) {
						DynamicEntity invItemData = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org." + 
								aaiExtMap.getApiVersion() + 
								".InventoryItemData");
						invItemData.set("propertyName", db);
						invItemData.set("propertyValue", "false");
						itemDataList.add(invItemData);
					}
				}

				item.set("inventoryItemData", itemDataList);
			}

			if( !resultSet.getSubResultSet().isEmpty() ){
				DynamicEntity taggedInventoryItemList = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org." + aaiExtMap.getApiVersion() + ".TaggedInventoryItemList");

				ListIterator<ResultSet> listItr = resultSet.getSubResultSet().listIterator();
				ArrayList<DynamicEntity> inventoryItemList = new ArrayList<DynamicEntity>();

				boolean hasItems = false;
				while( listItr.hasNext() ){

					DynamicEntity invItem = mapResult(g, logline, 
							jaxbContext,
							aaiExtMap,
							resultDetail,
							includeNodeTypes,
							listItr.next(),
							invItemCount,
							levelCount );
					if (invItem != null) {
						hasItems = true;
						inventoryItemList.add(invItem); // dont add if excluded nodes
					}
				}

				if (item != null && hasItems == true) {				
					taggedInventoryItemList.set("inventoryItem", inventoryItemList);
					ArrayList<DynamicEntity> til = new ArrayList<DynamicEntity>();
					til.add(taggedInventoryItemList);
					item.set("taggedInventoryItemList", til);
				}
			}
		}
		if (!retArr.isEmpty())
			logline.add("msg" + invItemCount, retArr.toString());
		return item;

	}// end of mapResult()


	/**
	 * Gets the edge label.
	 *
	 * @param targetNodeType the target node type
	 * @param nodeType the node type
	 * @return the edge label
	 * @throws AAIException the AAI exception
	 */
	public static String getEdgeLabel(String targetNodeType, String nodeType) throws AAIException{
		String edRule = "";
		String edgeLabel = "???";
		Collection <String> edRuleColl =  DbEdgeRules.EdgeRules.get(targetNodeType + "|" + nodeType);
		Iterator <String> ruleItr = edRuleColl.iterator();
		if( ruleItr.hasNext() ){
			// For now, we only look for one type of edge between two nodes.
			// We're just pulling off the edgeLabel which is the first thing on the list.
			edRule = ruleItr.next();
			String [] rules = edRule.split(",");
			edgeLabel = rules[0];
		}
		else {
			edRuleColl =  DbEdgeRules.EdgeRules.get(nodeType + "|" + targetNodeType);
			ruleItr = edRuleColl.iterator();
			if( ruleItr.hasNext() ){
				edRule = ruleItr.next();
				String [] rules = edRule.split(",");
				edgeLabel = rules[0];
			} else {
				// No edge rule found for this
				String detail = "No EdgeRule found for passed nodeTypes: " + nodeType + ", " + targetNodeType + ".";
				throw new AAIException("AAI_6120", detail); 
			}
		}
		return edgeLabel;
	}


	/**
	 * Run named query.
	 *
	 * @param fromAppId the from app id
	 * @param transId the trans id
	 * @param queryParameters the query parameters
	 * @param aaiExtMap the aai ext map
	 * @return the response
	 * @throws JAXBException the JAXB exception
	 * @throws AAIException the AAI exception
	 */
	public Response runNamedQuery(String fromAppId, String transId, String queryParameters,
			AAIExtensionMap aaiExtMap) throws JAXBException, AAIException {
		// TODO Auto-generated method stub
		AAIResources aaiResources = org.openecomp.aai.ingestModel.IngestModelMoxyOxm.aaiResourceContainer
				.get(aaiExtMap.getApiVersion());
		DynamicJAXBContext jaxbContext = aaiResources.getJaxbContext();
		JAXBUnmarshaller unmarshaller = jaxbContext.createUnmarshaller();

		//String dynamicClass = aaiRes.getResourceClassName();

		DynamicEntity inventoryItems;
		boolean success = true;
		TitanTransaction g = null;
		try {
			g = AAIGraph.getInstance().getGraph().newTransaction();
			if (aaiExtMap.getHttpServletRequest().getContentType() == null || // default to json
					aaiExtMap.getHttpServletRequest().getContentType().contains("application/json")) {
				unmarshaller.setProperty("eclipselink.media-type", "application/json");
				unmarshaller.setProperty("eclipselink.json.include-root", false);
			}

			if (queryParameters.length() == 0) { 
				queryParameters = "{}";
				unmarshaller.setProperty("eclipselink.media-type", "application/json");
				unmarshaller.setProperty("eclipselink.json.include-root", false);
			}
			String dynamicClass = "inventory.aai.openecomp.org." + aaiExtMap.getApiVersion() + ".ModelAndNamedQuerySearch";
			Class<? extends DynamicEntity> resultClass = jaxbContext.newDynamicEntity(dynamicClass).getClass();

			StringReader reader = new StringReader(queryParameters);

			DynamicEntity modelAndNamedQuerySearch = (DynamicEntity) unmarshaller.unmarshal(new StreamSource(reader), resultClass).getValue();

			if (modelAndNamedQuerySearch == null) { 
				throw new AAIException("AAI_5105");
			}

			HashMap<String,Object> namedQueryLookupHash = new HashMap<String,Object>();

			DynamicEntity qp = modelAndNamedQuerySearch.get("queryParameters");

			String namedQueryUuid = null;
			if (qp.isSet("namedQuery")) { 
				DynamicEntity namedQuery = (DynamicEntity) qp.get("namedQuery");

				if (namedQuery.isSet("namedQueryUuid")) { 
					namedQueryUuid = namedQuery.get("namedQueryUuid");
				}
				if (namedQuery.isSet("namedQueryName")) { 
					namedQueryLookupHash.put("named-query-name",  namedQuery.get("namedQueryName"));
				}
				if (namedQuery.isSet("namedQueryVersion")) { 
					namedQueryLookupHash.put("named-query-version", namedQuery.get("namedQueryVersion"));
				}
			}

			if (namedQueryUuid == null) { 
				ArrayList<TitanVertex> namedQueryVertices = DbMeth.getNodes(transId, fromAppId, g, "named-query", namedQueryLookupHash, false);

				for (TitanVertex vert : namedQueryVertices) { 
					namedQueryUuid = vert.<String>property("named-query-uuid").orElse(null); 
					// there should only be one, we'll pick the first if not
					break;
				}
			}

			ArrayList<HashMap<String,Object>> startNodeFilterHash = new ArrayList<HashMap<String,Object>>();

			mapInstanceFilters((DynamicEntity)modelAndNamedQuerySearch.get("instanceFilters"), 
					startNodeFilterHash, jaxbContext);			

			ArrayList<ResultSet> resultSet = ModelBasedProcessing.queryByNamedQuery(transId, fromAppId, g,
					namedQueryUuid, startNodeFilterHash, aaiExtMap.getApiVersion() );

			inventoryItems = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org." + aaiExtMap.getApiVersion() + ".InventoryResponseItems");

			HashMap<Object,String> objectToVertMap = new HashMap<Object,String>();
			ArrayList<DynamicEntity> invItemList = unpackResultSet(g, resultSet, jaxbContext, aaiResources, objectToVertMap, aaiExtMap);

			inventoryItems.set("inventoryResponseItem", invItemList);
			success = true;
		} catch (AAIException e) {
			aaiLogger.info(logline, false, e.getErrorObject().getErrorCodeString());
			success = false;
			throw e;
		} catch (Exception e) {
			aaiLogger.info(logline, false, "AAI_5105");
			success = false;
			throw new AAIException("AAI_5105", e);
		} finally {
			if (g != null) {
				if (success) {
					g.commit();
				} else {
					g.rollback();
				}
			}
		}

		aaiLogger.info(logline, true, "0");

		return getResponseFromDynamicEntity(inventoryItems, jaxbContext, aaiExtMap);
	}

	/**
	 * Execute model operation.
	 *
	 * @param fromAppId the from app id
	 * @param transId the trans id
	 * @param queryParameters the query parameters
	 * @param isDelete the is delete
	 * @param aaiExtMap the aai ext map
	 * @return the response
	 * @throws JAXBException the JAXB exception
	 * @throws AAIException the AAI exception
	 * @throws DynamicException the dynamic exception
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 */
	public Response executeModelOperation(String fromAppId, String transId, String queryParameters, boolean isDelete,
			AAIExtensionMap aaiExtMap) throws JAXBException, AAIException, DynamicException, UnsupportedEncodingException {
		// TODO Auto-generated method stub
		AAIResources aaiResources = org.openecomp.aai.ingestModel.IngestModelMoxyOxm.aaiResourceContainer
				.get(aaiExtMap.getApiVersion());
		DynamicJAXBContext jaxbContext = aaiResources.getJaxbContext();
		JAXBUnmarshaller unmarshaller = jaxbContext.createUnmarshaller();

		//String dynamicClass = aaiRes.getResourceClassName();
		Response response;
		boolean success = true;
		TitanTransaction g = null;
		try {

			g = AAIGraph.getInstance().getGraph().newTransaction();


			if (aaiExtMap.getHttpServletRequest().getContentType() == null || // default to json
					aaiExtMap.getHttpServletRequest().getContentType().contains("application/json")) {
				unmarshaller.setProperty("eclipselink.media-type", "application/json");
				unmarshaller.setProperty("eclipselink.json.include-root", false);
			}

			if (queryParameters.length() == 0) { 
				queryParameters = "{}";
				unmarshaller.setProperty("eclipselink.media-type", "application/json");
				unmarshaller.setProperty("eclipselink.json.include-root", false);
			}
			String dynamicClass = "inventory.aai.openecomp.org." + aaiExtMap.getApiVersion() + ".ModelAndNamedQuerySearch";
			Class<? extends DynamicEntity> resultClass = jaxbContext.newDynamicEntity(dynamicClass).getClass();

			StringReader reader = new StringReader(queryParameters);

			DynamicEntity modelAndNamedQuerySearch = (DynamicEntity) unmarshaller.unmarshal(new StreamSource(reader), resultClass).getValue();

			if (modelAndNamedQuerySearch == null) { 
				throw new AAIException("AAI_5105");
			}

			HashMap<String,Object> modelQueryLookupHash = new HashMap<String,Object>();
			
			String modelNameVersionId = null;
			String modelName = null;
			String modelId = null;
			String modelVersion = null;
			String topNodeType = null;
			
			if (modelAndNamedQuerySearch.isSet("topNodeType")) { 
				topNodeType = modelAndNamedQuerySearch.get("topNodeType");
			}
			if (modelAndNamedQuerySearch.isSet("queryParameters")) { 
				DynamicEntity qp = modelAndNamedQuerySearch.get("queryParameters");

				if (qp.isSet("model")) { 
					DynamicEntity model = (DynamicEntity) qp.get("model");

					if (model.isSet("modelNameVersionId")) { 
						modelNameVersionId = model.get("modelNameVersionId");
					}
					if (model.isSet("modelName")) {
						modelName = model.get("modelName");
						modelQueryLookupHash.put("model-name",  modelName);
					}
					if (model.isSet("modelId")) { 
						modelId =  model.get("modelId");
						modelQueryLookupHash.put("model-id", modelId);
					}
					if (model.isSet("modelVersion")) { 
						modelVersion =  model.get("modelVersion");
						modelQueryLookupHash.put("model-version", modelVersion);
					}
				}

				if (modelNameVersionId == null ) { 
					if (modelId != null || modelName != null) {
						ArrayList<TitanVertex> modelVertices = DbMeth.getNodes(transId, fromAppId, g, "model", modelQueryLookupHash, false);
						for (TitanVertex vert : modelVertices) { 
							modelNameVersionId = vert.<String>property("model-name-version-id").orElse(null); 
							// there should only be one, we'll pick the first if not
							break;
						}
					} else {
						throw new AAIException("AAI_6132", "Could not determine model to use.");
					}
				}
			}
			ArrayList< HashMap<String,Object> > startNodeFilterHash = new ArrayList< HashMap<String,Object> >();

			String resourceVersion = mapInstanceFilters((DynamicEntity)modelAndNamedQuerySearch.get("instanceFilters"), 
					startNodeFilterHash, jaxbContext);	

			if (isDelete) {

				ArrayList<ResultSet> resultSet = ModelBasedProcessing.queryByModel(transId, fromAppId, g,
						modelNameVersionId, modelId, modelName, topNodeType, startNodeFilterHash, aaiExtMap.getApiVersion() );

				new ArrayList<DynamicEntity>();

				HashMap<Object,String> objectToVertMap = new HashMap<Object,String>();
				ArrayList<DynamicEntity> invItemList = unpackResultSet(g, resultSet, jaxbContext, aaiResources, objectToVertMap, aaiExtMap);

				ResultSet rs = resultSet.get(0);

				TitanVertex firstVert = rs.getVert();
				String restURL = RestURL.get(g, firstVert);

				HashMap<String,String> delResult = ModelBasedProcessing.runDeleteByModel( transId, fromAppId, g,
						modelNameVersionId, topNodeType, startNodeFilterHash.get(0), aaiExtMap.getApiVersion(), resourceVersion );

				String resultStr = "";
				for (Map.Entry<String,String> ent : delResult.entrySet()) { 
					resultStr += "v[" + ent.getKey() + "] " + ent.getValue() + ",\n";
				}
				resultStr.trim();

				DynamicEntity inventoryItems = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org." + aaiExtMap.getApiVersion() + ".InventoryResponseItems");
				DynamicEntity topInvItem = remapInventoryItems(invItemList.get(0), jaxbContext, delResult, objectToVertMap, aaiExtMap);

				List<DynamicEntity> newInvItemList = new ArrayList<DynamicEntity>();
				newInvItemList.add(topInvItem);
				inventoryItems.set("inventoryResponseItem", newInvItemList);

				// put the inventoryItems in a UEB notification object
				String notificationVersion = AAIConfig.get("aai.notification.current.version");

				AAIResources aaiNotificationResources = org.openecomp.aai.ingestModel.IngestModelMoxyOxm.aaiResourceContainer
						.get(notificationVersion);

				DynamicJAXBContext notificationJaxbContext = aaiNotificationResources.getJaxbContext();

				DynamicEntity notificationHeader = notificationJaxbContext
						.getDynamicType("inventory.aai.openecomp.org." + notificationVersion + ".NotificationEventHeader")
						.newDynamicEntity();

				notificationHeader.set("entityLink", restURL);
				notificationHeader.set("action", "DELETE");	

				notificationHeader.set("entityType", "inventory-response-items");
				notificationHeader.set("topEntityType", "inventory-response-items");
				notificationHeader.set("sourceName", aaiExtMap.getFromAppId());
				notificationHeader.set("version", notificationVersion);

				StoreNotificationEvent sne = new StoreNotificationEvent();

				sne.storeDynamicEvent(notificationJaxbContext, notificationVersion, notificationHeader, inventoryItems);

				response = Response.ok(resultStr).build();

			} else {
				ArrayList<ResultSet> resultSet = ModelBasedProcessing.queryByModel( transId, fromAppId, g,
						modelNameVersionId, null, modelName, topNodeType, startNodeFilterHash, aaiExtMap.getApiVersion() );

				DynamicEntity inventoryItems = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org." + aaiExtMap.getApiVersion() + ".InventoryResponseItems");

				new ArrayList<DynamicEntity>();
				HashMap<Object,String> objectToVertMap = new HashMap<Object,String>();
				ArrayList<DynamicEntity> invItemList = unpackResultSet(g, resultSet, jaxbContext, aaiResources, objectToVertMap, aaiExtMap);

				inventoryItems.set("inventoryResponseItem", invItemList);

				response = getResponseFromDynamicEntity(inventoryItems, jaxbContext, aaiExtMap);
			}
			success = true;
		} catch (AAIException e) {
			success = false;
			aaiLogger.info(logline, false, e.getErrorObject().getErrorCodeString());
			throw e;
		} catch (Exception e) {
			success = false;
			aaiLogger.info(logline, false, "AAI_5105");
			throw new AAIException("AAI_5105", e);
		} finally {
			if (g != null) {
				if (success) {
					g.commit();
				} else {
					g.rollback();
				}
			}
		}

		aaiLogger.info(logline, true, "0");
		return response;
	}

	/**
	 *  
	 *
	 * @param g the g
	 * @param fromAppId the from app id
	 * @param transId the trans id
	 * @param avZoVtx the av zo vtx
	 * @param jaxbContext the jaxb context
	 * @param aaiExtMap the aai ext map
	 * @return zone type of AvailabilityZone
	 * @throws AAIException the AAI exception
	 */
	private DynamicEntity getAvailabilityZone(TitanTransaction g, String fromAppId, 
			String transId, 
			TitanVertex avZoVtx,
			DynamicJAXBContext jaxbContext,
			AAIExtensionMap aaiExtMap) throws AAIException {
		logline.init(COMPONENT, transId, fromAppId, "getAvailabilityZone");	
		String azName = avZoVtx.<String>property("availability-zone-name").toString();
		logline.add("availability-zone-name", avZoVtx.<String>property("availability-zone-name").toString());

		DynamicEntity zone = null;

		if (aaiExtMap.getApiVersion().equals("v2"))
			zone = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org.AvailabilityZone");
		else
			zone = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org." + aaiExtMap.getApiVersion() + ".AvailabilityZone");

		try {
			zone.set("availabilityZoneName", azName);
			if (avZoVtx.<String>property("hypervisor-type") != null) {
				zone.set("hypervisorType", avZoVtx.<String>property("hypervisor-type").orElse(null));
			}
			if (avZoVtx.<String>property("operational-state") != null) {
				zone.set("operationalState", (avZoVtx.<String>property("operational-state").orElse(null)));
			}

			String defaultApiVersion = AAIConfig.get(AAIConstants.AAI_DEFAULT_API_VERSION_PROP);

			zone.set("relationshipList", RelationshipGraph.getRelationships(g, avZoVtx, defaultApiVersion, aaiExtMap));

		} catch (AAIException e) {
			aaiLogger.info(logline, false, e.getErrorObject().getErrorCodeString());
			throw e;
		} catch (Exception e) {
			aaiLogger.info(logline, false, "AAI_5105");
			throw new AAIException("AAI_5105", e);
		}

		aaiLogger.info(logline, true, "0");
		return zone;
	}


	/**
	 *  
	 *
	 * @param g the g
	 * @param fromAppId the from app id
	 * @param transId the trans id
	 * @param swVtx the sw vtx
	 * @param jaxbContext the jaxb context
	 * @param aaiExtMap the aai ext map
	 * @return DvsSwitch
	 * @throws AAIException the AAI exception
	 */
	private DynamicEntity getDvsSwitch(TitanTransaction g, String fromAppId, 
			String transId, 
			TitanVertex swVtx,
			DynamicJAXBContext jaxbContext,
			AAIExtensionMap aaiExtMap) throws AAIException {
		logline.init(COMPONENT, transId, fromAppId, "getDvsSwitch");	
		logline.add("switch-name", swVtx.<String>property("switch-name").toString());
		DynamicEntity dvs = null;
		if (aaiExtMap.getApiVersion().equals("v2"))
			dvs = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org.DvsSwitch");
		else
			dvs = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org." + aaiExtMap.getApiVersion() + ".DvsSwitch");

		try {
			if (swVtx.<String>property("switch-name") != null) {
				dvs.set("switchName", swVtx.<String>property("switch-name").orElse(null));
			}
			if (swVtx.<String>property("vcenter-url") != null) {
				dvs.set("vcenterUrl", swVtx.<String>property("vcenter-url").orElse(null));
			}
			String defaultApiVersion = AAIConfig.get(AAIConstants.AAI_DEFAULT_API_VERSION_PROP);

			dvs.set("relationshipList", RelationshipGraph.getRelationships(g, swVtx, defaultApiVersion, aaiExtMap));


		} catch (AAIException e) {
			aaiLogger.info(logline, false, e.getErrorObject().getErrorCodeString());
			throw e;
		} catch (Exception e) {
			aaiLogger.info(logline, false, "AAI_5105");
			throw new AAIException("AAI_5105", e);
		}

		aaiLogger.info(logline, true, "0");
		return dvs;
	}


	/**
	 *  
	 *
	 * @param g the g
	 * @param fromAppId the from app id
	 * @param transId the trans id
	 * @param oamVtx the oam vtx
	 * @param jaxbContext the jaxb context
	 * @param aaiExtMap the aai ext map
	 * @return oam type of OamNetwork
	 * @throws AAIException the AAI exception
	 */
	private DynamicEntity getOamNetwork(TitanTransaction g, String fromAppId, 
			String transId, 
			TitanVertex oamVtx,
			DynamicJAXBContext jaxbContext,
			AAIExtensionMap aaiExtMap) throws AAIException {
		logline.init(COMPONENT, transId, fromAppId, "getOamNetwork");	
		String networkUuid = oamVtx.<String>property("network-uuid").toString();
		logline.add("oam-network", networkUuid );

		DynamicEntity oam = null;
		if (aaiExtMap.getApiVersion().equals("v2"))
			oam = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org.OamNetwork");
		else
			oam = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org." + aaiExtMap.getApiVersion() + ".OamNetwork");

		try {
			oam.set("networkUuid", networkUuid);
			if (oamVtx.<String>property("network-name") != null) {
				oam.set("networkName", oamVtx.<String>property("network-name").orElse(null));
			}

			Object cvt = oamVtx.<String>property("cvlan-tag");
			if (cvt instanceof Long) {
				oam.set("cvlanTag", (Long) cvt);
			}
			if (cvt instanceof Integer) {
				Integer tmp = (Integer) cvt;
				oam.set("cvlanTag", (Long) tmp.longValue());
			}
			if (oamVtx.<String>property("ipv4-oam-gateway-address") != null) {
				oam.set("ipv4OamGatewayAddress", oamVtx.<String>property("ipv4-oam-gateway-address").orElse(null));
			}
			Object len = oamVtx.<String>property("ipv4-oam-gateway-address-prefix-length");
			if (len instanceof Integer) {
				oam.set("ipv4OamGatewayAddressPrefixLength", (Integer) len);
			}

			String defaultApiVersion = AAIConfig.get(AAIConstants.AAI_DEFAULT_API_VERSION_PROP);

			DynamicEntity relationships = RelationshipGraph.getRelationships(g, oamVtx, defaultApiVersion, aaiExtMap);
			oam.set("relationshipList", relationships);

		} catch (AAIException e) {
			throw e;
		} catch (Exception e) {
			throw new AAIException("AAI_5105", e);
		}
		aaiLogger.info(logline, true, "0");
		return oam;
	}



	/**
	 * Gets the response from dynamic entity.
	 *
	 * @param searchResult the search result
	 * @param jaxbContext the jaxb context
	 * @param aaiExtMap the aai ext map
	 * @return the response from dynamic entity
	 * @throws JAXBException the JAXB exception
	 */
	private Response getResponseFromDynamicEntity(DynamicEntity searchResult, 
			DynamicJAXBContext jaxbContext, 
			AAIExtensionMap aaiExtMap) throws JAXBException {
		Response response = null;
		JAXBMarshaller marshaller = jaxbContext.createMarshaller();
		marshaller.setProperty(JAXBMarshaller.JAXB_FORMATTED_OUTPUT, false);

		for (MediaType mt : aaiExtMap.getHttpHeaders().getAcceptableMediaTypes()) {
			if (MediaType.APPLICATION_JSON_TYPE.isCompatible(mt)) {
				marshaller.setProperty("eclipselink.media-type", "application/json");
				marshaller.setProperty("eclipselink.json.include-root", false);
				marshaller.setProperty(MarshallerProperties.JSON_VALUE_WRAPPER, "property-value");
				marshaller.setProperty(MarshallerProperties.JSON_MARSHAL_EMPTY_COLLECTIONS, Boolean.FALSE) ;
			}
		}

		StringWriter writer = new StringWriter();
		marshaller.marshal(searchResult, writer);

		response = Response.ok(searchResult).entity(writer.toString()).build();
		return response;
	}

	/**
	 * Map instance filters.
	 *
	 * @param instanceFilters the instance filters
	 * @param startNodeFilterHash the start node filter hash
	 * @param jaxbContext the jaxb context
	 * @return the string
	 */
	private String mapInstanceFilters(DynamicEntity instanceFilters, ArrayList<HashMap<String,Object>> startNodeFilterHash, DynamicJAXBContext jaxbContext) { 			
		
		if (instanceFilters == null || !instanceFilters.isSet("instanceFilter")) {
			return null;
		}
		@SuppressWarnings("unchecked")
		List<DynamicEntity> instanceFilter = (ArrayList<DynamicEntity>)instanceFilters.get("instanceFilter");
		String resourceVersion = null;

		for (DynamicEntity instFilt : instanceFilter) { 
			List<DynamicEntity> any = instFilt.get("any");
			HashMap<String,Object> thisNodeFilterHash = new HashMap<String,Object>();
			for (DynamicEntity anyEnt : any) { 
				String clazz = anyEnt.getClass().getCanonicalName();
				String simpleClazz = anyEnt.getClass().getSimpleName();

				String nodeType = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, simpleClazz);

				DynamicType anyEntType = jaxbContext.getDynamicType(clazz);

				for (String propName : anyEntType.getPropertiesNames()) {
					// hyphencase the prop and throw it on the hash
					if (anyEnt.isSet(propName)) {
						thisNodeFilterHash.put(nodeType + "." + CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, propName), anyEnt.get(propName));
						if (propName.equals("resourceVersion") && resourceVersion == null) { 
							resourceVersion = (String)anyEnt.get(propName);
						}
					}
				}
			}
			startNodeFilterHash.add(thisNodeFilterHash);
		}
		return resourceVersion;
	}

	/**
	 * Remap inventory items.
	 *
	 * @param invResultItem the inv result item
	 * @param jaxbContext the jaxb context
	 * @param includeTheseVertices the include these vertices
	 * @param objectToVertMap the object to vert map
	 * @param aaiExtMap the aai ext map
	 * @return the dynamic entity
	 */
	private DynamicEntity remapInventoryItems(DynamicEntity invResultItem, DynamicJAXBContext jaxbContext, 
			HashMap<String,String> includeTheseVertices, HashMap<Object,String> objectToVertMap, AAIExtensionMap aaiExtMap) { 


		DynamicEntity inventoryItem = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org." + aaiExtMap.getApiVersion() + ".InventoryResponseItem");
		Object item = invResultItem.get("item");
		inventoryItem.set("modelName", 			invResultItem.get("modelName"));
		inventoryItem.set("item", 				item);
		inventoryItem.set("extraProperties", 	invResultItem.get("extraProperties"));

		String vertexId = "";

		if (objectToVertMap.containsKey(item)) {
			vertexId = objectToVertMap.get(item);
		}

		if (includeTheseVertices.containsKey(vertexId)) { 
			if (invResultItem.isSet("inventoryResponseItems")) {
				List<DynamicEntity> invItemList = new ArrayList<DynamicEntity>();
				DynamicEntity inventoryItems = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org." + aaiExtMap.getApiVersion() + ".InventoryResponseItems");
				DynamicEntity subInventoryResponseItems = invResultItem.get("inventoryResponseItems");
				List<DynamicEntity> subInventoryResponseItemList = subInventoryResponseItems.get("inventoryResponseItem");
				for (DynamicEntity ent : subInventoryResponseItemList) { 
					DynamicEntity invItem = remapInventoryItems(ent, jaxbContext, includeTheseVertices, objectToVertMap, aaiExtMap);
					if (invItem != null) { 
						invItemList.add(invItem);
					}
				}
				if (invItemList != null) { 
					inventoryItems.set("inventoryResponseItem", invItemList);
					inventoryItem.set("inventoryResponseItems",  inventoryItems);
				}
			}
		}
		return inventoryItem;
	}

	/**
	 * Unpack result set.
	 *
	 * @param g the g
	 * @param resultSetList the result set list
	 * @param jaxbContext the jaxb context
	 * @param aaiResources the aai resources
	 * @param objectToVertMap the object to vert map
	 * @param aaiExtMap the aai ext map
	 * @return the array list
	 * @throws AAIException the AAI exception
	 */
	// this should return an inventoryItem
	private ArrayList<DynamicEntity> unpackResultSet(TitanTransaction g, ArrayList<ResultSet> resultSetList, 
			DynamicJAXBContext jaxbContext, 
			AAIResources aaiResources, 
			HashMap<Object,String> objectToVertMap,
			AAIExtensionMap aaiExtMap) throws AAIException {

		ArrayList<DynamicEntity> resultList = new ArrayList<DynamicEntity>();

		for (ResultSet resultSet : resultSetList) { 

			DynamicEntity inventoryItem = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org." + aaiExtMap.getApiVersion() + ".InventoryResponseItem");
			DynamicEntity inventoryItems = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org." + aaiExtMap.getApiVersion() + ".InventoryResponseItems");
			
			// add this inventoryItem to the resultList for this level
			resultList.add(inventoryItem);

			TitanVertex vert = resultSet.getVert();

			Long vertId = (Long)vert.longId();

			String aaiNodeType = vert.<String>property("aai-node-type").orElse(null);

			String simpleName = CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL,aaiNodeType);
			// find an aaiResource with this node as the last element

			AAIResource aaiRes = null;
			for (Map.Entry<String,AAIResource> ent: aaiResources.getAaiResources().entrySet()) { 
				AAIResource res = ent.getValue();
				if (res.getSimpleName().equals(simpleName)) {
					aaiRes = res;
					break;
				}
			}

			if (aaiRes != null) { 
				PojoUtils pu = new PojoUtils();
				DynamicEntity thisObj = jaxbContext.newDynamicEntity(aaiRes.getResourceClassName());

				if (resultSet.getExtraPropertyHash() != null) { 
					HashMap<String,Object> extraProperties = resultSet.getExtraPropertyHash();	

					DynamicEntity extraPropertiesEntity = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org." + aaiExtMap.getApiVersion() + ".ExtraProperties");

					List<DynamicEntity> extraPropsList = new ArrayList<DynamicEntity>();

					for (Map.Entry<String,Object> ent : extraProperties.entrySet()) {
						String propName = ent.getKey();
						Object propVal = ent.getValue();

						DynamicEntity extraPropEntity = jaxbContext.newDynamicEntity("inventory.aai.openecomp.org." +  aaiExtMap.getApiVersion() + ".ExtraProperty");

						extraPropEntity.set("propertyName",  propName);
						extraPropEntity.set("propertyValue", propVal);

						extraPropsList.add(extraPropEntity);

					}
					extraPropertiesEntity.set("extraProperty", extraPropsList);
					inventoryItem.set("extraProperties", extraPropertiesEntity);
				}
				String propertyLimitDesc = resultSet.getPropertyLimitDesc();

				if (propertyLimitDesc != null && propertyLimitDesc.length() > 0) {

					if ("SHOW-NONE".equalsIgnoreCase(propertyLimitDesc)) { 
						HashMap<String,Object> emptyPropertyOverRideHash = new HashMap<String,Object>();
						pu.getAaiDynamicObjectFromVertex(aaiRes, thisObj, vert, aaiRes.getPropertyDataTypeMap(), emptyPropertyOverRideHash);
					} else if ("SHOW-ALL".equalsIgnoreCase(propertyLimitDesc)) { 
						pu.getAaiDynamicObjectFromVertex(aaiRes, thisObj, vert, aaiRes.getPropertyDataTypeMap());
					} else if ("NAME-AND-KEYS-ONLY".equalsIgnoreCase(propertyLimitDesc)) {
						AAIResourceKeys aaiResKeys = aaiRes.getAaiResourceKeys();
						HashMap<String,Object> keysAndNamesPropHash = new HashMap<String,Object>();
						for (AAIResourceKey aaiResKey : aaiResKeys.getAaiResourceKey()) { 
							keysAndNamesPropHash.put(aaiResKey.getKeyName(), "dummy");
						}
						for (String nodeNameProp : aaiRes.getNodeNameProps().get(CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN,simpleName))) {
							keysAndNamesPropHash.put(nodeNameProp, "dummy");
						}
						pu.getAaiDynamicObjectFromVertex(aaiRes, thisObj, vert, aaiRes.getPropertyDataTypeMap(), keysAndNamesPropHash);
					}
				} else { 
					if (resultSet.getPropertyOverRideHash() != null && resultSet.getPropertyOverRideHash().size() > 0) { 
						HashMap<String,Object> propertyOverRideHash = resultSet.getPropertyOverRideHash();
						pu.getAaiDynamicObjectFromVertex(aaiRes, thisObj, vert, aaiRes.getPropertyDataTypeMap(), propertyOverRideHash);
					} else {
						pu.getAaiDynamicObjectFromVertex(aaiRes, thisObj, vert, aaiRes.getPropertyDataTypeMap());
					}
				}

				if (thisObj != null) { 
					inventoryItem.set("item", thisObj);

					objectToVertMap.put(thisObj, vertId.toString());

					String modelName = null;
					try { 
						String personaModelId = (String)vert.<String>property("persona-model-id").orElse(null);
						String personaModelVersion = (String)vert.<String>property("persona-model-version").orElse(null);
						
						if ( (personaModelId != null && personaModelVersion != null) 
								&& (personaModelId.length() > 0 && personaModelVersion.length() > 0) ) {
							HashMap<String,Object> modelLookupHash = new HashMap<String,Object>();

							modelLookupHash.put("model-id", personaModelId);
							modelLookupHash.put("model-version", personaModelVersion);

							TitanVertex modelVert = DbMeth.getUniqueNode(aaiExtMap.getTransId(), 
									aaiExtMap.getFromAppId(), g, "model", modelLookupHash, null);

								modelName = modelVert.<String>property("model-name").orElse(null); 
								// there should only be one, we'll pick the first if not
								if (modelName.length() > 0) { 
									inventoryItem.set("modelName", modelName);
								}
						}
					} catch (DynamicException e) { 
						; // it's ok, dynamic object might not have these fields
					} catch (AAIException e) { 
						if (e.getErrorObject().getErrorCode().equals("6114")) { 
							// it's ok, couldn't find a matching model
						} else { 
							throw e;
						}
					}

					if (resultSet.getSubResultSet() != null) { 
						ArrayList<ResultSet> subResultSet = resultSet.getSubResultSet();
						if (subResultSet != null) { 
							ArrayList<DynamicEntity> res = unpackResultSet(g, subResultSet, jaxbContext, aaiResources, objectToVertMap, aaiExtMap);
							if (res.size() > 0) { 
								inventoryItems.set("inventoryResponseItem", res);
								inventoryItem.set("inventoryResponseItems", inventoryItems);
							}
						}
					}
				}
			}
		}

		return resultList;
	}

}
