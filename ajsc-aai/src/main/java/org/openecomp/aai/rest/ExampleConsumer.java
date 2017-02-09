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

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.introspection.Loader;
import org.openecomp.aai.introspection.LoaderFactory;
import org.openecomp.aai.introspection.MarshallerProperties;
import org.openecomp.aai.introspection.ModelType;
import org.openecomp.aai.introspection.Version;
import org.openecomp.aai.introspection.generator.CreateExample;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.logging.LogLineBuilder;

import javax.ws.rs.core.UriInfo;

/**
 * The Class ExampleConsumer.
 */
@Path("/{version: v[8]}/examples")
public class ExampleConsumer extends RESTAPI {

	
	/**
	 * Gets the example.
	 *
	 * @param versionParam the version param
	 * @param type the type
	 * @param headers the headers
	 * @param info the info
	 * @param req the req
	 * @return the example
	 */
	@GET
	@Path("/{objectType: [^\\/]+}")
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	public Response getExample(@PathParam("version")String versionParam,  @PathParam("objectType")String type, @Context HttpHeaders headers, @Context UriInfo info, @Context HttpServletRequest req) {
		
		Status status = Status.INTERNAL_SERVER_ERROR;
		Response response = null;
		String transId = headers.getRequestHeaders().getFirst("X-TransactionId");
		String sourceOfTruth = headers.getRequestHeaders().getFirst("X-FromAppId");
		LogLineBuilder llBuilder = new LogLineBuilder(transId, sourceOfTruth);
		LogLine logline = llBuilder.build(COMPONENT,  HttpMethod.PUT.toString());
		
		try {
			String mediaType = getMediaType(headers.getAcceptableMediaTypes());
			org.openecomp.aai.rest.MediaType outputMediaType = org.openecomp.aai.rest.MediaType.getEnum(mediaType);
			
			Version version = Version.valueOf(versionParam);
			Loader loader = LoaderFactory.createLoaderForVersion(ModelType.MOXY, version, llBuilder);
			
			CreateExample example = new CreateExample(loader, type);
			
			Introspector obj = example.getExampleObject();
			String result = "";
			if (obj != null) {
				status = Status.OK;
				MarshallerProperties properties = 
						new MarshallerProperties.Builder(outputMediaType).build();
				result = obj.marshal(properties);
			} else {
				
			}
			response = Response
					.ok(obj)
					.entity(result)
					.status(status)
					.type(outputMediaType.toString()).build();
//		} catch (AAIException e) {
//			//TODO check that the details here are sensible
//			response = consumerExceptionResponseGenerator(headers, info, HttpMethod.GET, e, logline);
		} catch (Exception e) {
			AAIException ex = new AAIException("AAI_4000", e);
			response = consumerExceptionResponseGenerator(headers, info, HttpMethod.GET, ex, logline);
		}
		return response;
	}
}
