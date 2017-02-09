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

package org.openecomp.aai.rest.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang.StringUtils;
import org.openecomp.aai.domain.responseMessage.AAIResponseMessage;
import org.openecomp.aai.domain.responseMessage.AAIResponseMessageData;
import org.openecomp.aai.domain.responseMessage.AAIResponseMessageDatum;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.logging.ErrorObject;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.rest.RESTAPI;

/**
 * The Class EchoResponse.
 */
public class EchoResponse extends RESTAPI {
	
	protected static String authPolicyFunctionName = "util";
	
	protected static AAILogger aaiLogger = new AAILogger(EchoResponse.class.getName());
	
	public static final String echoPath = "/util/echo";

	/**
	 * Simple health-check API that echos back the X-FromAppId and X-TransactionId to clients.
	 * If there is a query string, a transaction gets logged into hbase, proving the application is connected to the data store.
	 * If there is no query string, no transacction logging is done to hbase.
	 *
	 * @param headers the headers
	 * @param req the req
	 * @param myAction if exists will cause transaction to be logged to hbase
	 * @return the response
	 */
	@GET
	@Produces( { MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@Path(echoPath)
	public Response echoResult(@Context HttpHeaders headers, @Context HttpServletRequest req,
			@QueryParam("action") String myAction) {
		Response response = null;
		AAIResponseMessage aaiRespMessage = new AAIResponseMessage();
		AAIResponseMessageData messageData = aaiRespMessage.getAaiResponseMessageData();
		
		AAIException ex = null;
		String fromAppId = null;
		String transId = null;
		LogLine logline = new LogLine();
		
		try { 
			fromAppId = getFromAppId(headers, logline );
			transId = getTransId(headers, logline);
		} catch (AAIException e) { 
			ArrayList<String> templateVars = new ArrayList<String>();
			templateVars.add("GET echo");
			templateVars.add(fromAppId +" "+transId);
			return Response
					.status(e.getErrorObject().getHTTPResponseCode())
					.entity(ErrorLogHelper.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), e, templateVars, logline))
					.build();
		}
		
		try {
			logline.init(COMPONENT, transId, fromAppId,	"echoResult");
			
			HashMap<AAIException, ArrayList<String>> exceptionList = new HashMap<AAIException, ArrayList<String>>();
					
			ArrayList<String> templateVars = new ArrayList<String>();
			templateVars.add(fromAppId);
			templateVars.add(transId);
		
			exceptionList.put(new AAIException("AAI_0002", "OK"), templateVars);
				
			response = Response.status(Status.OK)
					.entity(ErrorLogHelper.getRESTAPIInfoResponse(
							headers.getAcceptableMediaTypes(), exceptionList, logline))
							.build();
			
//		} catch (AAIException e) {
//			ex = e;
//			ArrayList<String> templateVars = new ArrayList<String>();
//			templateVars.add(Action.GET.name());
//			templateVars.add(fromAppId +" "+transId);
//
//			response = Response
//					.status(e.getErrorObject().getHTTPResponseCode())
//					.entity(ErrorLogHelper.getRESTAPIErrorResponse(
//							headers.getAcceptableMediaTypes(), e, templateVars,
//							logline)).build();
		} catch (Exception e) {
			ex = new AAIException("AAI_4000", e);
			ArrayList<String> templateVars = new ArrayList<String>();
			templateVars.add(Action.GET.name());
			templateVars.add(fromAppId +" "+transId);

			response = Response
					.status(Status.INTERNAL_SERVER_ERROR)
					.entity(ErrorLogHelper.getRESTAPIErrorResponse(
							headers.getAcceptableMediaTypes(), ex,
							templateVars, logline)).build();

		} finally {
			if (ex == null) {
				aaiLogger.info(logline, true, "0");
			} else {
				aaiLogger.error(ex.getErrorObject(), logline, ex);
				aaiLogger.info(logline, false, ex.getErrorObject().getErrorCodeString());
			}

		}
		
		return response;
	}

}
