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

package org.openecomp.aai.rest.actions;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.bind.JAXBException;
import javax.xml.transform.stream.StreamSource;

import org.eclipse.persistence.dynamic.DynamicEntity;
import org.eclipse.persistence.jaxb.JAXBUnmarshaller;
import org.eclipse.persistence.jaxb.dynamic.DynamicJAXBContext;
import org.openecomp.aai.dbmap.ActionsGraph;
import org.openecomp.aai.domain.model.AAIResource;
import org.openecomp.aai.domain.model.AAIResources;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.extensions.AAIExtensionMap;
import org.openecomp.aai.extensions.ExtensionController;
import org.openecomp.aai.ingestModel.IngestModelMoxyOxm;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.rest.RESTAPI;
import org.openecomp.aai.util.AAIApiVersion;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIConstants;

/**
 * Implements the action subdomain in the REST API. All API calls must include
 * X-FromAppId and X-TransactionId in the header.
 * 
 
 *
 */

@Path("/{parameter: v8}/actions")
public class ActionsProvider extends RESTAPI {

	protected static AAILogger aaiLogger = new AAILogger(ActionsProvider.class.getName());
	protected static String authPolicyFunctionName = "actions";
	
	public static final String ACTIONS_UPDATE = "/update";

	public static final String ACTIONS_NOTIFY = "/notify";

	/**
	 * Update.
	 *
	 * @param objectFromRequest the object from request
	 * @param headers the headers
	 * @param req the req
	 * @return the response
	 */
	/* ---------------- Start Property Update PUT --------------------- */
	@PUT
	@Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(ACTIONS_UPDATE)
	public Response update(
							String objectFromRequest,
							@Context HttpHeaders headers, 
							@Context HttpServletRequest req) {
		AAIException ex = null;
		Response response = null;
		String fromAppId = null;
		String transId = null;
		LogLine logline = new LogLine();
		String rqstTm = genDate(logline);
		ArrayList<String> templateVars = new ArrayList<String>();	
		try { 
			fromAppId = getFromAppId(headers, logline );
			transId = getTransId(headers, logline);
			
			logline.init(COMPONENT, transId, fromAppId, "update");

			AAIExtensionMap aaiExtMap = new AAIExtensionMap();						
			aaiExtMap.setHttpHeaders(headers);
			aaiExtMap.setServletRequest(req);
			aaiExtMap.setApiVersion(AAIApiVersion.get());
			
		    AAIResources aaiResources = IngestModelMoxyOxm.aaiResourceContainer.get(aaiExtMap.getApiVersion());
		    AAIResource aaiRes = aaiResources.getAaiResources().get("/Actions/Update");
		    DynamicJAXBContext jaxbContext = aaiResources.getJaxbContext();	
		    
			DynamicEntity updateRequest = getDynamicEntityForRequest(jaxbContext, aaiRes, objectFromRequest, aaiExtMap);

			ActionsGraph actionsGraph = new ActionsGraph();
			actionsGraph.propertyUpdate(fromAppId, transId, updateRequest, aaiExtMap);

			response = Response
						.status(Status.OK)
						.build();
	
			String respTm = genDate();
			logTransaction(fromAppId, transId,
					"PUTACTIONSUPDATE", req.getRequestURI(), rqstTm, respTm, "",
					response, logline);
		} catch (AAIException e) { 
			// send error response
			ex = e;
			templateVars.add("PUT Actions");
			templateVars.add("update");
			response = Response
						.status(e.getErrorObject().getHTTPResponseCode())
						.entity(ErrorLogHelper.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), e, templateVars, logline))
						.build();
		} catch (Exception e) {
			// send error response
			ex = new AAIException("AAI_4000", e);
			templateVars.add("PUT Actions");
			templateVars.add("update");
			response = Response
						.status(Status.INTERNAL_SERVER_ERROR)
						.entity(ErrorLogHelper.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), ex, templateVars, logline))
						.build();
		} finally {
			// log success or failure
			if (ex != null) {
				aaiLogger.error(ex.getErrorObject(), logline, ex);
				aaiLogger.info(logline, false, ex.getErrorObject().getErrorCodeString());
			}
		}
		if (ex != null) {
			return response;
		}


		return response;
	}

	/* ---------------- End Property Update PUT --------------------- */

	/**
	 * Notify.
	 *
	 * @param objectFromRequest the object from request
	 * @param headers the headers
	 * @param req the req
	 * @return the response
	 */
	/* ---------------- Start Notify PUT --------------------- */
	@PUT
	@Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(ACTIONS_NOTIFY)
	public Response notify(
							String objectFromRequest,
							@Context HttpHeaders headers, @Context HttpServletRequest req) {
		AAIException ex = null;
		Response response = null;
		String fromAppId = null;
		String transId = null;
		LogLine logline = new LogLine();
		ArrayList<String> templateVars = new ArrayList<String>();	
		try { 
			fromAppId = getFromAppId(headers, logline );
			transId = getTransId(headers, logline);
			
			logline.init(COMPONENT, transId, fromAppId, "notify");											

			// get the API version from the URL
			String apiVersion = AAIApiVersion.get();
			AAIExtensionMap aaiExtMap = new AAIExtensionMap();						
			aaiExtMap.setHttpHeaders(headers);
			aaiExtMap.setServletRequest(req);
			aaiExtMap.setApiVersion(AAIApiVersion.get());
			
		    AAIResources aaiResources = IngestModelMoxyOxm.aaiResourceContainer.get(aaiExtMap.getApiVersion());
		    AAIResource aaiRes = aaiResources.getAaiResources().get("/Actions/Notify");
		    DynamicJAXBContext jaxbContext = aaiResources.getJaxbContext();	
		    
			DynamicEntity notifyRequest = getDynamicEntityForRequest(jaxbContext, aaiRes, objectFromRequest, aaiExtMap);
			
			List<DynamicEntity> keyParams = notifyRequest.get("keyData");

			String caller = null;
			if (notifyRequest.get("keyData") != null) {
				for (DynamicEntity keyDatum : keyParams) {
					if (keyDatum.get("keyName") != null) {
						if (keyDatum.get("keyName").equals("source")) {
							caller = keyDatum.get("keyValue");
						}
					} else {
						throw new AAIException("AAI_6103");
					}
				}
			} else {
				throw new AAIException("AAI_6103");
			}


//			if (caller != null) {
//				String callerProp = "aai.extensions." + apiVersion + ".notify."
//						+ caller.toLowerCase() + ".class";
//				String targetClass = AAIConfig.get(callerProp);
//
//				aaiExtMap.setAaiLogger(aaiLogger);
//				aaiExtMap.setTransId(transId);
//				aaiExtMap.setFromAppId(fromAppId);
//				aaiExtMap.setObjectFromRequest(notifyRequest);
//				aaiExtMap.setObjectFromRequestType("Actions/Notify");
//
////				ExtensionController ext = new ExtensionController();
////				ext.runExtension(apiVersion, "notify", targetClass, "processNotify", aaiExtMap, true);
//
//			} else {
//				throw new AAIException("AAI_6103");
//			}

			response = Response
						.status(Status.OK)
						.build();
			
		} catch (AAIException e) { 
			// send error response
			ex = e;
			templateVars.add("PUT Actions");
			templateVars.add("notify");
			response =  Response
						.status(e.getErrorObject().getHTTPResponseCode())
						.entity(ErrorLogHelper.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), e, templateVars, logline))
						.build();
		} catch (Exception e) {
			// send error response
			ex = new AAIException("AAI_4000", e);
			templateVars.add("PUT Actions");
			templateVars.add("notify");
			response = Response
						.status(Status.INTERNAL_SERVER_ERROR)
						.entity(ErrorLogHelper.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), ex, templateVars, logline))
						.build();
		} finally {
			// log success or failure
			if (ex == null)
				aaiLogger.info(logline, true, "0");
			else {
				aaiLogger.error(ex.getErrorObject(), logline, ex);
				aaiLogger.info(logline, false, ex.getErrorObject().getErrorCodeString());
			}
		}
		return response;
	}
	/* ---------------- End Notify PUT --------------------- */
	
}
