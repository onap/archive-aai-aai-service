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
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.lang.StringUtils;
import org.eclipse.persistence.dynamic.DynamicEntity;
import org.eclipse.persistence.dynamic.DynamicType;
import org.eclipse.persistence.exceptions.DynamicException;
import org.eclipse.persistence.jaxb.JAXBMarshaller;
import org.eclipse.persistence.jaxb.JAXBUnmarshaller;
import org.eclipse.persistence.jaxb.MarshallerProperties;
import org.eclipse.persistence.jaxb.dynamic.DynamicJAXBContext;
import org.openecomp.aai.dbgen.DbMeth;
import org.openecomp.aai.domain.model.AAIResource;
import org.openecomp.aai.domain.model.AAIResourceKey;
import org.openecomp.aai.domain.model.AAIResourceKeys;
import org.openecomp.aai.domain.model.AAIResources;
import org.openecomp.aai.domain.model.AncestryItem;
import org.openecomp.aai.domain.model.AncestryItems;
import org.openecomp.aai.domain.responseMessage.AAIResponseMessage;
import org.openecomp.aai.domain.responseMessage.AAIResponseMessageDatum;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.exceptions.AAIExceptionWithInfo;
import org.openecomp.aai.extensions.AAIExtensionMap;
import org.openecomp.aai.extensions.ExtensionController;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.rest.RestProviders;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIConstants;
import org.openecomp.aai.util.PojoUtils;
import org.openecomp.aai.util.StoreNotificationEvent;

import com.google.common.base.CaseFormat;
import com.thinkaurelius.titan.core.TitanTransaction;
import com.thinkaurelius.titan.core.TitanVertex;

public class GraphHelpersMoxy {

	protected LogLine logline = new LogLine();
	protected LogLine vlogline = new LogLine();
	protected LogLine tlogline = new LogLine();
	protected final String COMPONENT = "aaidbmap";
	protected AAILogger aaiLogger = new AAILogger(GraphHelpersMoxy.class.getName());
	
	/**
	 * Do checkpoint.
	 *
	 * @param location the location
	 * @param aaiExtMap the aai ext map
	 */
	private void doCheckpoint(String location, AAIExtensionMap aaiExtMap) { 

		long now = System.currentTimeMillis();
//		long lastCheckpoint = aaiExtMap.getCheckpointTime();
//		long totalElapsed = now - aaiExtMap.getStartTime();
//
//		long sinceLastCheckpoint = now - lastCheckpoint;
		//System.out.println("CHECKPOINT|" + aaiExtMap.getFullResourceName() + "|" + location + "|" + sinceLastCheckpoint + "|" + totalElapsed);

		//		System.out.println(now + "|" + "CHECKPOINT|" + location + "|" + sinceLastCheckpoint + "|" + totalElapsed);
		aaiExtMap.setCheckpointTime(now);

	}
	
	/**
	 * Handle put.
	 *
	 * @param objectFromRequest the object from request
	 * @param lookupHashMapList the lookup hash map list
	 * @param allKeys the all keys
	 * @param aaiExtMap the aai ext map
	 * @return the response
	 * @throws AAIException the AAI exception
	 */
	public Response handlePut(String objectFromRequest,
			HashMap<String, LinkedHashMap<String, Object>> lookupHashMapList, HashMap<String, String> allKeys,
			AAIExtensionMap aaiExtMap) throws AAIException {

		AAIException ex = null;
		
		LogLine hLogline = new LogLine();
		hLogline.init(COMPONENT, aaiExtMap.getTransId(), aaiExtMap.getFromAppId(), "handlePut");
		
		boolean success = true;
		TitanTransaction g = null;
		Response response = null;
		try {
			g = AAIGraph.getInstance().getGraph().newTransaction();
			g.rollback();
			g = AAIGraph.getInstance().getGraph().newTransaction();
			aaiExtMap.setGraph(g);

			boolean objectExisted[] = new boolean[1];
			objectExisted[0] = false;

			DynamicEntity meObject;

			AAIResources aaiResources = org.openecomp.aai.ingestModel.IngestModelMoxyOxm.aaiResourceContainer
					.get(aaiExtMap.getApiVersion());
			AAIResource aaiRes = aaiResources.getAaiResources().get(aaiExtMap.getFullResourceName());

			if (aaiRes.isAllowDirectWrite() == false) {
				throw new AAIException("AAI_3006");
			}
			
			String objectNameForExtensions = aaiRes.getFullName().replace("/", "");
			// see if this node has parent nodes before doing this...
			AAIResource parent = aaiRes.getParent();

			DynamicJAXBContext jaxbContext = aaiResources.getJaxbContext();

			AncestryItems ancestry = new AncestryItems();
			TitanVertex parentVertex = null;
			if (parent.getResourceType().equals("node")) {
				getAncestry(g, lookupHashMapList, allKeys, aaiRes, ancestry, false, aaiRes.getFullName(), jaxbContext,
						aaiExtMap);
				parentVertex = ancestry.getAncestryItems().get(parent.getFullName()).getVertex();
			} else {
				if (parent.getParent().getResourceType() != null && parent.getParent().getResourceType().equals("node")) {
					getAncestry(g, lookupHashMapList, allKeys, aaiRes, ancestry, false, aaiRes.getFullName(), jaxbContext,
							aaiExtMap);
					parentVertex = ancestry.getAncestryItems().get(parent.getParent().getFullName()).getVertex();
				}
			}

			JAXBUnmarshaller unmarshaller = jaxbContext.createUnmarshaller();

			String dynamicClass = aaiRes.getResourceClassName();

			if (aaiExtMap.getHttpServletRequest().getContentType() == null || // default to json
					aaiExtMap.getHttpServletRequest().getContentType().contains("application/json")) {
				unmarshaller.setProperty("eclipselink.media-type", "application/json");
				unmarshaller.setProperty("eclipselink.json.include-root", false);
			}

			if (objectFromRequest.length() == 0) { 
				objectFromRequest = "{}";
				unmarshaller.setProperty("eclipselink.media-type", "application/json");
				unmarshaller.setProperty("eclipselink.json.include-root", false);
			}

			Class<? extends DynamicEntity> resultClass = jaxbContext.newDynamicEntity(dynamicClass).getClass();

			StringReader reader = new StringReader(objectFromRequest);

			meObject = (DynamicEntity) unmarshaller.unmarshal(new StreamSource(reader), resultClass).getValue();

			if (meObject == null) { 
				throw new AAIException("AAI_5105");
			}

			// set up the extension and call the pre-extension if it's found
			aaiExtMap.setAncestry(ancestry);
			aaiExtMap.setObjectFromRequest(meObject);
			aaiExtMap.setObjectFromRequestType(dynamicClass);
			aaiExtMap.setJaxbContext(jaxbContext);

			String topObjectSimpleResourceName = aaiResources.getAaiResources().get(aaiExtMap.getTopObjectFullResourceName()).getSimpleName();

			ExtensionController ext = new ExtensionController();
			ext.runExtension(aaiExtMap.getApiVersion(),
					CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, aaiExtMap.getNamespace()),
					topObjectSimpleResourceName,
					"DynamicAdd" + objectNameForExtensions + "PreProc",
					aaiExtMap,
					true);

			TitanVertex meVertex = putObject(g, aaiRes, allKeys, lookupHashMapList, meObject, parentVertex, objectExisted,
					jaxbContext, ancestry, aaiExtMap);

			AncestryItems newAncestry = new AncestryItems();
			// get ourselves this time
			getAncestry(g, lookupHashMapList, allKeys, aaiRes, newAncestry, true, aaiRes.getFullName(), jaxbContext,
					aaiExtMap);

			String eventAction = "CREATE";
			if (objectExisted[0] == true) {
				eventAction = "UPDATE";
			}

			aaiExtMap.setEventAction(eventAction);

			meVertex = newAncestry.getAncestryItems().get(aaiRes.getFullName()).getVertex();

			storeNotificationEvent(g, eventAction, meVertex, meObject, aaiRes, newAncestry, aaiResources, aaiExtMap);

			ext.runExtension(aaiExtMap.getApiVersion(),
					CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, aaiExtMap.getNamespace()),
					topObjectSimpleResourceName,
					"DynamicAdd" + objectNameForExtensions + "PostProc",
					aaiExtMap,
					false);

			int versionNumber = 0;
			String apiVersion = aaiExtMap.getApiVersion();
			if (apiVersion != null && apiVersion.matches("v\\d+")) {
				versionNumber = Integer.parseInt(apiVersion.replaceFirst("v", ""));
			}


			if (aaiExtMap.getPrecheckAddedList().size() > 0) {
				HashMap<AAIException, ArrayList<String>> exceptionList = new HashMap<AAIException, ArrayList<String>>();

				String[] chunks = aaiExtMap.getFullResourceName().split("/");

				String keyString = "";
				if (chunks.length > 0) { 
					HashMap<String,Object> thisResourceKeys = lookupHashMapList.get(aaiExtMap.getFullResourceName());

					for (Map.Entry<String,Object> ent : thisResourceKeys.entrySet()) { 
						keyString += ent.getKey() + "=" + ent.getValue() + " ";
					}
				} 

				for (AAIResponseMessage msg : aaiExtMap.getPrecheckResponseMessages().getAAIResponseMessage()) {
					ArrayList<String> templateVars = new ArrayList<String>();

					templateVars.add("PUT " + aaiRes.getSimpleName());
					templateVars.add(keyString);
					List<String> keys = new ArrayList<String>();
					templateVars.add(msg.getAaiResponseMessageResourceType());
					for (AAIResponseMessageDatum dat : msg.getAaiResponseMessageData().getAAIResponseMessageDatum()) {
						keys.add(dat.getAaiResponseMessageDatumKey() + "=" + dat.getAaiResponseMessageDatumValue());
					}
					templateVars.add(StringUtils.join(keys, ", "));
					exceptionList.put(new AAIException("AAI_0004", msg.getAaiResponseMessageResourceType()),
							templateVars);
				}
				response = Response
						.status(Status.ACCEPTED).entity(ErrorLogHelper
								.getRESTAPIInfoResponse(aaiExtMap.getHttpHeaders().getAcceptableMediaTypes(), exceptionList, logline))
								.build();
			} else if (versionNumber >= 5 && objectExisted[0] == false) {
				response = Response.status(Status.CREATED).build();
			} else {
				response = Response.ok().build();
			}

			success = true;

		} catch (AAIExceptionWithInfo e) {
			ex = e;
			success = false;
			throw ex;
		} catch (AAIException e) {
			ex = e;
			success = false;
			throw ex;
		} catch (Exception e) {
			ex = new AAIException("AAI_5105", e);
			success = false;
			throw ex;
		} finally {
			// log success or failure
			if (ex == null)
				aaiLogger.info(hLogline, true, "0");
			else {
				aaiLogger.info(hLogline, false, ex.getErrorObject().getErrorCodeString());
			}
			if (g != null) {
				if (success) {
					g.commit();
				} else {
					g.rollback();
				}
			}
		}
		
		return response;


	}

	/**
	 * Handle delete.
	 *
	 * @param lookupHashMapList the lookup hash map list
	 * @param allKeys the all keys
	 * @param resourceVersion the resource version
	 * @param aaiExtMap the aai ext map
	 * @return the response
	 * @throws AAIException the AAI exception
	 */
	public Response handleDelete(HashMap<String, LinkedHashMap<String, Object>> lookupHashMapList,
			HashMap<String, String> allKeys, String resourceVersion, AAIExtensionMap aaiExtMap) throws AAIException {

		AAIException ex = null;

		LogLine hLogline = new LogLine();
		hLogline.init(COMPONENT, aaiExtMap.getTransId(), aaiExtMap.getFromAppId(), "handleDelete");
		
		Response response = null;
		boolean success = true;
		TitanTransaction g = null;
		try {
			
			g = AAIGraph.getInstance().getGraph().newTransaction();

			aaiExtMap.setGraph(g);

			AAIResources aaiResources = org.openecomp.aai.ingestModel.IngestModelMoxyOxm.aaiResourceContainer
					.get(aaiExtMap.getApiVersion());
			AAIResource aaiRes = aaiResources.getAaiResources().get(aaiExtMap.getFullResourceName());
			if (aaiRes.isAllowDirectWrite() == false) {
				throw new AAIException("AAI_3006");
			}
			DynamicJAXBContext jaxbContext = aaiResources.getJaxbContext();

			aaiExtMap.setEventAction("DELETE");
			String topObjectSimpleResourceName = aaiResources.getAaiResources().get(aaiExtMap.getTopObjectFullResourceName()).getSimpleName();
			deleteObject(g, lookupHashMapList, allKeys, jaxbContext, resourceVersion,
					aaiRes, aaiResources, topObjectSimpleResourceName, aaiExtMap); 

			response = Response.noContent().build();
			success = true;
		} catch (AAIExceptionWithInfo e) {
			ex = e;
			success = false;
			throw ex;
		} catch (AAIException e) {
			ex = e;
			success = false;
			throw ex;
		} catch (Exception e) {
			ex = new AAIException("AAI_5105", e);
			success = false;
			throw ex;
		} finally {
			// log success or failure
			if (ex == null)
				aaiLogger.info(hLogline, true, "0");
			else {
				aaiLogger.info(hLogline, false, ex.getErrorObject().getErrorCodeString());
			}
			if (g != null) {
				if (success) {
					g.commit();
				} else {
					g.rollback();
				}
			}
		}
		
		return response;
	}

	/**
	 * Handle get by name.
	 *
	 * @param lookupHashMapList the lookup hash map list
	 * @param allKeys the all keys
	 * @param depth the depth
	 * @param aaiExtMap the aai ext map
	 * @return the response
	 * @throws AAIException the AAI exception
	 */
	public Response handleGetByName(HashMap<String, LinkedHashMap<String, Object>> lookupHashMapList,
			HashMap<String, String> allKeys, int depth, AAIExtensionMap aaiExtMap) throws AAIException {

		AAIException ex = null;

		LogLine hLogline = new LogLine();
		hLogline.init(COMPONENT, aaiExtMap.getTransId(), aaiExtMap.getFromAppId(), "handleGetByName");
		
		Response response = null;
		
		boolean success = true;
		TitanTransaction g = null;
		try {

			g = AAIGraph.getInstance().getGraph().newTransaction();
			
			aaiExtMap.setGraph(g);

			AAIResources aaiResources = org.openecomp.aai.ingestModel.IngestModelMoxyOxm.aaiResourceContainer
					.get(aaiExtMap.getApiVersion());
			AAIResource aaiRes = aaiResources.getAaiResources().get(aaiExtMap.getFullResourceName());
			if (aaiRes.isAllowDirectRead() == false) {
				throw new AAIException("AAI_3005");
			}
			DynamicJAXBContext jaxbContext = aaiResources.getJaxbContext();

			AncestryItems ancestry = new AncestryItems();

			getAncestry(g, lookupHashMapList, allKeys, aaiRes, ancestry, false, aaiRes.getFullName(), jaxbContext,
					aaiExtMap);

			TitanVertex parentVertex = null;

			if (aaiRes.getParent() != null) { 
				AAIResource parent = aaiRes.getParent();
				if (parent.getResourceType().equals("node")) {
					parentVertex = ancestry.getAncestryItems().get(parent.getFullName()).getVertex();
				} else {
					if (parent.getParent() != null && parent.getParent().getResourceType().equals("node")) { 
						parentVertex = ancestry.getAncestryItems().get(parent.getParent().getFullName()).getVertex();
					}
				}
			}

			String dnHypResourceType = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, aaiRes.getSimpleName());
			String titanDnHypResourceType = dnHypResourceType;
			if ("cvlan-tag-entry".equals(dnHypResourceType)) {
				titanDnHypResourceType = "cvlan-tag";
			}
			doCheckpoint("GET_SINGLE_BY_NAME_PRE_GET_NODES", aaiExtMap);
			ArrayList<TitanVertex> vertices = new ArrayList<TitanVertex>();
			if (parentVertex == null) { 
				vertices = DbMeth.getNodes(aaiExtMap.getTransId(), aaiExtMap.getFromAppId(), g,
						titanDnHypResourceType, lookupHashMapList.get(aaiRes.getFullName()), true, aaiRes.getApiVersion());
			} else {
				vertices = DbMeth.getConnectedNodes(aaiExtMap.getTransId(), aaiExtMap.getFromAppId(), g, 
						titanDnHypResourceType, lookupHashMapList.get(aaiRes.getFullName()), parentVertex, 
						aaiRes.getApiVersion(), false);
			}
			doCheckpoint("GET_SINGLE_BY_NAME_POST_GET_NODES", aaiExtMap);
			String dynamicClass = aaiRes.getResourceClassName();
			DynamicType meObjectType = jaxbContext.getDynamicType(dynamicClass);

			String outsideObjectDynamicClass = aaiRes.getParent().getResourceClassName();
			DynamicType parentObjectType = jaxbContext.getDynamicType(outsideObjectDynamicClass);

			DynamicEntity parentObject = parentObjectType.newDynamicEntity();

			DynamicEntity meObject = meObjectType.newDynamicEntity();

			DynamicEntity returnSingleObject = meObjectType.newDynamicEntity();

			int vertCount = 0;
			List<DynamicEntity> returnList = new ArrayList<DynamicEntity>();
			for (TitanVertex vert : vertices) {

				meObject = getObject(g, vert, vert, aaiRes, null, depth, ancestry, jaxbContext, aaiExtMap.getApiVersion(),
						aaiExtMap);

				returnSingleObject = meObject;

				returnList.add(meObject);

				vertCount++;

			}

			if (vertCount == 0) { // return not found if no vertices found
				throw new AAIException("AAI_6114", "no nodes found for " + lookupHashMapList.get(aaiRes.getFullName()).toString());
			}

			parentObject.set(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL,aaiRes.getSimpleName()), returnList);

			JAXBMarshaller marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty(JAXBMarshaller.JAXB_FORMATTED_OUTPUT, false);

			for (MediaType mt : aaiExtMap.getHttpHeaders().getAcceptableMediaTypes()) {
				if (MediaType.APPLICATION_JSON_TYPE.isCompatible(mt)) {
					marshaller.setProperty("eclipselink.media-type", "application/json");
					marshaller.setProperty("eclipselink.json.include-root", false);
					marshaller.setProperty(MarshallerProperties.JSON_MARSHAL_EMPTY_COLLECTIONS, Boolean.FALSE);
				}
			}

			StringWriter writer = new StringWriter();

			if (vertCount > 1) {
				marshaller.marshal(parentObject, writer);
				response = Response.ok(parentObject).entity(writer.toString()).build();
			} else {
				marshaller.marshal(meObject, writer);
				response = Response.ok(returnSingleObject).entity(writer.toString()).build();
			}

			success = true;
			
		} catch (AAIExceptionWithInfo e) {
			ex = e;
			success = false;
			throw ex;
		} catch (AAIException e) {
			ex = e;
			success = false;
			throw ex;
		} catch (Exception e) {
			ex = new AAIException("AAI_5105", e);
			success = false;
			throw ex;
		} finally {
			// log success or failure
			if (ex == null)
				aaiLogger.info(hLogline, true, "0");
			else {
				aaiLogger.info(hLogline, false, ex.getErrorObject().getErrorCodeString());
			}
			if (g != null) {
				if (success) {
					g.commit();
				} else {
					g.rollback();
				}
			}
		}
		
		return response;
	}

	/**
	 * Handle get single by key.
	 *
	 * @param lookupHashMapList the lookup hash map list
	 * @param allKeys the all keys
	 * @param depth the depth
	 * @param aaiExtMap the aai ext map
	 * @return the response
	 * @throws AAIException the AAI exception
	 */
	public Response handleGetSingleByKey(HashMap<String, LinkedHashMap<String, Object>> lookupHashMapList,
			HashMap<String, String> allKeys, int depth, AAIExtensionMap aaiExtMap) throws AAIException {

		AAIException ex = null;

		LogLine hLogline = new LogLine();
		hLogline.init(COMPONENT, aaiExtMap.getTransId(), aaiExtMap.getFromAppId(), "handleGetSingleByKey");
		
		DynamicEntity meObject = null;
		StringWriter writer = new StringWriter();

		boolean success = true;
		TitanTransaction g = null;
		try {
			
			g = AAIGraph.getInstance().getGraph().newTransaction();
			
			aaiExtMap.setGraph(g);

			AAIResources aaiResources = org.openecomp.aai.ingestModel.IngestModelMoxyOxm.aaiResourceContainer
					.get(aaiExtMap.getApiVersion());
			AAIResource aaiRes = aaiResources.getAaiResources().get(aaiExtMap.getFullResourceName());
			if (aaiRes.isAllowDirectRead() == false) {
				throw new AAIException("AAI_3005");
			}
			DynamicJAXBContext jaxbContext = aaiResources.getJaxbContext();

			AncestryItems ancestry = new AncestryItems();
			getAncestry(g, lookupHashMapList, allKeys, aaiRes, ancestry, true, aaiRes.getFullName(), jaxbContext, aaiExtMap);

			TitanVertex meVertex = ancestry.getAncestryItems().get(aaiExtMap.getFullResourceName()).getVertex();
			
			meObject = getObject(g, meVertex, meVertex, aaiRes, null, depth, ancestry, jaxbContext, aaiExtMap.getApiVersion(),
					aaiExtMap);

			JAXBMarshaller marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty(JAXBMarshaller.JAXB_FORMATTED_OUTPUT, false);

			for (MediaType mt : aaiExtMap.getHttpHeaders().getAcceptableMediaTypes()) {
				if (MediaType.APPLICATION_JSON_TYPE.isCompatible(mt)) {
					marshaller.setProperty("eclipselink.media-type", "application/json");
					marshaller.setProperty("eclipselink.json.include-root", false);
					marshaller.setProperty(MarshallerProperties.JSON_MARSHAL_EMPTY_COLLECTIONS, Boolean.FALSE) ;
				}
			}

			marshaller.marshal(meObject, writer);

			success = true;
			
		} catch (AAIExceptionWithInfo e) {
			ex = e;
			success = false;
			throw ex;
		} catch (AAIException e) {
			ex = e;
			success = false;
			throw ex;
		} catch (Exception e) {
			ex = new AAIException("AAI_5105", e);
			success = false;
			throw ex;
		} finally {
			// log success or failure
			if (ex == null)
				aaiLogger.info(hLogline, true, "0");
			else {
				aaiLogger.info(hLogline, false, ex.getErrorObject().getErrorCodeString());
			}
			if (g != null) {
				if (success) {
					g.commit();
				} else {
					g.rollback();
				}
			}
		}

		return Response.ok(meObject).entity(writer.toString()).build();
	}

	/**
	 * Handle get all.
	 *
	 * @param lookupHashMapList the lookup hash map list
	 * @param allKeys the all keys
	 * @param depth the depth
	 * @param aaiExtMap the aai ext map
	 * @return the response
	 * @throws AAIException the AAI exception
	 */
	public Response handleGetAll(LinkedHashMap<String, LinkedHashMap<String, Object>> lookupHashMapList,
			HashMap<String, String> allKeys, int depth, AAIExtensionMap aaiExtMap) throws AAIException {

		AAIException ex = null;

		LogLine hLogline = new LogLine();
		hLogline.init(COMPONENT, aaiExtMap.getTransId(), aaiExtMap.getFromAppId(), "handleGetAll");
		
		DynamicEntity outsideObject = null;
		StringWriter writer = new StringWriter();
		boolean success = true;
		TitanTransaction g = null;
		try {

			g = AAIGraph.getInstance().getGraph().newTransaction();
			
			aaiExtMap.setGraph(g);

			AAIResources aaiResources = org.openecomp.aai.ingestModel.IngestModelMoxyOxm.aaiResourceContainer
					.get(aaiExtMap.getApiVersion());
			AAIResource aaiRes = aaiResources.getAaiResources().get(aaiExtMap.getFullResourceName());
			if (aaiRes.isAllowDirectRead() == false) {
				throw new AAIException("AAI_3006");
			}
			DynamicJAXBContext jaxbContext = aaiResources.getJaxbContext();
			
			AncestryItems ancestry = new AncestryItems();
			getAncestry(g, lookupHashMapList, allKeys, aaiRes, ancestry, true, aaiRes.getFullName(), jaxbContext, aaiExtMap);

			String outsideObjectDynamicClass = aaiRes.getResourceClassName();
			DynamicType outsideObjectObjectType = jaxbContext.getDynamicType(outsideObjectDynamicClass);

			outsideObject = outsideObjectObjectType.newDynamicEntity();

			getObjects(g, outsideObject, aaiRes, depth, ancestry, jaxbContext, aaiExtMap);

			JAXBMarshaller marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty(JAXBMarshaller.JAXB_FORMATTED_OUTPUT, false);

			for (MediaType mt : aaiExtMap.getHttpHeaders().getAcceptableMediaTypes()) {
				if (MediaType.APPLICATION_JSON_TYPE.isCompatible(mt)) {
					marshaller.setProperty("eclipselink.media-type", "application/json");
					marshaller.setProperty("eclipselink.json.include-root", false);
					marshaller.setProperty(MarshallerProperties.JSON_MARSHAL_EMPTY_COLLECTIONS, Boolean.FALSE) ;
				}
			}

			marshaller.marshal(outsideObject, writer);

			success = true;			
		} catch (AAIExceptionWithInfo e) {
			ex = e;
			success = false;
			throw ex;
		} catch (AAIException e) {
			ex = e;
			success = false;
			throw ex;
		} catch (Exception e) {
			ex = new AAIException("AAI_5105", e);
			success = false;
			throw ex;
		} finally {
			// log success or failure
			if (ex == null)
				aaiLogger.info(hLogline, true, "0");
			else {
				aaiLogger.info(hLogline, false, ex.getErrorObject().getErrorCodeString());
			}
			
			if (g != null) {
				if (success) {
					g.commit();
				} else {
					g.rollback();
				}
			}
		}
		
		return Response.ok(outsideObject).entity(writer.toString()).build();
	}

	/**
	 * Handle update rel.
	 *
	 * @param objectFromRequest the object from request
	 * @param lookupHashMapList the lookup hash map list
	 * @param allKeys the all keys
	 * @param aaiExtMap the aai ext map
	 * @return the response
	 * @throws AAIException the AAI exception
	 */
	public Response handleUpdateRel(String objectFromRequest,
			HashMap<String, LinkedHashMap<String, Object>> lookupHashMapList, HashMap<String, String> allKeys,
			AAIExtensionMap aaiExtMap) throws AAIException {

		AAIException ex = null;

		LogLine hLogline = new LogLine();
		hLogline.init(COMPONENT, aaiExtMap.getTransId(), aaiExtMap.getFromAppId(), "handleUpdateRel");
		
		boolean success = true;
		TitanTransaction g = null;
		
		try {
			g = AAIGraph.getInstance().getGraph().newTransaction();
			
			aaiExtMap.setGraph(g);

			AAIResources aaiResources = org.openecomp.aai.ingestModel.IngestModelMoxyOxm.aaiResourceContainer
					.get(aaiExtMap.getApiVersion());
			AAIResource aaiRes = aaiResources.getAaiResources().get(aaiExtMap.getFullResourceName());

			DynamicJAXBContext jaxbContext = aaiResources.getJaxbContext();

			AncestryItems ancestry = new AncestryItems();
			
			getAncestry(g, lookupHashMapList, allKeys, aaiRes, ancestry, true, aaiRes.getFullName(), jaxbContext,
					aaiExtMap);
			JAXBUnmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			String dynamicClass;
			if ("v2".equals(aaiExtMap.getApiVersion()))  
				dynamicClass = "inventory.aai.openecomp.org.Relationship";
			else 
				dynamicClass = "inventory.aai.openecomp.org." + aaiExtMap.getApiVersion() + ".Relationship";

			if (aaiExtMap.getHttpServletRequest().getContentType() == null || 
					aaiExtMap.getHttpServletRequest().getContentType().contains("application/json")) {
				unmarshaller.setProperty("eclipselink.media-type", "application/json");
				unmarshaller.setProperty("eclipselink.json.include-root", false);
			}

			Class<? extends DynamicEntity> resultClass = jaxbContext.newDynamicEntity(dynamicClass).getClass();

			StringReader reader = new StringReader(objectFromRequest);

			DynamicEntity relationship = (DynamicEntity) unmarshaller.unmarshal(new StreamSource(reader), resultClass).getValue();

			TitanVertex meVertex = ancestry.getAncestryItems().get(aaiExtMap.getFullResourceName()).getVertex();

			RelationshipGraph.updRelationship(g, meVertex, jaxbContext, relationship, aaiExtMap);

			success = true;

		} catch (AAIExceptionWithInfo e) {
			ex = e;
			success = false;
			throw ex;
		} catch (AAIException e) {
			ex = e;
			success = false;
			throw ex;
		} catch (Exception e) {
			ex = new AAIException("AAI_5105", e);
			success = false;
			throw ex;
		} finally {
			// log success or failure
			if (g != null) {
				if (success) {
					g.commit();
				} else {
					g.rollback();
				}
			}
			if (ex == null)
				aaiLogger.info(hLogline, true, "0");
			else {
				aaiLogger.info(hLogline, false, ex.getErrorObject().getErrorCodeString());
			}
		}
		
		return Response.ok().build();

	}

	/**
	 * Handle delete rel.
	 *
	 * @param objectFromRequest the object from request
	 * @param lookupHashMapList the lookup hash map list
	 * @param allKeys the all keys
	 * @param aaiExtMap the aai ext map
	 * @return the response
	 * @throws AAIException the AAI exception
	 */
	public Response handleDeleteRel(String objectFromRequest,
			HashMap<String, LinkedHashMap<String, Object>> lookupHashMapList, HashMap<String, String> allKeys,
			AAIExtensionMap aaiExtMap) throws AAIException {

		AAIException ex = null;

		LogLine hLogline = new LogLine();
		hLogline.init(COMPONENT, aaiExtMap.getTransId(), aaiExtMap.getFromAppId(), "handleDeleteRel");

		Response response = null;
		boolean success = true;
		TitanTransaction g = null;
		// boolean hasPayload = false;
		try {
			
			g = AAIGraph.getInstance().getGraph().newTransaction();
			
			aaiExtMap.setGraph(g);

			AAIResources aaiResources = org.openecomp.aai.ingestModel.IngestModelMoxyOxm.aaiResourceContainer
					.get(aaiExtMap.getApiVersion());
			AAIResource aaiRes = aaiResources.getAaiResources().get(aaiExtMap.getFullResourceName());

			DynamicJAXBContext jaxbContext = aaiResources.getJaxbContext();

			AncestryItems ancestry = new AncestryItems();
			
			getAncestry(g, lookupHashMapList, allKeys, aaiRes, ancestry, true, aaiRes.getFullName(), jaxbContext,
					aaiExtMap);
			
			DynamicEntity relationship = null;
			
			String dynamicClass;
			if ("v2".equals(aaiExtMap.getApiVersion())) { 
				dynamicClass = "inventory.aai.openecomp.org.Relationship";
			} else { 
				dynamicClass = "inventory.aai.openecomp.org." + aaiExtMap.getApiVersion() + ".Relationship";
			}
			if (objectFromRequest.length() > 0) { 
				// hasPayload = true;
				JAXBUnmarshaller unmarshaller = jaxbContext.createUnmarshaller();

				if (aaiExtMap.getHttpServletRequest().getContentType().contains("application/json")) {
					unmarshaller.setProperty("eclipselink.media-type", "application/json");
					unmarshaller.setProperty("eclipselink.json.include-root", false);
				}

				Class<? extends DynamicEntity> resultClass = jaxbContext.newDynamicEntity(dynamicClass).getClass();

				StringReader reader = new StringReader(objectFromRequest);

				relationship = (DynamicEntity) unmarshaller.unmarshal(new StreamSource(reader), resultClass).getValue();

			} else { 
				throw new AAIException("AAI_3102", "You must supply a relationship");
			}
			TitanVertex meVertex = ancestry.getAncestryItems().get(aaiExtMap.getFullResourceName()).getVertex();

			RelationshipGraph.delRelationship(g ,meVertex, jaxbContext, relationship, aaiExtMap);

			response = Response.noContent().build();
			success = true;
		} catch (AAIExceptionWithInfo e) {
				ex = e;
				success = false;
				throw ex;
		} catch (AAIException e) {
			ex = e;
			success = false;
			throw ex;
		} catch (Exception e) {
			ex = new AAIException("AAI_5105", e);
			success = false;
			throw ex;
		} finally {
			// log success or failure
			if (ex == null)
				aaiLogger.info(hLogline, true, "0");
			else {
				aaiLogger.info(hLogline, false, ex.getErrorObject().getErrorCodeString());
			}
			if (g != null) {
				if (success) {
					g.commit();
				} else {
					g.rollback();
				}
			}
		}
		
		return response;

	}


	/**
	 * Handle example.
	 *
	 * @param aaiRes the aai res
	 * @param aaiExtMap the aai ext map
	 * @param singleton the singleton
	 * @param getChildren the get children
	 * @return the response
	 * @throws AAIException the AAI exception
	 */
	public Response handleExample(AAIResource aaiRes, AAIExtensionMap aaiExtMap, boolean singleton, boolean getChildren)
			throws AAIException {

		AAIException ex = null;

		LogLine hLogline = new LogLine();
		hLogline.init(COMPONENT, aaiExtMap.getTransId(), aaiExtMap.getFromAppId(), "handleExample");
		
		try {
			Response response = null;
			if (aaiRes.getResourceType().equals("container")) {

				AAIResources aaiResources = org.openecomp.aai.ingestModel.IngestModelMoxyOxm.aaiResourceContainer
						.get(aaiExtMap.getApiVersion());
				DynamicJAXBContext jaxbContext = aaiResources.getJaxbContext();

				PojoUtils pu = new PojoUtils();
				
				
				DynamicType baseObjectType = jaxbContext.getDynamicType(aaiRes.getSimpleName());
				DynamicEntity baseObject = baseObjectType.newDynamicEntity();

				DynamicEntity singletonObject = null;

				AAIResources children = aaiRes.getChildren();

				HashMap<String, AAIResource> childResHash = children.getAaiResources();

				for (Map.Entry<String, AAIResource> entry : childResHash.entrySet()) {

					AAIResource aaiChildRes = entry.getValue();

					DynamicType childObjectType = jaxbContext.getDynamicType(aaiChildRes.getSimpleName());
					DynamicEntity childObject = childObjectType.newDynamicEntity();

					pu.getDynamicExampleObject(childObject, aaiChildRes, singleton);

					// attach this object to its parent
					List<DynamicEntity> dynamicEntityList = new ArrayList<DynamicEntity>();

					if (aaiChildRes.getChildren().getAaiResources().size() > 0 && getChildren == true) {
						getExampleWithChildren(aaiChildRes, childObject, singleton, jaxbContext, aaiExtMap);
					}
					singletonObject = childObject;

					dynamicEntityList.add(childObject);

					baseObject.set(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL,aaiChildRes.getSimpleName()), dynamicEntityList);
				}
				JAXBMarshaller marshaller = jaxbContext.createMarshaller();
				marshaller.setProperty(JAXBMarshaller.JAXB_FORMATTED_OUTPUT, false);

				for (MediaType mt : aaiExtMap.getHttpHeaders().getAcceptableMediaTypes()) {
					if (MediaType.APPLICATION_JSON_TYPE.isCompatible(mt)) {
						marshaller.setProperty("eclipselink.media-type", "application/json");
						marshaller.setProperty(MarshallerProperties.JSON_MARSHAL_EMPTY_COLLECTIONS, Boolean.FALSE);
						marshaller.setProperty("eclipselink.json.include-root", false);
					}
				}

				if (singleton == true) { 
					StringWriter writer = new StringWriter();
					marshaller.marshal(singletonObject, writer);
					response = Response.ok(singletonObject).entity(writer.toString()).build();
				} else { 
					StringWriter writer = new StringWriter();
					marshaller.marshal(baseObject, writer);

					response = Response.ok(baseObject).entity(writer.toString()).build();
				}
			}
			return response;
		} catch (AAIExceptionWithInfo e) {
			ex = e;
			throw ex;
		} catch (AAIException e) {
			ex = e;
			throw ex;
		} catch (Exception e) {
			ex = new AAIException("AAI_5105", e);
			throw ex;
		} finally {
			AAIGraph.getInstance().graphRollback();
			// log success or failure
			if (ex == null)
				aaiLogger.info(hLogline, true, "0");
			else {
				aaiLogger.info(hLogline, false, ex.getErrorObject().getErrorCodeString());
			}
		}
	}

	/**
	 * Gets the example with children.
	 *
	 * @param aaiRes the aai res
	 * @param baseObject the base object
	 * @param singleton the singleton
	 * @param jaxbContext the jaxb context
	 * @param aaiExtMap the aai ext map
	 * @return the example with children
	 * @throws AAIException the AAI exception
	 * @throws ClassNotFoundException the class not found exception
	 * @throws InstantiationException the instantiation exception
	 * @throws IllegalAccessException the illegal access exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws SecurityException the security exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 */
	public void getExampleWithChildren(AAIResource aaiRes, DynamicEntity baseObject, boolean singleton, DynamicJAXBContext jaxbContext,
			AAIExtensionMap aaiExtMap)
					throws AAIException, ClassNotFoundException, InstantiationException, IllegalAccessException,
					NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {

		AAIResources children = aaiRes.getChildren();

		if (!(children.getAaiResources().size() > 0)) {
			return;
		}

		PojoUtils pu = new PojoUtils();

		HashMap<String, AAIResource> childResHash = children.getAaiResources();

		for (Map.Entry<String, AAIResource> entry : childResHash.entrySet()) {

			AAIResource aaiChildRes = entry.getValue();

			if (aaiChildRes.getResourceType().equals("container")) {

				DynamicType childObjectType = jaxbContext.getDynamicType(aaiChildRes.getSimpleName());				

				DynamicEntity childObject = childObjectType.newDynamicEntity();

				if (aaiChildRes.getChildren().getAaiResources().size() > 0) {
					getExampleWithChildren(aaiChildRes, childObject, singleton, jaxbContext, aaiExtMap);
				}

				baseObject.set(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, aaiChildRes.getSimpleName()), childObject);

			} else if (aaiChildRes.getResourceType().equals("node")) {

				DynamicType childObjectType = jaxbContext.getDynamicType(aaiChildRes.getSimpleName());
				DynamicEntity childObject = childObjectType.newDynamicEntity();

				pu.getDynamicExampleObject(childObject, aaiChildRes, singleton);

				// attach this object to its parent

				List<DynamicEntity> dynamicEntityList = new ArrayList<DynamicEntity>();

				if (aaiChildRes.getChildren().getAaiResources().size() > 0) {
					getExampleWithChildren(aaiChildRes, childObject, singleton, jaxbContext, aaiExtMap);
				}

				dynamicEntityList.add(childObject);

				baseObject.set(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL,aaiChildRes.getSimpleName()), dynamicEntityList);

			}
		}
	}

	/**
	 * Put object.
	 *
	 * @param g the g
	 * @param aaiRes the aai res
	 * @param allKeys the all keys
	 * @param lookupHashMapList the lookup hash map list
	 * @param meObject the me object
	 * @param parentVertex the parent vertex
	 * @param objectExisted the object existed
	 * @param jaxbContext the jaxb context
	 * @param ancestry the ancestry
	 * @param aaiExtMap the aai ext map
	 * @return the titan vertex
	 * @throws AAIException the AAI exception
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws ClassNotFoundException the class not found exception
	 * @throws InstantiationException the instantiation exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws SecurityException the security exception
	 */
	public TitanVertex putObject(TitanTransaction g, AAIResource aaiRes, HashMap<String, String> allKeys, HashMap<String, LinkedHashMap<String, Object>> lookupHashMapList,
			DynamicEntity meObject, TitanVertex parentVertex, boolean[] objectExisted, DynamicJAXBContext jaxbContext,
			AncestryItems ancestry, AAIExtensionMap aaiExtMap)
					throws AAIException, IllegalAccessException, IllegalArgumentException, InvocationTargetException,
					ClassNotFoundException, InstantiationException, NoSuchMethodException, SecurityException {

		String dnHypResourceType = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, aaiRes.getSimpleName());
		String titanDnHypResourceType = dnHypResourceType;

		// we still have a special case for cvlan-tag-entry
		if ("cvlan-tag-entry".equals(dnHypResourceType)) {
			titanDnHypResourceType = "cvlan-tag";
		}
		
		TitanVertex meVertex = parentVertex;
		if ("node".equals(aaiRes.getResourceType())) {

			PojoUtils pu = new PojoUtils();
			HashMap<String, Object> propHash = new HashMap<String, Object>();

			// this fills in the keys from the URI, if there are any (there won't be with children)
			if (lookupHashMapList != null) {
				pu.setPropHashKeys(aaiRes, propHash, aaiRes.getPropertyDataTypeMap(),
						lookupHashMapList.get(aaiRes.getFullName()), aaiExtMap);
			}

			// map the properties from the payload to the attribute hash for use with graph layer
			pu.fillPropHashFromDynamicObject(aaiRes, meObject, propHash, aaiRes.getPropertyDataTypeMap(), aaiExtMap);

			// check required fields
			checkRequiredProps(dnHypResourceType, aaiRes.getRequiredFields(), propHash);

			// put the vertex in the graph

			if (AAIConfig.get("aai.use.unique.key", "false").equals("true")) {

				String newUniqueKey = allKeys.get(aaiRes.getFullName());
				propHash.put("aai-unique-key", newUniqueKey);
				AncestryItem a = ancestry.getAncestryItems().get(aaiRes.getFullName());

				TitanVertex existingVert = null;
				if (a != null) { 
					existingVert = a.getVertex();
				}
				doCheckpoint("PUT_OBJECT_PRE_PERSIST_AAI_NODES_WITHOUT_VERTEX", aaiExtMap);
				meVertex = DbMeth.persistAaiNode(aaiExtMap.getTransId(), aaiExtMap.getFromAppId(), g,
						titanDnHypResourceType, propHash, true, parentVertex, aaiExtMap.getApiVersion(), objectExisted, existingVert);
				doCheckpoint("PUT_OBJECT_POST_PERSIST_AAI_NODES_WITHOUT_VERTEX", aaiExtMap);
			} else {
				doCheckpoint("PUT_OBJECT_PRE_PERSIST_AAI_NODES_WITH_VERTEX", aaiExtMap);
				meVertex = DbMeth.persistAaiNode(aaiExtMap.getTransId(), aaiExtMap.getFromAppId(), g,
						titanDnHypResourceType, propHash, true, parentVertex, aaiExtMap.getApiVersion(), objectExisted);
				doCheckpoint("PUT_OBJECT_POST_PERSIST_AAI_NODES_WITH_VERTEX", aaiExtMap);
			}

			// attach this vertex to its parent
			if (parentVertex != null) {
				doCheckpoint("PUT_OBJECT_PRE_PERSIST_AAI_EDGE", aaiExtMap);
				DbMeth.persistAaiEdge(aaiExtMap.getTransId(), aaiExtMap.getFromAppId(), g, parentVertex, meVertex,
						aaiExtMap.getApiVersion(), "parentChild");
				doCheckpoint("PUT_OBJECT_POST_PERSIST_AAI_EDGE", aaiExtMap);
			}
			try { 
				DynamicEntity relationshipList = meObject.get("relationshipList");
				RelationshipGraph.updRelationships(g, meVertex, jaxbContext, relationshipList, aaiExtMap);
			} catch (DynamicException e) { // it's ok, they don't all have relationshipList 
				;
			} catch (Exception e) { 
				throw e;
			}
		}
		if (aaiRes.getChildren().getAaiResources().size() > 0 || aaiRes.getRecurseToResource() != null) {
			// reflect on the object to get children, then put the child

			HashMap<String, AAIResource> children = aaiRes.getChildren().getAaiResources();

			if (aaiRes.getRecurseToResource() != null) { 
				AAIResource recurseToResource = aaiRes.getRecurseToResource();
				children.put(recurseToResource.getFullName(), recurseToResource);
			}
			
			for (Entry<String, AAIResource> ent : children.entrySet()) {
				AAIResource aaiChildRes = ent.getValue();

				if ("container".equals(aaiChildRes.getResourceType())) {

					// make a new container and attach it to the baseObject

					DynamicEntity childObject = meObject
							.get(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, aaiChildRes.getSimpleName()));

					if (childObject != null) { 
						if (aaiChildRes.getChildren().getAaiResources().size() > 0) {
							// add this container name to the key, in lower case, natch


							allKeys.put(aaiChildRes.getFullName(), 
									allKeys.get(aaiRes.getFullName()) + "/" + 
											CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN,aaiChildRes.getSimpleName()).toLowerCase());

							putObject(g, aaiChildRes, allKeys, null, childObject, meVertex, new boolean[1], jaxbContext, ancestry, aaiExtMap);
						}
					}
				} else if ("node".equals(aaiChildRes.getResourceType())) {

					if (meObject != null) {

						String childDnHypResourceType = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN,
								aaiChildRes.getSimpleName());
						// expect to find this attached to the node

						// for now, i think there's only one of these.
						String titanChildDnHypResourceType = childDnHypResourceType;
						if ("cvlan-tag-entry".equals(childDnHypResourceType)) {
							titanChildDnHypResourceType = "cvlan-tag";
						}
						List<DynamicEntity> objList = meObject
								.get(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, aaiChildRes.getSimpleName()));
						doCheckpoint("PUT_OBJECT_PRE_GET_CONNECTED_NODES", aaiExtMap);
						List<TitanVertex> deleteCandidateList = DbMeth.getConnectedChildren(aaiExtMap.getTransId(), aaiExtMap.getFromAppId(), g, meVertex, titanChildDnHypResourceType); 
						doCheckpoint("PUT_OBJECT_POST_GET_CONNECTED_NODES", aaiExtMap);
						if (objList != null) {

							for (DynamicEntity childObjFromList : objList) {

								// figure out the keys from the payload and apply it to the allKeys hash				
								AAIResourceKeys aaiResKeys = aaiChildRes.getAaiResourceKeys();
								String childKey = allKeys.get(aaiRes.getFullName()) + "/" + 
										CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN,aaiChildRes.getSimpleName()).toLowerCase();
								for (AAIResourceKey rk : aaiResKeys.getAaiResourceKey()) { 
									String dnCamKeyName = CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL,rk.getKeyName());
									childKey += "/" + childObjFromList.get(dnCamKeyName);
								}

								// this will potentially overwrite the last one, but that's ok since
								// we want to use it the next time we call persist AAI node
								// all children should persisted under this node before this goes away
								allKeys.put(aaiChildRes.getFullName(), childKey);


								TitanVertex vert = putObject(g, aaiChildRes, allKeys, null, childObjFromList, meVertex,
										new boolean[1], jaxbContext, ancestry, aaiExtMap);
								deleteCandidateList.remove(vert);

							}
						}
						for (TitanVertex deleteVertex : deleteCandidateList) {
							String resourceVersion = "";
							if (deleteVertex.property("resource-version").orElse(null) != null) {
								resourceVersion = deleteVertex.<String>property("resource-version").orElse(null);
							}
							doCheckpoint("PUT_OBJECT_PRE_DELETE_EXTRA_NODE", aaiExtMap);
							DbMeth.removeAaiNode(aaiExtMap.getTransId(), aaiExtMap.getFromAppId(), g,
									deleteVertex, "USE_DEFAULT", aaiExtMap.getApiVersion(), resourceVersion);
							doCheckpoint("PUT_OBJECT_POST_DELETE_EXTRA_NODE", aaiExtMap);
						}
					}
				}
			}
		}
		return meVertex;
	}

	/**
	 * Delete object.
	 *
	 * @param g the g
	 * @param lookupHashMapList the lookup hash map list
	 * @param allKeys the all keys
	 * @param jaxbContext the jaxb context
	 * @param resourceVersion the resource version
	 * @param aaiRes the aai res
	 * @param aaiResources the aai resources
	 * @param topObjectSimpleResourceName the top object simple resource name
	 * @param aaiExtMap the aai ext map
	 * @throws AAIException the AAI exception
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 */
	public void deleteObject(TitanTransaction g, HashMap<String, LinkedHashMap<String, Object>> lookupHashMapList,
			HashMap<String, String> allKeys, DynamicJAXBContext jaxbContext, String resourceVersion,
			AAIResource aaiRes, AAIResources aaiResources, String topObjectSimpleResourceName, AAIExtensionMap aaiExtMap) throws AAIException, UnsupportedEncodingException {  
	
		AncestryItems ancestry = new AncestryItems();
		getAncestry(g, lookupHashMapList, allKeys, aaiRes, ancestry, true, aaiRes.getFullName(), jaxbContext, aaiExtMap);
	
		TitanVertex meVertex = ancestry.getAncestryItems().get(aaiRes.getFullName()).getVertex();
	
		String objectNameForExtensions = aaiRes.getFullName().replace("/", "");
		aaiRes.getSimpleName();
	
		ExtensionController ext = new ExtensionController();
		ext.runExtension(aaiExtMap.getApiVersion(),
				CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, aaiRes.getNamespace()),
				topObjectSimpleResourceName,
				"DynamicDel" + objectNameForExtensions + "PreProc",
				aaiExtMap,
				true);
	
		storeNotificationEvent(g, "DELETE", meVertex, null, aaiRes, ancestry, aaiResources, aaiExtMap);
	
		doCheckpoint("HANDLE_DELETE_PRE_DELETE", aaiExtMap);
		DbMeth.removeAaiNode(aaiExtMap.getTransId(), aaiExtMap.getFromAppId(), g, meVertex, "USE_DEFAULT",
				aaiExtMap.getApiVersion(), resourceVersion);
	
		ext.runExtension(aaiExtMap.getApiVersion(),
				CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, aaiRes.getNamespace()),
				topObjectSimpleResourceName,
				"DynamicDel" + objectNameForExtensions + "PostProc",
				aaiExtMap,
				false);
	
		doCheckpoint("HANDLE_DELETE_POST_DELETE", aaiExtMap);
	
	}
	
	/**
	 * Gets the objects.
	 *
	 * @param g the g
	 * @param outsideObject the outside object
	 * @param aaiRes the aai res
	 * @param depth the depth
	 * @param ancestry the ancestry
	 * @param jaxbContext the jaxb context
	 * @param aaiExtMap the aai ext map
	 * @return the objects
	 * @throws ClassNotFoundException the class not found exception
	 * @throws InstantiationException the instantiation exception
	 * @throws IllegalAccessException the illegal access exception
	 * @throws AAIException the AAI exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws SecurityException the security exception
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 */
	public void getObjects(TitanTransaction g, DynamicEntity outsideObject, AAIResource aaiRes, int depth, AncestryItems ancestry,
			DynamicJAXBContext jaxbContext, AAIExtensionMap aaiExtMap)
					throws ClassNotFoundException, InstantiationException, IllegalAccessException, AAIException,
					IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException,
					UnsupportedEncodingException {

		if (aaiRes.getResourceType().equals("container")) {

			AAIResources children = aaiRes.getChildren();

			if (!(children.getAaiResources().size() > 0)) {
				return;
			}
					
			HashMap<String, AAIResource> childResHash = children.getAaiResources();
			if (aaiRes.getRecurseToResource() != null) { 
				AAIResource recurseToResource = aaiRes.getRecurseToResource();
				childResHash.put(recurseToResource.getFullName(), recurseToResource);
			}
			for (Map.Entry<String, AAIResource> entry : childResHash.entrySet()) {

				AAIResource aaiChildRes = entry.getValue();

				DynamicType childObjectType = jaxbContext.getDynamicType(aaiChildRes.getSimpleName());

				String dnHypResourceType = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN,
						aaiChildRes.getSimpleName());
				String titanDnHypResourceType = dnHypResourceType;
				if ("cvlan-tag-entry".equals(dnHypResourceType)) {
					titanDnHypResourceType = "cvlan-tag";
				}

				TitanVertex parentVertex = null;

				if (aaiRes.getParent() != null) { 
					AAIResource parent = aaiRes.getParent();
					if (parent.getResourceType().equals("node")) {
						parentVertex = ancestry.getAncestryItems().get(parent.getFullName()).getVertex();
					} else {
						if (parent.getParent() != null && parent.getParent().getResourceType().equals("node")) { 
							parentVertex = ancestry.getAncestryItems().get(parent.getParent().getFullName()).getVertex();
						}
					}
				}

				doCheckpoint("GET_OBJECTS_PRE_GET_NODES", aaiExtMap);
				ArrayList<TitanVertex> vertices = new ArrayList<TitanVertex>();
				if (parentVertex == null) { 
					vertices = DbMeth.getNodes(aaiExtMap.getTransId(), aaiExtMap.getFromAppId(),
							g, titanDnHypResourceType, new HashMap<String, Object>(), true,
							aaiChildRes.getApiVersion());
				} else {
					vertices = DbMeth.getConnectedChildren(aaiExtMap.getTransId(), aaiExtMap.getFromAppId(), g, parentVertex, titanDnHypResourceType);
				}
				doCheckpoint("GET_OBJECTS_POST_GET_NODES", aaiExtMap);
				List<DynamicEntity> childDynamicEntityList = new ArrayList<DynamicEntity>();

				for (TitanVertex vert : vertices) {

					DynamicEntity childObject = childObjectType.newDynamicEntity();

					childObject = getObject(g, vert, vert, aaiChildRes, null, depth, ancestry, jaxbContext, aaiExtMap.getApiVersion(),
							aaiExtMap);

					childDynamicEntityList.add(childObject);
				}
				outsideObject.set(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL,aaiChildRes.getSimpleName()), childDynamicEntityList);
			}
		}
	}


	/**
	 * Gets the object.
	 *
	 * @param g the g
	 * @param meVertex the me vertex
	 * @param baseVertex the base vertex
	 * @param aaiRes the aai res
	 * @param topologyUntilThisOne the topology until this one
	 * @param depth the depth
	 * @param ancestry the ancestry
	 * @param jaxbContext the jaxb context
	 * @param apiVersion the api version
	 * @param aaiExtMap the aai ext map
	 * @return the object
	 * @throws AAIException the AAI exception
	 */
	public DynamicEntity getObject(TitanTransaction g, TitanVertex meVertex, TitanVertex baseVertex, AAIResource aaiRes,
			String topologyUntilThisOne, int depth, AncestryItems ancestry,
			DynamicJAXBContext jaxbContext, String apiVersion, AAIExtensionMap aaiExtMap) throws AAIException {

		DynamicEntity meObject;
		try {

			DynamicType meObjectType = jaxbContext.getDynamicType(aaiRes.getResourceClassName());

			meObject = meObjectType.newDynamicEntity();

			PojoUtils pu = new PojoUtils();

			boolean doTopology = false;
			if (topologyUntilThisOne != null) {
				doTopology = true;
			}

			if (doTopology == true) {
				if (aaiRes.getFullName().equals(topologyUntilThisOne)) {
					doTopology = false;
				}
			}
			
			if (doTopology == true) {
				String notificationVersion = AAIConfig.get("aai.notification.current.version");

				AAIResources aaiNotificationResources = org.openecomp.aai.ingestModel.IngestModelMoxyOxm.aaiResourceContainer
						.get(notificationVersion);

				DynamicJAXBContext notificationJaxbContext = aaiNotificationResources.getJaxbContext();

				meObject = unpackAncestry(g, ancestry, topologyUntilThisOne, notificationJaxbContext, aaiExtMap);
			} else {
				pu.getAaiDynamicObjectFromVertex(aaiRes, meObject, baseVertex, aaiRes.getPropertyDataTypeMap());

				DynamicEntity newRelationshipList = RelationshipGraph.getRelationships(g, meVertex, apiVersion, aaiExtMap);

				try { 
					meObject.set("relationshipList", newRelationshipList);
				} catch (DynamicException e) { 
					;
				}
			
				if (aaiRes.getChildren() != null || aaiRes.getRecurseToResource() != null) { 

					if (depth == -1 || depth > 0) {
						int nextDepth = depth;
						if (depth != -1) {
							nextDepth--;
						}
						
						if (aaiRes.getChildren().getAaiResources().size() > 0 || aaiRes.getRecurseToResource() != null) {
							getDescendants(g, baseVertex, meVertex, nextDepth, meObject, aaiRes, ancestry,
									apiVersion, jaxbContext, aaiExtMap);
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new AAIException("AAI_3000", e);
		}
		return meObject;

	}

	/**
	 * Gets the descendants.
	 *
	 * @param g the g
	 * @param baseVertex the base vertex
	 * @param stopTopologyVertex the stop topology vertex
	 * @param depth the depth
	 * @param baseObject the base object
	 * @param aaiRes the aai res
	 * @param ancestry the ancestry
	 * @param apiVersion the api version
	 * @param jaxbContext the jaxb context
	 * @param aaiExtMap the aai ext map
	 * @return the descendants
	 * @throws AAIException the AAI exception
	 * @throws ClassNotFoundException the class not found exception
	 * @throws InstantiationException the instantiation exception
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws SecurityException the security exception
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 */
	private void getDescendants(TitanTransaction g, TitanVertex baseVertex, TitanVertex stopTopologyVertex, int depth, DynamicEntity baseObject,
			AAIResource aaiRes, AncestryItems ancestry,
			String apiVersion, DynamicJAXBContext jaxbContext, AAIExtensionMap aaiExtMap) throws AAIException,
			ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException,
			InvocationTargetException, NoSuchMethodException, SecurityException, UnsupportedEncodingException {


		if (aaiRes.getChildren() == null) { 
			return;
		}

		AAIResources children = aaiRes.getChildren();

		PojoUtils pu = new PojoUtils();

		HashMap<String, AAIResource> childResHash = children.getAaiResources();
		
		if (aaiRes.getRecurseToResource() != null) { 
			AAIResource recurseToResource = aaiRes.getRecurseToResource();
			childResHash.put(recurseToResource.getFullName(), recurseToResource);
		}
			
		// check to see if we're stopping topology at this level.  this prevents us from digging 
		// into the sibling objects which we're not supposed to include here

		for (Map.Entry<String, AAIResource> entry : childResHash.entrySet()) {

			AAIResource aaiChildRes = entry.getValue();

			if (aaiChildRes.getResourceType().equals("container")) {
				// make a new container and attach it to the baseObject

				DynamicType childObjectType = jaxbContext.getDynamicType(aaiChildRes.getResourceClassName());

				DynamicEntity childObject = childObjectType.newDynamicEntity();

				baseObject.set(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, aaiChildRes.getSimpleName()),
						childObject);
				if (aaiChildRes.getChildren() != null || aaiChildRes.getRecurseToResource() != null) { 
					if (aaiChildRes.getChildren().getAaiResources().size() > 0 || aaiChildRes.getRecurseToResource() != null) {
						getDescendants(g, baseVertex, stopTopologyVertex, depth, childObject, aaiChildRes, 
								ancestry, apiVersion, jaxbContext, aaiExtMap);
					}
				} 
			} else if (aaiChildRes.getResourceType().equals("node")) {

				String dnHypResourceType = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN,
						aaiChildRes.getSimpleName());
				// expect to find this attached to the node

				// for now, i think there's only one of these.
				String titanDnHypResourceType = dnHypResourceType;
				if ("cvlan-tag-entry".equals(dnHypResourceType)) {
					titanDnHypResourceType = "cvlan-tag";
				}
				List<DynamicEntity> childList = new ArrayList<DynamicEntity>();
				// load the set of children into childList, and then attach it
				// to the parent

				if (depth == -1 || depth >= 0) {
					int nextDepth = depth;
					if (depth != -1) {
						nextDepth--;
					}
					if (depth == 0) { 
						// we want to stop when we get to zero, but AFTER we process these connected nodes
						nextDepth = -2;
					}

					doCheckpoint("GET_DESCENDANTS_PRE_GET_CONNECTED_NODES", aaiExtMap);
					ArrayList<TitanVertex> vertices = DbMeth.getConnectedChildren(aaiExtMap.getTransId(), aaiExtMap.getFromAppId(), g, baseVertex, titanDnHypResourceType);
					
					doCheckpoint("GET_DESCENDANTS_POST_GET_CONNECTED_NODES", aaiExtMap);
					for (TitanVertex childVert : vertices) {

						DynamicType childObjectType = jaxbContext.getDynamicType(aaiChildRes.getResourceClassName());

						DynamicEntity childObject = childObjectType.newDynamicEntity();

						pu.getAaiDynamicObjectFromVertex(aaiChildRes, childObject, childVert,
								aaiChildRes.getPropertyDataTypeMap());

						// add this child object to the list
						childList.add(childObject);
						try {
							childObject.set("relationshipList", RelationshipGraph.getRelationships(g, childVert, apiVersion, aaiExtMap));
						} catch (DynamicException e) { 
							;
						} catch (Exception e1) { 
							throw e1;
						}

						if (nextDepth >= 0 || nextDepth == -1) { 
							if (aaiChildRes.getChildren().getAaiResources().size() > 0 || aaiChildRes.getRecurseToResource() != null) {
								getDescendants(g, childVert, stopTopologyVertex, nextDepth, childObject, aaiChildRes, 
										ancestry, apiVersion, jaxbContext, aaiExtMap);
							}
						}
					}
				}
				baseObject.set(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, aaiChildRes.getSimpleName()),
						childList);
			}
		}
	}

	/**
	 * Gets the ancestry.
	 *
	 * @param g the g
	 * @param lookupHashMapList the lookup hash map list
	 * @param allKeys the all keys
	 * @param aaiRes the aai res
	 * @param ancestry the ancestry
	 * @param includeSelf the include self
	 * @param selfFullName the self full name
	 * @param jaxbContext the jaxb context
	 * @param aaiExtMap the aai ext map
	 * @return the ancestry
	 * @throws AAIException the AAI exception
	 */
	public void getAncestry(TitanTransaction g, HashMap<String, LinkedHashMap<String, Object>> lookupHashMapList,
			HashMap<String, String> allKeys, AAIResource aaiRes, AncestryItems ancestry, boolean includeSelf,
			String selfFullName, DynamicJAXBContext jaxbContext, AAIExtensionMap aaiExtMap) throws AAIException {


		AAIResource parent = aaiRes.getParent();

		if (parent != null && !parent.getFullName().equals("/Inventory")) {
			getAncestry(g, lookupHashMapList, allKeys, parent, ancestry, includeSelf, selfFullName, jaxbContext,
					aaiExtMap);
		}
		PojoUtils pu = new PojoUtils();
		if (aaiRes.getResourceType().equals("node")) {
			// we're at the top, get me the vertex
			// it'll be the hyphencased node name, unless it's a cvlan-tag, i
			// think?
			String dnHypResourceType = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, aaiRes.getSimpleName());
			if (dnHypResourceType.equals("cvlan-tag-entry")) { 
				dnHypResourceType = "cvlan-tag";
			}
			HashMap<String, Object> propHash = lookupHashMapList.get(aaiRes.getFullName());
			if (propHash != null) {

				boolean stop = false;
				if (includeSelf == false && aaiRes.getFullName().equals(selfFullName)) {
					stop = true;
				}

				// try first to the get the unique node by its key

				TitanVertex vert = null;
				if (AAIConfig.get("aai.use.unique.key", "false").equals("true")) {
					try {
						HashMap<String,Object> keyPropHash = new HashMap<String,Object>();

						keyPropHash.put("aai-unique-key", allKeys.get(aaiRes.getFullName()));
						String key = allKeys.get(aaiRes.getFullName());
						doCheckpoint("GET_ANCESTRY_PRE_GET_BY_KEY|" + key, aaiExtMap);
						DbMeth d = new DbMeth();
						vert = d.getNodeByUniqueKey(aaiExtMap.getTransId(), aaiExtMap.getFromAppId(), g, key);
						doCheckpoint("GET_ANCESTRY_POST_GET_BY_KEY|" + key + ",VERT=" + vert, aaiExtMap);
					} catch (Exception e1) {
						if (stop == true) {
							return;
						}
					}
				}

				// try again, i couldn't find it by the key
				if (vert == null && stop != true) { 
					// the parent will be 1 or 2 nodes up
					TitanVertex depNode = null;

					if (parent != null) {
						if (parent.getResourceType().equals("node")) {
							depNode = ancestry.getAncestryItems().get(parent.getFullName()).getVertex();
						} else if (parent.getParent().getResourceType() != null
								&& parent.getParent().getResourceType().equals("node")) {
							depNode = ancestry.getAncestryItems().get(parent.getParent().getFullName()).getVertex();
						}
					}

					doCheckpoint("GET_ANCESTRY_PRE_GET_UNIQUE_NODE", aaiExtMap);
					vert = DbMeth.getUniqueNode(aaiExtMap.getTransId(), aaiExtMap.getFromAppId(), g, dnHypResourceType,
							propHash, depNode, aaiExtMap.getApiVersion());
					doCheckpoint("GET_ANCESTRY_POST_GET_UNIQUE_NODE", aaiExtMap);

				}
				if (vert != null) {
					try {

						String notificationVersion = AAIConfig.get("aai.notification.current.version");
						
						if (aaiRes != null && aaiRes.getApiVersion().matches("v[2-6]$") && aaiRes.getFullName() != null ) {	
							String[] fullNameSplit = aaiRes.getFullName().split("/");
							if (fullNameSplit.length > 3) {
								if (fullNameSplit[3] != null && CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, fullNameSplit[3]).matches("tenant|image|flavor|volume\\-group|availability\\-zone|oam\\-network|dvs\\-switch")) {
									notificationVersion = aaiRes.getApiVersion();
								}
							}
						}
						
						
						AAIResources aaiNotificationResources = org.openecomp.aai.ingestModel.IngestModelMoxyOxm.aaiResourceContainer
								.get(notificationVersion);

						DynamicJAXBContext notificationJaxbContext = aaiNotificationResources.getJaxbContext();
						
						String resFullName = aaiRes.getFullName();
						if (aaiNotificationResources.getAaiResources().containsKey(resFullName)) { 
							AAIResource notificationRes = aaiNotificationResources.getAaiResources().get(aaiRes.getFullName());

							AncestryItem ancestryItem = new AncestryItem();

							ancestryItem.setFullResourceName(notificationRes.getFullName());
							ancestryItem.setAaiResource(notificationRes);
							ancestryItem.setVertex(vert);
												
							DynamicType meObjectType = notificationJaxbContext.getDynamicType(notificationRes.getResourceClassName());

							DynamicEntity notificationObject = pu.getDynamicTopologyObject(aaiRes,
									meObjectType,
									notificationRes.getNodeNameProps(), 
									notificationRes.getNodeKeyProps(), 
									notificationRes.getPropertyDataTypeMap(),
									vert);

							ancestryItem.setObj(notificationObject);
							ancestry.getAncestryItems().put(aaiRes.getFullName(), ancestryItem);
						}
					} catch (Exception e) {
						throw new AAIException("AAI_3000", e);
					}
				}
			}
		}
	}


	/**
	 * Unpack ancestry.
	 *
	 * @param g the g
	 * @param ancestry the ancestry
	 * @param topologyUntilThisOne the topology until this one
	 * @param jaxbContext the jaxb context
	 * @param aaiExtMap the aai ext map
	 * @return the dynamic entity
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 * @throws ClassNotFoundException the class not found exception
	 * @throws InstantiationException the instantiation exception
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws SecurityException the security exception
	 */
	public DynamicEntity unpackAncestry (TitanTransaction g, AncestryItems ancestry, String topologyUntilThisOne, DynamicJAXBContext jaxbContext, AAIExtensionMap aaiExtMap) 
			throws UnsupportedEncodingException, AAIException, ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		
		LinkedHashMap<String,AncestryItem> ancestryHash = ancestry.getAncestryItems();
		String apiVersion = AAIConfig.get("aai.notification.current.version", "v7");
		
		DynamicEntity outsideObject = null;
		
		for (Map.Entry<String,AncestryItem> ent : ancestryHash.entrySet()) { 
						
			AncestryItem anc = ent.getValue();
			
			AAIResource aaiRes = anc.getAaiResource();
			
			DynamicEntity ancestryObj = (DynamicEntity) anc.getObj();
			TitanVertex ancestryVertex = anc.getVertex();
			
			if (outsideObject == null) { 
				outsideObject = ancestryObj;
			}
			
			if (topologyUntilThisOne.equals(aaiRes.getFullName())) { 
				PojoUtils pu = new PojoUtils();
				pu.getAaiDynamicObjectFromVertex(aaiRes, ancestryObj, ancestryVertex, aaiRes.getPropertyDataTypeMap());
				
				DynamicEntity newRelationshipList = RelationshipGraph.getRelationships(g, ancestryVertex, apiVersion, aaiExtMap);

				try { 
					ancestryObj.set("relationshipList", newRelationshipList);
				} catch (DynamicException e) { 
					;
				}
				if (aaiRes.getChildren() != null) { 
					if (aaiRes.getChildren().getAaiResources().size() > 0) {
						getDescendants(g, ancestryVertex, ancestryVertex, -1, ancestryObj, aaiRes, ancestry,
								apiVersion, jaxbContext, aaiExtMap);
					}
				}
			} else { 
				if (aaiRes.getChildren() != null) { 

					for (Map.Entry<String,AAIResource> childEnt : aaiRes.getChildren().getAaiResources().entrySet()) { 

						String childEntFullName = childEnt.getKey();
						AAIResource childRes = childEnt.getValue();

						// this could be a direct descendant
						if (ancestryHash.containsKey(childEntFullName)) {

							AncestryItem childAnc = ancestryHash.get(childEntFullName);
							DynamicEntity childObj = (DynamicEntity) childAnc.getObj();

							TitanVertex childVertex = childAnc.getVertex();

							if (topologyUntilThisOne.equals(childRes.getFullName())) { 
								PojoUtils pu = new PojoUtils();
								pu.getAaiDynamicObjectFromVertex(childRes, childObj, childVertex, childRes.getPropertyDataTypeMap());

								DynamicEntity newRelationshipList = RelationshipGraph.getRelationships(g, childVertex, apiVersion, aaiExtMap);

								try { 
									childObj.set("relationshipList", newRelationshipList);
								} catch (DynamicException e) { 
									;
								}
								if (childRes.getChildren() != null) { 
									if (childRes.getChildren().getAaiResources().size() > 0) {
										getDescendants(g, childVertex, childVertex, -1, childObj, childRes, ancestry,
												apiVersion, jaxbContext, aaiExtMap);
									}
								}
							}
							List<DynamicEntity> cList = new ArrayList<DynamicEntity>();
							cList.add(childObj);
							// we're going to include this
							ancestryObj.set(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL,childRes.getSimpleName()), cList); 

						}

						// or a grandchild
						if (childRes.getChildren() != null) { 

							for (Map.Entry<String,AAIResource> grandchildEnt : childRes.getChildren().getAaiResources().entrySet()) { 

								String grandchildEntFullName = grandchildEnt.getKey();
								AAIResource grandchildRes = grandchildEnt.getValue();

								if (ancestryHash.containsKey(grandchildEntFullName)) {

									// we need to attach the parent
									AncestryItem grandchildAnc = ancestryHash.get(grandchildEntFullName);
									DynamicEntity grandchildObj = (DynamicEntity) grandchildAnc.getObj(); 
									TitanVertex grandchildVertex = grandchildAnc.getVertex();
									// we're going to include this

									if (topologyUntilThisOne.equals(grandchildRes.getFullName())) { 
										PojoUtils pu = new PojoUtils();
										pu.getAaiDynamicObjectFromVertex(grandchildRes, grandchildObj, grandchildVertex, grandchildRes.getPropertyDataTypeMap());

										DynamicEntity newRelationshipList = RelationshipGraph.getRelationships(g, grandchildVertex, apiVersion, aaiExtMap);

										try { 
											grandchildObj.set("relationshipList", newRelationshipList);
										} catch (DynamicException e) { 
											;
										}
										if (childRes.getChildren() != null) { 
											if (childRes.getChildren().getAaiResources().size() > 0) {
												getDescendants(g, grandchildVertex, grandchildVertex, -1, grandchildObj, grandchildRes, ancestry,
														apiVersion, jaxbContext, aaiExtMap);
											}
										}
									}

									List<DynamicEntity> gcList = new ArrayList<DynamicEntity>();

									gcList.add(grandchildObj);

									DynamicEntity childObj = jaxbContext.newDynamicEntity(childRes.getResourceClassName());

									childObj.set(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL,grandchildRes.getSimpleName()), gcList);

									ancestryObj.set(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL,childRes.getSimpleName()), childObj); 
								}
							}	
						}
					}
				}
			}
		}
		return outsideObject;
	}

	/**
	 * Checks if is event enabled.
	 *
	 * @param action the action
	 * @param namespace the namespace
	 * @param simpleName the simple name
	 * @return true, if is event enabled
	 * @throws AAIException the AAI exception
	 */
	public boolean isEventEnabled(String action, String namespace, String simpleName) throws AAIException {
		boolean isEventEnabled = true;
		if ((AAIConfig.get("aai.notificationEvent.disable.aaiEvent.global." + action, "false").equals("true"))
				|| (AAIConfig
						.get("aai.notificationEvent.disable.aaiEvent.graph." + namespace + "Graph." + action, "false")
						.equals("true"))
						|| (AAIConfig.get("aai.notificationEvent.disable.aaiEvent.object." + simpleName + "." + action, "false")
								.equals("true"))) {
			isEventEnabled = false;
		}
		return isEventEnabled;
	}

	/**
	 * Check required props.
	 *
	 * @param dnHypResourceType the dn hyp resource type
	 * @param requiredProps the required props
	 * @param propHash the prop hash
	 * @throws AAIException the AAI exception
	 */
	private void checkRequiredProps(String dnHypResourceType, Collection<String> requiredProps,
			HashMap<String, Object> propHash) throws AAIException {
		ArrayList<String> missingFields = new ArrayList<String>();
		ArrayList<String> emptyFields = new ArrayList<String>();

		for (String reqProp : requiredProps) {
			if (propHash.containsKey(reqProp)) {
				if (propHash.get(reqProp) == null) {
					missingFields.add(reqProp);
				} else {
					Object tmpObj = propHash.get(reqProp);
					if (tmpObj.toString().trim().equals("")) {
						emptyFields.add(reqProp);
					}
				}
			} else {
				missingFields.add(reqProp);
			}
		}

		if (missingFields.size() > 0) {
			StringBuffer missing = new StringBuffer();
			boolean isFirst = true;
			for (String missingAttr : missingFields) {
				if (!isFirst) {
					missing.append(",");
				}
				missing.append(missingAttr);
				isFirst = false;
			}
			String reqP = missingFields.get(0);
			logline.add("property", reqP);
			logline.add("nodeType", dnHypResourceType);
			aaiLogger.info(logline, false, "AAI_6103");
			String detail = "[REST] Required property = " + missing + " not passed for nodeType = " + dnHypResourceType;
			throw new AAIException("AAI_6103", detail);
		}
		if (emptyFields.size() > 0) {
			StringBuffer empty = new StringBuffer();
			boolean isFirst = true;
			for (String emptyAttr : emptyFields) {
				if (!isFirst) {
					empty.append(",");
				}
				empty.append(emptyAttr);
				isFirst = false;
			}
			String reqP = emptyFields.get(0);
			logline.add("property", reqP);
			logline.add("nodeType", dnHypResourceType);
			aaiLogger.info(logline, false, "AAI_6104");
			String detail = "[REST] Required property = " + empty + " for nodeType = " + dnHypResourceType
					+ " was passed with no data";
			throw new AAIException("AAI_6104", detail);
		}

	}

	/**
	 * Store notification event.
	 *
	 * @param g the g
	 * @param eventAction the event action
	 * @param meVertex the me vertex
	 * @param meObject the me object
	 * @param aaiRes the aai res
	 * @param ancestry the ancestry
	 * @param aaiResources the aai resources
	 * @param aaiExtMap the aai ext map
	 * @throws AAIException the AAI exception
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 */
	public void storeNotificationEvent(TitanTransaction g, String eventAction, TitanVertex meVertex, DynamicEntity meObject,
			AAIResource aaiRes, AncestryItems ancestry, AAIResources aaiResources, AAIExtensionMap aaiExtMap) throws AAIException, UnsupportedEncodingException {
		
		storeNotificationEvent(g, eventAction, meVertex, meObject, aaiRes, ancestry, aaiResources, aaiExtMap, "");

	}
			
	/**
	 * Store notification event.
	 *
	 * @param g the g
	 * @param eventAction the event action
	 * @param meVertex the me vertex
	 * @param meObject the me object
	 * @param aaiRes the aai res
	 * @param ancestry the ancestry
	 * @param aaiResources the aai resources
	 * @param aaiExtMap the aai ext map
	 * @param overrideUri the override uri
	 * @throws AAIException the AAI exception
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 */
	public void storeNotificationEvent(TitanTransaction g, String eventAction, TitanVertex meVertex, DynamicEntity meObject,
			AAIResource aaiRes, AncestryItems ancestry, AAIResources aaiResources, AAIExtensionMap aaiExtMap, String overrideUri)
					throws AAIException, UnsupportedEncodingException {
		
		String notificationVersion = AAIConfig.get("aai.notification.current.version");
		
		AAIResources aaiNotificationResources = org.openecomp.aai.ingestModel.IngestModelMoxyOxm.aaiResourceContainer
				.get(notificationVersion);

		DynamicJAXBContext notificationJaxbContext = aaiNotificationResources.getJaxbContext();

		DynamicEntity notificationHeader = notificationJaxbContext
				.getDynamicType("inventory.aai.openecomp.org." + notificationVersion + ".NotificationEventHeader")
				.newDynamicEntity();

		boolean storeEvent = isEventEnabled((String) notificationHeader.get("action"), aaiExtMap.getNamespace(),
				aaiRes.getSimpleName());

		if (storeEvent == true) {
			// get the parent/gp/greatGp vertex, it will be the container
			if (overrideUri.length() > 0) { 
				notificationHeader.set("entityLink", overrideUri);
			} else { 
				notificationHeader.set("entityLink", aaiExtMap.getNotificationUri());
			}
			notificationHeader.set("action", eventAction);
			String entityType = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, aaiRes.getSimpleName());

			if (entityType.equals("cvlan-tag-entry")) { 
				entityType = "cvlan-tag";
			}
			notificationHeader.set("entityType", entityType);

			String topEntityType = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, aaiRes.getSimpleName());

			if (topEntityType.equals("cvlan-tag-entry")) { 
				topEntityType = "cvlan-tag";
			}
			notificationHeader.set("topEntityType", topEntityType);

			notificationHeader.set("sourceName", aaiExtMap.getFromAppId());

			notificationHeader.set("version", notificationVersion);

			StoreNotificationEvent sne = new StoreNotificationEvent();

			AAIResource aaiActualRes = aaiRes;

			if (aaiExtMap.getTopObjectFullResourceName().equals(aaiExtMap.getFullResourceName())) {

				aaiActualRes = aaiRes;
				if (!aaiRes.getApiVersion().equals(notificationVersion)) {
					aaiActualRes = aaiNotificationResources.getAaiResources().get(aaiRes.getFullName());
				}
				
				// -1 depth means get all the children to any depth
				meObject = getObject(g, meVertex, meVertex, aaiActualRes, null, -1, ancestry,
						notificationJaxbContext, notificationVersion, aaiExtMap);

				sne.storeDynamicEvent(notificationJaxbContext, notificationVersion, notificationHeader, meObject);
				aaiExtMap.setObjectFromResponse(meObject);
				aaiExtMap.setObjectFromResponseType(aaiActualRes.getResourceClassName());
			} else {
				AAIResource topRes = aaiNotificationResources.getAaiResources().get(aaiExtMap.getTopObjectFullResourceName());
				TitanVertex topVertex = ancestry.getAncestryItems().get(aaiExtMap.getTopObjectFullResourceName())
						.getVertex();

				aaiActualRes = topRes;
				if (!aaiRes.getApiVersion().equals(notificationVersion)) {
					aaiActualRes = aaiNotificationResources.getAaiResources().get(topRes.getFullName());
				}
				// -1 depth means get all the children to any depth
				DynamicEntity topObject = getObject(g, meVertex, topVertex, aaiActualRes, aaiRes.getFullName(), -1,
						ancestry, notificationJaxbContext, notificationVersion, aaiExtMap);

				notificationHeader.set("topEntityType",
						CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, topRes.getSimpleName()));

				sne.storeDynamicEvent(notificationJaxbContext, notificationVersion, notificationHeader, topObject);
				aaiExtMap.setObjectFromResponse(topObject);
				aaiExtMap.setObjectFromResponseType(aaiActualRes.getResourceClassName());
			}
		}
	}

}
