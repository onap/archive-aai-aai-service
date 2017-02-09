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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.openecomp.aai.db.AAIProperties;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.introspection.Loader;
import org.openecomp.aai.introspection.MarshallerProperties;
import org.openecomp.aai.introspection.ModelType;
import org.openecomp.aai.introspection.Version;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.rest.db.HttpEntry;
import org.openecomp.aai.serialization.db.DBSerializer;
import org.openecomp.aai.serialization.engines.QueryStyle;
import org.openecomp.aai.serialization.engines.TransactionalGraphEngine;

import com.thinkaurelius.titan.core.TitanTransaction;

/**
 * The Class VertexIdConsumer.
 */
@Path("{version: v[8]}/resources")
public class VertexIdConsumer extends RESTAPI {
	
	private ModelType introspectorFactoryType = ModelType.MOXY;
	private QueryStyle queryStyle = QueryStyle.TRAVERSAL;

	private final String ID_ENDPOINT = "/id/{vertexid: \\d+}";
	
	/**
	 * Gets the by vertex id.
	 *
	 * @param content the content
	 * @param versionParam the version param
	 * @param vertexid the vertexid
	 * @param depthParam the depth param
	 * @param headers the headers
	 * @param info the info
	 * @param req the req
	 * @return the by vertex id
	 */
	@GET
	@Path(ID_ENDPOINT)
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	public Response getByVertexId(String content, @PathParam("version")String versionParam, @PathParam("vertexid")long vertexid, @DefaultValue("all") @QueryParam("depth") String depthParam, @Context HttpHeaders headers, @Context UriInfo info, @Context HttpServletRequest req) {
				
		String outputMediaType = getMediaType(headers.getAcceptableMediaTypes());
		String sourceOfTruth = headers.getRequestHeaders().getFirst("X-FromAppId");
		String transId = headers.getRequestHeaders().getFirst("X-TransactionId");

		Version version = Version.valueOf(versionParam);
		

		LogLineBuilder llBuilder = new LogLineBuilder(transId, sourceOfTruth);
		LogLine logline = llBuilder.build(COMPONENT, HttpMethod.GET.toString());
		
		Status status = Status.NOT_FOUND;
		String result = "";
		Response response = null;
		TitanTransaction g = null;
		try {

			int depth = setDepth(depthParam);
			HttpEntry httpEntry = new HttpEntry(version, introspectorFactoryType, queryStyle, llBuilder);
			TransactionalGraphEngine dbEngine = httpEntry.getDbEngine();
			Loader loader = httpEntry.getLoader();
			g = dbEngine.getGraph().newTransaction();

			DBSerializer serializer = new DBSerializer(version, dbEngine, g, introspectorFactoryType, sourceOfTruth, llBuilder);
			
			Vertex thisVertex = null;
			Iterator<Vertex> itr = g.vertices(vertexid);
			
			if (!itr.hasNext()) {
				throw new AAIException("AAI_6114", "no node at that vertex id");
			}
			thisVertex = itr.next();
			String objName = thisVertex.<String>property(AAIProperties.NODE_TYPE).orElse(null);
			Introspector obj = loader.introspectorFromName(objName);
			List<Vertex> vertexListWrapper= new ArrayList<Vertex>();
			vertexListWrapper.add(thisVertex);
			String cleanUp = "false";
			obj = serializer.dbToObject(vertexListWrapper, obj, depth, cleanUp);
			if (obj != null) {
				status = Status.OK;
				//this tells it to include the xml root in the marshalled object
				MarshallerProperties marshprops = new MarshallerProperties.Builder(org.openecomp.aai.rest.MediaType.getEnum(outputMediaType))
																				.includeRoot(true)
																				.build();
				result = obj.marshal(marshprops);
			}
			response = Response.ok().entity(result).status(status).type(outputMediaType).build();
		} catch (AAIException e){
			response = consumerExceptionResponseGenerator(headers, info, HttpMethod.GET, e, logline);
		} catch (Exception e) {
			AAIException ex = new AAIException("AAI_4000", e);
			response = consumerExceptionResponseGenerator(headers, info, HttpMethod.GET, ex, logline);
		} finally { //to close the titan transaction (I think)
			if (g != null) {
				g.rollback();
			}
		}
		return response;
	}
}
