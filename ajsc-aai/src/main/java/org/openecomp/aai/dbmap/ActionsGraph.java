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

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.persistence.dynamic.DynamicEntity;
import org.eclipse.persistence.exceptions.DynamicException;
import org.eclipse.persistence.jaxb.dynamic.DynamicJAXBContext;
import org.openecomp.aai.dbgen.DbMeth;
import org.openecomp.aai.domain.model.AAIResource;
import org.openecomp.aai.domain.model.AAIResources;
import org.openecomp.aai.domain.model.AncestryItem;
import org.openecomp.aai.domain.model.AncestryItems;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.extensions.AAIExtensionMap;
import org.openecomp.aai.ingestModel.DbMaps;
import org.openecomp.aai.ingestModel.IngestModelMoxyOxm;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.RestController;
import org.openecomp.aai.util.RestObject;
import org.openecomp.aai.util.RestURL;
import org.openecomp.aai.util.StoreNotificationEvent;
import org.openecomp.aai.domain.yang.GenericVnf;
import com.google.common.base.CaseFormat;
import com.thinkaurelius.titan.core.TitanTransaction;
import com.thinkaurelius.titan.core.TitanVertex;

/**
 * Database Mapping class which acts as the middle man between the REST interface objects 
 * for the Search namespace 
 
 */
public class ActionsGraph {

	private final int URI_OFFSET = 20; // length of cloud-infrastructure/ to be replaced
	private LogLine logline = new LogLine();
	private LogLine vlogline = new LogLine();
	private LogLine tlogline = new LogLine();
	private final String COMPONENT = "aaidbmap";
	protected AAILogger aaiLogger = new AAILogger(ActionsGraph.class.getName());

	/**
	 * Needs cloud region for node type.
	 *
	 * @param nodeType the node type
	 * @return true, if successful
	 */
	private boolean needsCloudRegionForNodeType( String nodeType ) {
		switch ( nodeType ) {
		case "volume-group":
		case "tenant":
		case "flavor":	
		case "image":
		case "dvs-switch":
		case "oam-network":
		case "availability-zone":
			return true;
		}
			
		return false;
	}
	
	/**
	 * ProcessUpdate based on the request provided.
	 *
	 * @param fromAppId the from app id
	 * @param transId the trans id
	 * @param updateRequest the update request
	 * @param aaiExtMap the aai ext map
	 * @throws AAIException the AAI exception
	 */
	public void propertyUpdate (String fromAppId, 
								String transId, 
								DynamicEntity updateRequest,
								AAIExtensionMap aaiExtMap) throws AAIException {
		logline.init(COMPONENT, transId, fromAppId, "propertyUpdate");
		AAIException ex = null;
		boolean success = true;
		TitanTransaction g = null;
		try {
				g = AAIGraph.getInstance().getGraph().newTransaction();
	    	   TitanVertex updatedVertex = null;
		       // Build a hash with properties to update
			   HashMap <String,Object> propHash = new HashMap<String, Object>();
			   
			   DbMaps dbMaps = IngestModelMoxyOxm.dbMapsContainer.get(aaiExtMap.getApiVersion());
			   String reqVers = aaiExtMap.getApiVersion().substring(1);
			   int vers = (new Integer(reqVers)).intValue();
			   String defaultRegionId = null;
			   String defaultRegionOwner = null;
			   
			   if ((String)updateRequest.get("updateNodeType") == null)
				   throw new AAIException("AAI_6120", "Missing update-node-type");
			   String updateNodeType = ((String)updateRequest.get("updateNodeType")).toLowerCase().trim();
			   if (!dbMaps.NodeProps.containsKey(updateNodeType)) {
					throw new AAIException("AAI_6120", "Invalid update-node-type:" + updateNodeType);
			   } else {
				  logline.add("update-node-type", updateNodeType);
			   }
			   
			   HashMap <String,Object> keyHash = new HashMap<String, Object>();
			   LinkedHashMap <String,Object> depNodeKeyHash = new LinkedHashMap<String, Object>();
			   String updateNodeURI = null;
			   try {
				   updateNodeURI = (String)updateRequest.get("updateNodeUri");
			   } catch (DynamicException de) {
				   // nothing to do - as it is not supported in pre v6 version
			   }
			   
			   String depNodeType = null;
					   			
			   // Build a hash with keys to uniquely identify the update Node
			   // and a hash for the keys to get the dependent Node
			   if (updateNodeURI == null) {			   
				   List<DynamicEntity> keyParams = updateRequest.get("updateNodeKey"); 
				   
				   if( keyParams == null || keyParams.isEmpty())
					  	throw new AAIException("AAI_6120", "update-node-key missing");
				   for( DynamicEntity keyData : keyParams ){ 
					   String nodeTypeAndKeyName = ((String)keyData.get("keyName")).toLowerCase().trim();
					   if (nodeTypeAndKeyName.indexOf(".")  > 0) { 
						   String nodeType = nodeTypeAndKeyName.substring(0, nodeTypeAndKeyName.indexOf("."));
						   String keyName = nodeTypeAndKeyName.substring(nodeTypeAndKeyName.indexOf(".") + 1);
						   if (!nodeType.equals(updateNodeType)) // if its not a key for the updateNodeType - it must be a key 
							                                // for the dependent node
							   depNodeKeyHash.put(nodeTypeAndKeyName, ((String)keyData.get("keyValue")).trim());
						   else
							   keyHash.put(keyName, ((String)keyData.get("keyValue")).trim());
					   } else { // we support keyname without nodetype for a non dependent node
						   keyHash.put(nodeTypeAndKeyName, ((String)keyData.get("keyValue")).trim());
					   }
				   }
				   depNodeType = DbMeth.figureDepNodeTypeForRequest(transId, fromAppId, updateNodeType, depNodeKeyHash, aaiExtMap.getApiVersion());
			  } else {
					LinkedHashMap <String,Object> returnHash = RestURL.getKeyHashes(updateNodeURI);
					Set<String> returnKeySet = returnHash.keySet();
					Iterator<String> iter = returnKeySet.iterator();
					String updateNodeKey = null;
					while (iter.hasNext()) {
						String key = (String) iter.next();
						if (key.startsWith(updateNodeType + ".")) {
							keyHash.put(key.substring(key.indexOf(".") + 1), returnHash.get(key)); // this hash does not need nodetype in name/key
							updateNodeKey = key; 
						} else {	
							if (depNodeType == null) { // get the parent dependent node, first one after its own key
								depNodeType = key.substring(0, key.indexOf("."));
								logline.add("depNodeType", depNodeType);
								returnHash.remove(updateNodeKey); // remove this node's key
								depNodeKeyHash.putAll(returnHash);
								logline.add("depNodeKeyHash", depNodeKeyHash.toString());
								break;
							}
						}
					}
			  }
					   
			  if( keyHash == null || keyHash.isEmpty()){
			  		throw new AAIException("AAI_6120", "update-node-key or update-node-uri missing or invalid");
			  } else {
			  	    logline.add("keyHash", keyHash.toString());
			  }
		       
			List <DynamicEntity> actionList = updateRequest.get("action");
			for (DynamicEntity action: actionList) {			
		       if (!((String)action.get("actionType")).toLowerCase().trim().equals("replace")) { 
		 			throw new AAIException("AAI_6120", "Invalid action-type:" + (String)action.get("actionType") );
		       }
	
		       List <DynamicEntity> actionDataList = action.get("actionData");
				Collection <String> valProps =  dbMaps.NodeProps.get(updateNodeType);
				String type = "";
				String propName = "";
				String detail = "";
				String trimmedValue = "";
				Object propValue = null;
			   for( DynamicEntity actionData : actionDataList ){ 
				   propName = ((String)actionData.get("propertyName")).toLowerCase().trim();
				   if (! valProps.contains(propName)) {
						aaiLogger.info(logline, false, "AAI_6102");
						detail = "property = " + propName + " is not valid for nodeType = " + updateNodeType; 
						throw new AAIException("AAI_6102", detail);
				   }
				   type = dbMaps.PropertyDataTypeMap.get(propName);
				   if (type.matches("Boolean|String|Integer|Long")) {
					   trimmedValue = ((String)actionData.get("propertyValue")).trim();
					   if (type.equals("Boolean")) {
						   propValue = new Boolean(trimmedValue);
					   } else if (type.equals("Integer")) {
						   propValue = new Integer(trimmedValue);
					   } else if (type.equals("Long")) {
						   propValue = new Long(trimmedValue);
					   } else {
						   propValue = trimmedValue;
					   }
					   propHash.put(propName, propValue);
				   }
		       }
			}
			   
	       if( propHash == null || propHash.isEmpty()){
	      		throw new AAIException("AAI_6120", "action-data not found");
	      } else {
	      	    logline.add("action-data", propHash.toString());
	      }
		       
	       propHash.putAll(keyHash); // combine keys and props to send to patchAaiNode

	       if (depNodeKeyHash.isEmpty() || depNodeType == null || depNodeType.equals("")) 
			   // update the properties for updateNode
			   updatedVertex = DbMeth.patchAaiNode(transId, fromAppId, g, updateNodeType, propHash, null, aaiExtMap.getApiVersion());
	       else {
		       TitanVertex depNode = DbMeth.getUniqueNodeWithDepParams(transId, fromAppId, g, depNodeType, depNodeKeyHash, aaiExtMap.getApiVersion());			   
			   // update the properties for updateNode
		       updatedVertex = DbMeth.patchAaiNode(transId, fromAppId, g, updateNodeType, propHash, depNode, aaiExtMap.getApiVersion());
	       }
			
			// UEB Event added to table
			String updateNodeURL = setupUEBEventObject(fromAppId, transId, g, updateNodeType, updatedVertex, aaiExtMap.getApiVersion());
			String vnfName = null;
			try {
				String vnfId = (String) keyHash.get("vnf-id");
				if ( updateNodeType.equals("generic-vnf") ) {
					vnfName = retrieveGenricVnfName(fromAppId, transId, vnfId);
					if ( vnfName == null ) {
						logline.add("msg", "no notify to INSTAR, equipment-role is not VRR/VIPE");
					}
				} 
				
			} catch (Exception e) {
				vlogline.add("msg", "no notify to INSTAR, exception retrieving vnfName");
				ex = new AAIException("AAI_4000", e);
				return;
			}
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
	}		
	
	/**
	 * Setup UEB event object.
	 *
	 * @param fromAppId the from app id
	 * @param transId the trans id
	 * @param graph the graph
	 * @param updateNodeType the update node type
	 * @param updatedVertex the updated vertex
	 * @param apiVersion the api version
	 * @return the string
	 * @throws AAIException the AAI exception
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 */
	public String setupUEBEventObject (String fromAppId, 
									 String transId,
									 TitanTransaction graph,
									 String updateNodeType,
									 TitanVertex updatedVertex,
									 String apiVersion) throws AAIException, UnsupportedEncodingException {		
			
			String currentApiVersion = AAIConfig.get("aai.notification.current.version");
			String action = "UPDATE";			
			AAIExtensionMap aaiExtMap = new AAIExtensionMap();
			long startTime = System.currentTimeMillis() ;
			aaiExtMap.setStartTime(startTime);
			aaiExtMap.setCheckpointTime(startTime);
			aaiExtMap.setTransId(transId);
			aaiExtMap.setFromAppId(fromAppId);
			
			HashMap<String, String> allKeys = new HashMap<String, String>();
			LinkedHashMap<String, LinkedHashMap<String,Object>> keyList = new LinkedHashMap<String, LinkedHashMap<String,Object>>();			
			GraphHelpersMoxy graphHelpers = new GraphHelpersMoxy();
			AncestryItems ancestry = new AncestryItems();
			
			String updatedNodeURL = RestURL.get(graph,  updatedVertex, currentApiVersion, false, true);		
			AAIResource aaiRes = RestURL.parseUri(allKeys, keyList, updatedNodeURL, aaiExtMap);
			
			AAIResources aaiResources = org.openecomp.aai.ingestModel.IngestModelMoxyOxm.aaiResourceContainer.get(currentApiVersion);
			DynamicJAXBContext notificationJaxbContext = aaiResources.getJaxbContext();
			DynamicEntity eh = notificationJaxbContext
					.getDynamicType("inventory.aai.openecomp.org." + currentApiVersion + ".NotificationEventHeader")
					.newDynamicEntity();

			eh.set("entityType",    updateNodeType);
			eh.set("action",        action);
			eh.set("sourceName",    fromAppId);
			eh.set("version",       currentApiVersion);
			eh.set("entityLink",    updatedNodeURL);	
			
			 if (!graphHelpers.isEventEnabled(action, aaiRes.getNamespace(), aaiRes.getSimpleName())) {
				 return updatedNodeURL;
			 }

			// get ourselves this time
			graphHelpers.getAncestry(graph, keyList, allKeys, aaiRes, ancestry, true, aaiRes.getFullName(), 
										notificationJaxbContext, aaiExtMap);
			
			for (Map.Entry<String,AncestryItem> ent : ancestry.getAncestryItems().entrySet()) { 
				AncestryItem anc = ent.getValue();
				eh.set("topEntityType", CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN,anc.getAaiResource().getSimpleName()));
				break;
			}
			
			DynamicEntity topObject;
			try {
				topObject = graphHelpers.unpackAncestry(graph, ancestry, aaiRes.getFullName(), notificationJaxbContext, aaiExtMap);
			} catch (Exception e) { 
				AAIException ex = new AAIException("AAI_5105", e);
				throw ex;
			}
			
			StoreNotificationEvent sne = new StoreNotificationEvent();
			sne.storeDynamicEvent(notificationJaxbContext, currentApiVersion, eh, topObject);
			
			return updatedNodeURL;

		}
		
		
		
		/**
		 * Retrieve genric vnf name.
		 *
		 * @param fromAppId the from app id
		 * @param transId the trans id
		 * @param vnfId the vnf id
		 * @return the string
		 * @throws AAIException the AAI exception
		 */
		private String retrieveGenricVnfName(String fromAppId, String transId, String vnfId) throws AAIException {
			AAIException ex = null;
			String vnfName = null;
			String encodedVnfId = null;
			LogLine mylogline = new LogLine();
			try {
				mylogline.init("aairestctrl", transId, "AAI", "retrieveGenricVnfName");
				
				RestObject<GenericVnf> restObjGenericVnf = new RestObject<GenericVnf>();
				GenericVnf genericVnf = new GenericVnf();
				restObjGenericVnf.set(genericVnf);
				
				encodedVnfId = RestURL.encodeURL(vnfId);
				mylogline.add("encodedVnfId", encodedVnfId);

				String path = RestController.REST_APIPATH_GENERIC_VNF + encodedVnfId;
				mylogline.add("url", path);

				RestController.<GenericVnf> Get(genericVnf, fromAppId, transId, path, restObjGenericVnf,
						false);

				genericVnf = restObjGenericVnf.get();

				if ( ( genericVnf.getEquipmentRole() != null ) && 
						( genericVnf.getEquipmentRole().equals("VRR") ||
								genericVnf.getEquipmentRole().equals("VIPE") ) ) {		
					vnfName =  genericVnf.getVnfName();
				}
				vlogline.add("vnfName", vnfName);

			} catch (AAIException ae) {
				ex = ae;
			} catch (Exception e) {
				ex = new AAIException("AAI_7115", e);
			} finally {
				if (ex == null) {
					aaiLogger.info(mylogline, true, "0");
				} else {
					aaiLogger.error(ex.getErrorObject(), mylogline, ex);
					aaiLogger.info(mylogline, false, ex.getErrorObject().getErrorCodeString());
				}
			}
			return vnfName;
		}	
}
		
