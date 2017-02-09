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

package org.openecomp.aai.rest;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.openecomp.aai.domain.model.AAIResource;
import org.openecomp.aai.domain.model.AAIResourceKey;
import org.openecomp.aai.domain.model.AAIResourceKeys;
import org.openecomp.aai.domain.model.AAIResources;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.exceptions.AAIExceptionWithInfo;
import org.openecomp.aai.extensions.AAIExtensionMap;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.util.AAIConfig;
import org.springframework.web.util.UriUtils;

import com.google.common.base.CaseFormat;

/**
 * The Class RestProviders.
 */
@Path("{parameter: v[8]}/service-design-and-creation")
public class RestProviders extends RESTAPI {
	protected static String authPolicyFunctionName = "REST";
	protected static AAILogger aaiLogger = new AAILogger(RestProviders.class.getName());

	/**
	 * Put provider.
	 *
	 * @param objectFromRequest the object from request
	 * @param uriInfo the uri info
	 * @param headers the headers
	 * @param req the req
	 * @return the response
	 */
	@PUT
	@Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path("/{subResources:.*}")
	public Response putProvider (
			String objectFromRequest,
			@Context UriInfo uriInfo,
			@Context HttpHeaders headers,
			@Context HttpServletRequest req) {
		AAIException ex = null;
		Response response = null;
		LogLine logline = new LogLine();
		long startTime = System.currentTimeMillis();
		ArrayList<String> templateVars = new ArrayList<String>();
		
		try {
			
			String fromAppId = getFromAppId(headers, logline);
			String transId = getTransId(headers, logline);
			
			AAIExtensionMap aaiExtMap = new AAIExtensionMap();
			aaiExtMap.setAaiLogger(aaiLogger);
			aaiExtMap.setLogline(new LogLine());
			
			aaiExtMap.setStartTime(startTime);
			aaiExtMap.setCheckpointTime(startTime);
			HashMap<String, String> allKeys = new HashMap<String, String>();
			LinkedHashMap<String, LinkedHashMap<String,Object>> keyList = new LinkedHashMap<String, LinkedHashMap<String,Object>>();			
			
			aaiExtMap.setHttpHeaders(headers);
			aaiExtMap.setServletRequest(req);
			
			logline.init(COMPONENT, transId, fromAppId, "parseUri");									
			parseUri(allKeys, keyList, uriInfo.getPath(false), aaiExtMap);			
			logline.init(COMPONENT, transId, fromAppId, "putProvider");
			
			aaiExtMap.setTransId(transId);
			aaiExtMap.setFromAppId(fromAppId);
			org.openecomp.aai.dbmap.GraphHelpersMoxy graphHelpers = new org.openecomp.aai.dbmap.GraphHelpersMoxy();
			
			String[] chunks = aaiExtMap.getFullResourceName().split("/");
			
			String keyString = "";
			String resName = "";
			if (chunks.length > 0) { 
				resName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, chunks[(chunks.length-1)]);
			
				HashMap<String,Object> thisResourceKeys = keyList.get(aaiExtMap.getFullResourceName());
			
				for (Map.Entry<String,Object> ent : thisResourceKeys.entrySet()) { 
					keyString += ent.getKey() + "=" + ent.getValue() + " ";
				}
			} 
			logline.add("nodeType", resName);
			logline.add("keyString", keyString);
			
			if (keyList.containsKey(aaiExtMap.getFullResourceName() + "/RelationshipList")) {
				templateVars.add("PUT " + resName + " relationship");
				logline.add("putRel", "true");
				templateVars.add(keyString);
						response = graphHelpers.handleUpdateRel(objectFromRequest, keyList, allKeys, aaiExtMap);
			} else { 
				templateVars.add("PUT " + resName);
				templateVars.add(keyString);
				response = graphHelpers.handlePut(objectFromRequest, keyList, allKeys, aaiExtMap);
			}
		} catch (AAIExceptionWithInfo e) { 
					
			ex = e;
			templateVars.add(e.getInfo());
			String hashString = e.getInfoHash().toString();
			hashString = hashString.replace("{", "");
			hashString = hashString.replace("}", "");
			templateVars.add(hashString);
			response = Response
					.status(e.getErrorObject().getHTTPResponseCode()).entity(ErrorLogHelper
							.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), e, templateVars, logline))
							.build();
						
		} catch (AAIException e) {
			
			if (templateVars.size() == 0) {
				templateVars.add("PUT");
				templateVars.add(uriInfo.getPath().toString());
			}
			// send error response
			ex = e;
			// e.printStackTrace();

			response = Response
					.status(e.getErrorObject().getHTTPResponseCode())
					.entity(ErrorLogHelper.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), e,
							templateVars, logline)).build();
			
		} catch (Exception e) {
			
			if (templateVars.size() == 0) {
				templateVars.add("PUT");
				templateVars.add(uriInfo.getPath().toString());
			}
			// send error response
			ex = new AAIException("AAI_4000", e);
			// e.printStackTrace();

			response = Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.entity(ErrorLogHelper.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), ex,
							templateVars, logline)).build();
		} finally {
			// log success or failure
			if (ex == null)
				aaiLogger.info(logline, true, "0");
			else {
				aaiLogger.error(ex.getErrorObject(), logline, ex);
				aaiLogger.info(logline, false, ex.getErrorObject().getErrorCodeString());
			}
		}
		
/*		long endDate = System.currentTimeMillis();		
		long diff = endDate - startTime;
		System.out.println("PUT    " + diff);*/
		
		return response;
	}

	/**
	 * Delete provider.
	 *
	 * @param objectFromRequest the object from request
	 * @param uriInfo the uri info
	 * @param headers the headers
	 * @param req the req
	 * @param resourceVersion the resource version
	 * @return the response
	 */
	@DELETE
	@Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path("/{subResources:.*}")
	public Response deleteProvider (
			String objectFromRequest,
			@Context UriInfo uriInfo,
			@Context HttpHeaders headers,
			@Context HttpServletRequest req, 
			@QueryParam("resource-version") String resourceVersion) {
		AAIException ex = null;
		Response response = null;
		LogLine logline = new LogLine();

		long startTime = System.currentTimeMillis() ;
		ArrayList<String> templateVars = new ArrayList<String>();
		try {
			
			String fromAppId = getFromAppId(headers, logline);
			String transId = getTransId(headers, logline);
			
			AAIExtensionMap aaiExtMap = new AAIExtensionMap();
			aaiExtMap.setStartTime(startTime);
			aaiExtMap.setCheckpointTime(startTime);
			aaiExtMap.setAaiLogger(aaiLogger);
			aaiExtMap.setLogline(new LogLine());
			HashMap<String, String> allKeys = new HashMap<String, String>();
			LinkedHashMap<String, LinkedHashMap<String,Object>> keyList = new LinkedHashMap<String, LinkedHashMap<String,Object>>();			
			
			aaiExtMap.setHttpHeaders(headers);
			aaiExtMap.setServletRequest(req);
			
			logline.init(COMPONENT, transId, fromAppId, "parseUri");	
			parseUri(allKeys, keyList, uriInfo.getPath(false), aaiExtMap);
			logline.init(COMPONENT, transId, fromAppId, "deleteProvider");
			
			String[] chunks = aaiExtMap.getFullResourceName().split("/");			
			String keyString = "";
			String resName = "";
			if (chunks.length > 0) { 
				resName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, chunks[(chunks.length-1)]);
			
				HashMap<String,Object> thisResourceKeys = keyList.get(aaiExtMap.getFullResourceName());
			
				for (Map.Entry<String,Object> ent : thisResourceKeys.entrySet()) { 
					keyString += ent.getKey() + "=" + ent.getValue() + " ";
				}
			} 
			logline.add("nodeType", resName);
			logline.add("keyString",  keyString);
			aaiExtMap.setTransId(transId);
			aaiExtMap.setFromAppId(fromAppId);
			
			org.openecomp.aai.dbmap.GraphHelpersMoxy graphHelpers = new org.openecomp.aai.dbmap.GraphHelpersMoxy();
			if (keyList.containsKey(aaiExtMap.getFullResourceName() + "/RelationshipList")) {
				templateVars.add("DELETE " + resName + " relationship");
				logline.add("delRel", "true");
				templateVars.add(keyString);
				response = graphHelpers.handleDeleteRel(objectFromRequest, keyList, allKeys, aaiExtMap);
			} else { 
				templateVars.add("DELETE " + resName);
				templateVars.add(keyString);
				response = graphHelpers.handleDelete(keyList, allKeys, resourceVersion, aaiExtMap);
			}
		} catch (AAIExceptionWithInfo e) { 

			ex = e;
			templateVars.add(e.getInfo());
			String hashString = e.getInfoHash().toString();
			hashString = hashString.replace("{", "");
			hashString = hashString.replace("}", "");
			templateVars.add(hashString);
			response = Response
					.status(e.getErrorObject().getHTTPResponseCode()).entity(ErrorLogHelper
							.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), e, templateVars, logline))
							.build();
								
		} catch (AAIException e) {
			if (templateVars.size() == 0) {
				templateVars.add("DELETE");
				templateVars.add(uriInfo.getPath().toString());
			}
			// send error response
			ex = e;
			
			response = Response
					.status(e.getErrorObject().getHTTPResponseCode())
					.entity(ErrorLogHelper.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), e, 
							templateVars, logline)).build();
		} catch (Exception e) {
			if (templateVars.size() == 0) {
				templateVars.add("DELETE");
				templateVars.add(uriInfo.getPath().toString());
			}
			// send error response
			ex = new AAIException("AAI_4000", e);

			response = Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.entity(ErrorLogHelper.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), ex,
							templateVars, logline)).build();
		} finally {
			// log success or failure
			if (ex == null)
				aaiLogger.info(logline, true, "0");
			else {
				aaiLogger.error(ex.getErrorObject(), logline, ex);
				aaiLogger.info(logline, false, ex.getErrorObject().getErrorCodeString());
			}
		}
		
/*		long endDate = System.currentTimeMillis();		
		long diff = endDate - startTime;	
		System.out.println("DELETE " + diff);*/
		
		return response;
	}

	/**
	 * Gets the provider.
	 *
	 * @param uriInfo the uri info
	 * @param headers the headers
	 * @param req the req
	 * @param depthParam the depth param
	 * @return the provider
	 */
	@GET
	@Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path("/{subResources:.*}")
	// TODO: include "depth" thing
	public Response getProvider (
			@Context UriInfo uriInfo,
			@Context HttpHeaders headers,
			@Context HttpServletRequest req,
			@QueryParam("depth") String depthParam) {
		AAIException ex = null;
		Response response = null;
		LogLine logline = new LogLine();
		long startTime = System.currentTimeMillis() ;
		ArrayList<String> templateVars = new ArrayList<String>(2);
		try {
			
			int depth = -1;
			if (depthParam != null && depthParam.length() > 0) {
				if ("all".equals(depthParam)) {
					depthParam = "-1";
				}
				try {
					depth = Integer.valueOf(depthParam);
				} catch (Exception e) {
					throw new AAIException("AAI_4016");
				}
			}
			
			String fromAppId = getFromAppId(headers, logline);
			String transId = getTransId(headers, logline);
			String rqstTm = genDate();		
			AAIExtensionMap aaiExtMap = new AAIExtensionMap();
			aaiExtMap.setAaiLogger(aaiLogger);
			aaiExtMap.setLogline(new LogLine());
			aaiExtMap.setStartTime(startTime);
			aaiExtMap.setCheckpointTime(startTime);
			
			HashMap<String, String> allKeys = new HashMap<String, String>();
			LinkedHashMap<String, LinkedHashMap<String,Object>> keyList = new LinkedHashMap<String, LinkedHashMap<String,Object>>();			
			
			aaiExtMap.setHttpHeaders(headers);
			aaiExtMap.setServletRequest(req);
			
			logline.init(COMPONENT, transId, fromAppId, "parseUri");	
			AAIResource aaiRes = parseUri(allKeys, keyList, uriInfo.getPath(false), aaiExtMap);
			logline.init(COMPONENT, transId, fromAppId, "getProvider");			
			
			String[] chunks = aaiExtMap.getFullResourceName().split("/");			
			String resName = "";
			if (chunks.length > 0) { 
				resName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, chunks[(chunks.length-1)]);
				
			} 
			templateVars.add("GET " + resName);
			
			aaiExtMap.setTransId(transId);
			aaiExtMap.setFromAppId(fromAppId);

			org.openecomp.aai.dbmap.GraphHelpersMoxy graphHelpers = new org.openecomp.aai.dbmap.GraphHelpersMoxy();
			logline.add("nodeType", aaiRes.getSimpleName());
			if (aaiRes.getResourceType().equals("container")) { 
				
				if (keyList.containsKey(aaiRes.getFullName())) {
					String exampleType = (String)keyList.get(aaiRes.getFullName()).get("container|example");
					
					boolean singleton = false;
					if (exampleType.equals("singletonExample")) {
						templateVars.add("singletonExample");
						logline.add("example", "singleton");
						singleton = true;
					} else {
						logline.add("example",  "full");
						templateVars.add("example");
					}
					response = graphHelpers.handleExample(aaiRes, aaiExtMap, singleton, true);
					
				} else { 
					logline.add("getAll",  "true");
					templateVars.add("all");
					response = graphHelpers.handleGetAll(keyList, allKeys, depth, aaiExtMap);
				}
			} else {
				// check if there's a key for this node Type in the lookupHashMap
				String dnHypNodeType = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, aaiRes.getSimpleName());
			
				if (!keyList.containsKey(aaiRes.getFullName())) { 
					MultivaluedMap<String, String> params = uriInfo.getQueryParameters();
					if (dnHypNodeType.equals("cvlan-tag-entry")) {
						dnHypNodeType = "cvlan-tag";
					}
					Collection<String> indexedProps = aaiRes.getNodeMapIndexedProps().get(dnHypNodeType);

					ArrayList<String> stringFields = aaiRes.getStringFields();
					// get all the string props for this method
					
					boolean hasIndexProp = false;

					LinkedHashMap<String,Object> subKeyList = new LinkedHashMap<String,Object>();
					String keyString = "";
					for (Entry<String, List<String>> param : params.entrySet()) {
						
						String paramName = param.getKey();
						if (indexedProps.contains(paramName)) { 
							hasIndexProp = true;
						}
						List<String> paramVals = param.getValue();
						for (String val : paramVals) { 
							if (stringFields.contains(paramName)) {
								subKeyList.put(paramName, val);
								keyString += paramName + "=" + val + " ";
							}
							if (aaiRes.getBooleanFields().contains(paramName)) { 
								subKeyList.put(paramName, Boolean.valueOf(val));
								keyString += paramName + "=" + val + " ";
							}
							if (aaiRes.getLongFields().contains(paramName)) { 
								subKeyList.put(paramName, Long.valueOf(val));
								keyString += paramName + "=" + val + " ";
							}
						}
					}
					keyString = keyString.trim();
					templateVars.add(keyString);
					logline.add("keyString",  keyString);
					keyList.put(aaiRes.getFullName(), subKeyList);
					if (hasIndexProp == true) {
			
						response = graphHelpers.handleGetByName(keyList, allKeys, depth, aaiExtMap);
					} else { 
						AAIException e = new AAIException("AAI_4015");
						
						response = Response
								.status(e.getErrorObject().getHTTPResponseCode())
								.entity(ErrorLogHelper.getRESTAPIErrorResponse(
										headers.getAcceptableMediaTypes(), e, templateVars,
										logline)).build();
					}
				} else {
					
					HashMap<String,Object> thisResourceKeys = keyList.get(aaiExtMap.getFullResourceName());
					String keyString = "";
					for (Map.Entry<String,Object> ent : thisResourceKeys.entrySet()) { 
						keyString += ent.getKey() + "=" + ent.getValue() + " ";
					}
					keyString = keyString.trim();
					templateVars.add(keyString);
					logline.add("keyString",  keyString);
					response = graphHelpers.handleGetSingleByKey(keyList, allKeys, depth, aaiExtMap);
				}
			}
		} catch (AAIExceptionWithInfo e) { 

			ex = e;
			templateVars.add(e.getInfo());
			String hashString = e.getInfoHash().toString();
			hashString = hashString.replace("{", "");
			hashString = hashString.replace("}", "");
			templateVars.add(hashString);
			response = Response
					.status(e.getErrorObject().getHTTPResponseCode()).entity(ErrorLogHelper
							.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), e, templateVars, logline))
							.build();

		} catch (AAIException e) {
			if (templateVars.size() == 0) {
				templateVars.add("GET");
				templateVars.add(uriInfo.getPath().toString());
			}
			// send error response
			ex = e;

			response = Response
					.status(e.getErrorObject().getHTTPResponseCode())
					.entity(ErrorLogHelper.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), e, 
							templateVars, logline)).build();
		} catch (Exception e) {
			if (templateVars.size() == 0) {
				templateVars.add("GET");
				templateVars.add(uriInfo.getPath().toString());
			}
			// send error response
			ex = new AAIException("AAI_4000", e);
	
			response = Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.entity(ErrorLogHelper.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), ex,
							templateVars, logline)).build();
		} finally {
			// log success or failure
			if (ex == null)
				aaiLogger.info(logline, true, "0");
			else {
				aaiLogger.error(ex.getErrorObject(), logline, ex);
				aaiLogger.info(logline, false, ex.getErrorObject().getErrorCodeString());
			}
		}
		
/*		long endDate = System.currentTimeMillis();		
		long diff = endDate - startTime;	
		System.out.println("DELETE " + diff);*/
		
		return response;
	}

	/**
	 * Parses the uri.
	 *
	 * @param allKeys the all keys
	 * @param keyList the key list
	 * @param uriInfo the uri info
	 * @param aaiExtMap the aai ext map
	 * @return the AAI resource
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 */
	public AAIResource parseUri(HashMap<String, String> allKeys, LinkedHashMap<String, 
			LinkedHashMap<String,Object>> keyList, String uriInfo, 
			AAIExtensionMap aaiExtMap) throws UnsupportedEncodingException, AAIException {
		// map back to the model
		
		String[] ps = uriInfo.split("/");
		
		String apiVersion = ps[0];
		aaiExtMap.setApiVersion(apiVersion);
		
		AAIResources aaiResources = org.openecomp.aai.ingestModel.IngestModelMoxyOxm.aaiResourceContainer.get(apiVersion);
		
		String namespace = ps[1];
		
		aaiExtMap.setNamespace(namespace);
		
		// /vces/vce/{vnf-id}/port-groups/port-group/{port-group-id}/cvlan-tag-entry/cvlan-tag/{cvlan-tag}
		
		// FullName -> /Vces/Vce/PortGroups/PortGroup/CvlanTagEntry/CvlanTag <- 

		String fullResourceName = "/" + CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, namespace);
		AAIResources theseResources = new AAIResources();
		
		StringBuffer thisUri = new StringBuffer();
		
		// the URI config option in the props file has a trailing slash
		thisUri.append("/" + namespace);
				
		boolean firstNode = true;
		
		AAIResource lastResource = null;
		
		for (int i = 2; i < ps.length; i++) { 
			
			AAIResource aaiRes;
			StringBuffer tmpResourceName = new StringBuffer();
				
			String p = ps[i];
			String seg =ps[i];
						
			thisUri.append("/" + seg);
			
			tmpResourceName.append(fullResourceName);
			
			if (seg.equals("cvlan-tag")) {
				seg = "cvlan-tag-entry";
			}
			tmpResourceName.append("/" + CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, seg));
			
			String tmpResource = tmpResourceName.toString();
			
			if (aaiResources.getAaiResources().containsKey(tmpResource)) {
				aaiRes = aaiResources.getAaiResources().get(tmpResource);
				lastResource = aaiRes;
				theseResources.getAaiResources().put(tmpResource, aaiRes);
				fullResourceName = tmpResource;
				if ("node".equals(aaiRes.getResourceType())) {
					
					if (firstNode == true) { 
						aaiExtMap.setTopObjectFullResourceName(fullResourceName);
						firstNode = false;
					}
							
					// get the keys, which will be in order and the next path segment(s)
					AAIResourceKeys keys = aaiRes.getAaiResourceKeys();
									
					LinkedHashMap<String,Object> subKeyList = new LinkedHashMap<String,Object>();
					
					// there might not be another path segment
					if ( (i + 1) < ps.length) { 

						for (AAIResourceKey rk : keys.getAaiResourceKey()) {
							String p1 = ps[++i];
							String encodedKey = p1.toString();
							thisUri.append("/" + encodedKey);
							String decodedKey =  UriUtils.decode(p1.toString(), "UTF-8");
							subKeyList.put(rk.getKeyName(), decodedKey);
						}
						keyList.put(tmpResource, subKeyList);
						// this is the key
						allKeys.put(tmpResource, thisUri.toString());
					}
				} else { // examples sit directly under the container level, should probably be query params!!!
					if ( (i + 1) < ps.length) { 
						String p1 = ps[i+1];
						if (p1.toString().equals("example") || p1.toString().equals("singletonExample")) { 
							LinkedHashMap<String,Object> subKeyList = new LinkedHashMap<String,Object>();
							subKeyList.put("container|example", p1.toString());
							keyList.put(tmpResource, subKeyList);
						}
					}
				}
			} else {
				if (p.equals("relationship-list")) { 
					LinkedHashMap<String,Object> subKeyList = new LinkedHashMap<String,Object>();
					subKeyList.put("container|relationship", p.toString());
					keyList.put(tmpResource, subKeyList);
				} else if ( p.toString().length() > 0 && !p.toString().equals("example") && !p.toString().equals("singletonExample") 
						&& !p.toString().equals("relationship") ) {
					// this means the URL will break the model, so we bail
					throw new AAIException("AAI_3001", "bad path");
				}
			}
		}
		aaiExtMap.setUri(AAIConfig.get("aai.global.callback.url")  + apiVersion + thisUri.toString());
		aaiExtMap.setNotificationUri(AAIConfig.get("aai.global.callback.url") + AAIConfig.get("aai.notification.current.version") + thisUri.toString());
		aaiExtMap.setFullResourceName(fullResourceName);
		return lastResource;
	}
}
