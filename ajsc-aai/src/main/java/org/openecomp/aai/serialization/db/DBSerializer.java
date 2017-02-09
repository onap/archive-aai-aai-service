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

package org.openecomp.aai.serialization.db;


import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.lang.ClassUtils;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.javatuples.Pair;
import org.openecomp.aai.db.AAIProperties;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.introspection.IntrospectorFactory;
import org.openecomp.aai.introspection.Loader;
import org.openecomp.aai.introspection.LoaderFactory;
import org.openecomp.aai.introspection.ModelType;
import org.openecomp.aai.introspection.Version;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.parsers.query.QueryParser;
import org.openecomp.aai.parsers.uri.URIToRelationshipObject;
import org.openecomp.aai.query.builder.QueryBuilder;
import org.openecomp.aai.serialization.engines.TransactionalGraphEngine;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIConstants;

import com.att.aft.dme2.internal.javaxwsrs.core.UriBuilder;
import com.google.common.base.Joiner;
import com.thinkaurelius.titan.core.SchemaViolationException;
import com.thinkaurelius.titan.core.TitanException;

public class DBSerializer {
	
	
	/*
	 * Todo:
	 *  - Generic query Interface
	 * 
	 */
	private final String className = DBSerializer.class.getSimpleName();
	private AAILogger aaiLogger = new AAILogger(DBSerializer.class.getName());
	private Graph graph = null;
	private AtomicInteger totalCount = null;
	private TransactionalGraphEngine engine = null;
	private final String uniqueId = "aai-unique-key";
	private String sourceOfTruth = "";
	private ModelType introspectionType = null;
	private String key = "";
	private Version version = AAIProperties.LATEST;
	private Loader latestLoader = null;
	private EdgeRules edgeRules = EdgeRules.getInstance();
	private Loader loader = null;
	private final LogLineBuilder llBuilder;

	/**
	 * Instantiates a new DB serializer.
	 *
	 * @param version the version
	 * @param engine the engine
	 * @param g the g
	 * @param introspectionType the introspection type
	 * @param sourceOfTruth the source of truth
	 * @param llBuilder the ll builder
	 */
	public DBSerializer(Version version, TransactionalGraphEngine engine, Graph g, ModelType introspectionType, String sourceOfTruth, LogLineBuilder llBuilder) {
		this(engine, g, introspectionType, sourceOfTruth, llBuilder);
		this.version = version;
		this.loader = LoaderFactory.createLoaderForVersion(introspectionType, version, llBuilder);

	}
	
	/**
	 * Instantiates a new DB serializer.
	 *
	 * @param engine the engine
	 * @param g the g
	 * @param introspectionType the introspection type
	 * @param sourceOfTruth the source of truth
	 * @param llBuilder the ll builder
	 */
	public DBSerializer(TransactionalGraphEngine engine, Graph g, ModelType introspectionType, String sourceOfTruth, LogLineBuilder llBuilder) {
		this.llBuilder = llBuilder;
		this.engine = engine;
		this.graph = g;
		this.totalCount = engine.getCount();
		this.sourceOfTruth = sourceOfTruth;
		this.introspectionType = introspectionType;
		this.loader = LoaderFactory.createLoaderForVersion(introspectionType, version, llBuilder);
		this.latestLoader = LoaderFactory.createLoaderForVersion(introspectionType, AAIProperties.LATEST, llBuilder);
	}
	
	/**
	 * Touch standard vertex properties.
	 *
	 * @param v the v
	 * @param isNewVertex the is new vertex
	 */
	/*
	 * to be defined and expanded later
	 */
	public void touchStandardVertexProperties(Vertex v, boolean isNewVertex) {
		
		/*v.setProperty(AAIProperties.NODE_TYPE, "");
		v.setProperty("aai-last-mod-ts", "");
		v.setProperty("aai-created-ts", "");
		v.setProperty("source-of-truth", "");
		v.setProperty("last-mod-source-of-truth", "");
		v.setProperty("_rest-url", "");
		*/
		long unixTimeNow = System.currentTimeMillis() / 1000L;
		String timeNowInSec = "" + unixTimeNow;
		if (isNewVertex) {
			v.property(AAIProperties.SOURCE_OF_TRUTH, this.sourceOfTruth);
			v.property(AAIProperties.CREATED_TS, timeNowInSec);

		}
		v.property(AAIProperties.RESOURCE_VERSION, timeNowInSec );
		v.property(AAIProperties.LAST_MOD_TS, timeNowInSec);
		v.property(AAIProperties.LAST_MOD_SOURCE_OF_TRUTH, this.sourceOfTruth);
	}
	
	/**
	 * Creates the new vertex.
	 *
	 * @param wrappedObject the wrapped object
	 * @param uri the uri
	 * @return the vertex
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws AAIException the AAI exception
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 */
	private Vertex createNewVertex(Introspector wrappedObject, String uri) throws IllegalArgumentException, AAIException, UnsupportedEncodingException {
		
		Vertex v = graph.addVertex();
		v.property(AAIProperties.NODE_TYPE, wrappedObject.getDbName());
		touchStandardVertexProperties(v, true);
		return v;
	}
	
	/**
	 * Creates the new vertex.
	 *
	 * @param wrappedObject the wrapped object
	 * @return the vertex
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 */
	public Vertex createNewVertex(Introspector wrappedObject) throws UnsupportedEncodingException, AAIException {

		return createNewVertex(wrappedObject, wrappedObject.getURI());
	}
	
	/**
	 * Trim class name.
	 *
	 * @param className the class name
	 * @return the string
	 */
	/*
	 * Removes the classpath from a class name
	 */
	public String trimClassName (String className) {
		String returnValue = "";
		
		if (className.lastIndexOf('.') == -1) {
			return className;
		}
		returnValue = className.substring(className.lastIndexOf('.') + 1, className.length());
		
		return returnValue;
	}
	
	/**
	 * Serialize to db.
	 *
	 * @param obj the obj
	 * @param v the v
	 * @param uriQuery the uri query
	 * @param identifier the identifier
	 * @throws SecurityException the security exception
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws InstantiationException the instantiation exception
	 * @throws InterruptedException the interrupted exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws AAIException the AAI exception
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 */
	public void serializeToDb(Introspector obj, Vertex v, QueryParser uriQuery, String identifier) throws SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException, InterruptedException, NoSuchMethodException, AAIException, UnsupportedEncodingException {

		int maxRetries = 10;
		int objectCount = 1;
		int retry = 0;
		for (retry = 0; retry < maxRetries; ++retry) {
			 try {
				if (uriQuery.isDependent()) {
					//try to find the parent
					List<Vertex> vertices = engine.getQueryEngine().executeParentQuery(graph, uriQuery.getQueryBuilder());
					if (vertices.size() > 0) {
						Vertex parent = vertices.get(0);
						this.reflectDependentVertex(parent, v, obj);
					} else {
						throw new AAIException("AAI_6114", "No parent Node of type " + uriQuery.getParentResultType() + " for " + identifier);
					}
				} else {
					processObject(obj, v);
				}
					
				break;
			 } catch (SchemaViolationException e) {
				 throw new AAIException("AAI_6117", e);
			 } catch (TitanException e) {
				graph.tx().rollback();
				AAIException ex = new AAIException("AAI_6142", e);
				aaiLogger.error(ex.getErrorObject(), llBuilder.build(className, "serialize to db"), e);
				Thread.sleep((retry + 1) * 20);
				graph = engine.getGraph().newTransaction();
			 }
		}
		
		if (retry == maxRetries) {
			throw new AAIException("AAI_6134");
		}
		
		totalCount.getAndAdd(objectCount);
		
	}
	
	/**
	 * Process object.
	 *
	 * @param <T> the generic type
	 * @param obj the obj
	 * @param v the v
	 * @return the list
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws InstantiationException the instantiation exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws SecurityException the security exception
	 * @throws AAIException the AAI exception
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 */
	/*
	 * Helper method for reflectToDb
	 * Handles all the property setting
	 */
	private <T> List<Vertex> processObject (Introspector obj, Vertex v) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException, NoSuchMethodException, SecurityException, AAIException, UnsupportedEncodingException {
		Object value = null;
		int objectCount = 0;
		String propertyType = "";
		List<String> properties = obj.getProperties();
		properties.remove(AAIProperties.RESOURCE_VERSION);
		List<Vertex> dependentVertexes = new ArrayList<>();
		List<Vertex> processedVertexes = new ArrayList<>();
		boolean isComplexType = false;
		boolean isListType = false;
		
		for (String property : properties) {
			propertyType = obj.getType(property);
			isComplexType = obj.isComplexType(property);
			isListType = obj.isListType(property);
			value = obj.getValue(property);

			if (!(isComplexType || isListType)) {
				if (value != null) {
					if (propertyType.toLowerCase().contains(".long")) {
						v.property(property, new Integer(((Long)value).toString()));
					} else {
						v.property(property, value);
					}
				} else {
					v.property(property).remove();
				}
			} else if (isListType) {
				List list = (List)value;
				if (obj.isComplexGenericType(property)) {
					if (list != null) {
						for (Object o : list) {
							Introspector child = IntrospectorFactory.newInstance(this.introspectionType, o, llBuilder);
							child.setURIChain(obj.getURI());
							processedVertexes.add(reflectDependentVertex(v, child));
						}
					}
				} else {
					//simple list case
					engine.setListProperty(v, property, list);
				}
			} else {
				//method.getReturnType() is not 'simple' then create a vertex and edge recursively returning an edge back to this method
				if (value != null) { //effectively ignore complex properties not included in the object we're processing
					if (value.getClass().isArray()) {
						
						int length = Array.getLength(value);
					    for (int i = 0; i < length; i ++) {
					        Object arrayElement = Array.get(value, i);
					        Introspector child = IntrospectorFactory.newInstance(this.introspectionType, arrayElement, llBuilder);
							child.setURIChain(obj.getURI());
							processedVertexes.add(reflectDependentVertex(v, child));

					    }
					} else if (!property.equals("relationship-list")) {
						// container case
						Introspector introspector = IntrospectorFactory.newInstance(this.introspectionType, value, llBuilder);
						if (introspector.isContainer()) {
							dependentVertexes.addAll(this.getDependentVertexesOfType(v, introspector.getChildDBName()));
							introspector.setURIChain(obj.getURI());
							
							processedVertexes.addAll(processObject(introspector, v));

						} else {
							dependentVertexes.addAll(this.getDependentVertexesOfType(v, introspector.getDbName()));
							processedVertexes.add(reflectDependentVertex(v, introspector));

						}
					} else if (property.equals("relationship-list")) {
						handleRelationships(obj, v);
					}
				}
			}
		}
		this.writeThroughDefaults(v, obj);
		/* handle those vertexes not touched */
		for (Vertex toBeRemoved : processedVertexes) {
			dependentVertexes.remove(toBeRemoved);
		}
		this.deleteItemsWithTraversal(dependentVertexes);
		
		this.totalCount.getAndAdd(objectCount);
		return processedVertexes;
	}
	
	/**
	 * Handle relationships.
	 *
	 * @param obj the obj
	 * @param vertex the vertex
	 * @throws SecurityException the security exception
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 */
	/*
	 * Handles the explicit relationships defined for an obj
	 */
	private void handleRelationships(Introspector obj, Vertex vertex) throws SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, UnsupportedEncodingException, AAIException {
		
	
		Object rl = null;
	
		rl = obj.getValue("relationship-list");
		Introspector wrappedRl = IntrospectorFactory.newInstance(this.introspectionType, rl, llBuilder);
		processRelationshipList(wrappedRl, vertex);
		
	
	}
	
	
	/**
	 * Process relationship list.
	 *
	 * @param wrapped the wrapped
	 * @param v the v
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 */
	private void processRelationshipList(Introspector wrapped, Vertex v) throws UnsupportedEncodingException, AAIException {
				
		List<Object> relationships = (List<Object>)wrapped.getValue("relationship");
	
		Vertex cousinVertex = null;
		Edge e = null;
		List<Edge> existingEdges = new ArrayList<>();
		GraphTraversal<Vertex, Edge> pipeline = graph.traversal().V(v).bothE().has("isParent", false).dedup();
		
		existingEdges = pipeline.toList();
		for (Object relationship : relationships) {
			Introspector wrappedRel = IntrospectorFactory.newInstance(this.introspectionType, relationship, llBuilder);
			QueryParser parser = engine.getQueryBuilder().createQueryFromRelationship(wrappedRel);
			
			List<Vertex> results = engine.getQueryEngine().executeQuery(graph, parser.getQueryBuilder());
			if (results.size() == 0) {
				AAIException ex = new AAIException("AAI_6129", "Node of type " + parser.getResultType() + ". Could not find object at: " + parser.getUri());
				List<String> templateVars = new ArrayList<>();
				templateVars.add(parser.getResultType());
				templateVars.add(parser.getUri().toString());
				ex.setTemplateVars(templateVars);
				throw ex;
			} else { 
				//still an issue if there's more than one
				cousinVertex = results.get(0);
			}
			
			if (cousinVertex != null) {
				e = this.getEdgeBetween(v, cousinVertex);
				
				if (e == null) {
					edgeRules.addEdge(v, cousinVertex);
				} else { 
					existingEdges.remove(e);
				}
			}
		}
		
		for (Edge edge : existingEdges) {
			edge.remove();
		}

	}
	
	/**
	 * Write through defaults.
	 *
	 * @param v the v
	 * @param obj the obj
	 */
	private void writeThroughDefaults(Vertex v, Introspector obj) {
		Introspector latest = this.latestLoader.introspectorFromName(obj.getName());
		if (latest != null) {
			List<String> required  = latest.getRequiredProperties();
			String defaultValue = null;
			Object vertexProp = null;
			for (String field : required) {
				defaultValue = latest.getPropertyMetadata(field).get("defaultValue");
				if (defaultValue != null) {
					vertexProp = v.<Object>property(field).orElse(null);
					if (vertexProp == null) {
						v.property(field, defaultValue);
					}
				}
			}
		}
		
	}

	
 	/**
	  * Reflect dependent vertex.
	  *
	  * @param v the v
	  * @param dependentObj the dependent obj
	  * @return the vertex
	  * @throws IllegalAccessException the illegal access exception
	  * @throws IllegalArgumentException the illegal argument exception
	  * @throws InvocationTargetException the invocation target exception
	  * @throws InstantiationException the instantiation exception
	  * @throws NoSuchMethodException the no such method exception
	  * @throws SecurityException the security exception
	  * @throws AAIException the AAI exception
	  * @throws UnsupportedEncodingException the unsupported encoding exception
	  */
	 private Vertex reflectDependentVertex(Vertex v, Introspector dependentObj) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException, NoSuchMethodException, SecurityException, AAIException, UnsupportedEncodingException {
		
 		//QueryParser p = this.engine.getQueryBuilder().createQueryFromURI(obj.getURI());
 		//List<Vertex> items = this.engine.getQueryEngine().executeQuery(p.getQuery());
 		QueryBuilder query = this.engine.getQueryBuilder(v);
 		query.createEdgeTraversal(v, dependentObj);
 		query.createKeyQuery(dependentObj);
 		
 		List<Vertex> items = this.engine.getQueryEngine().executeQuery(graph, query);
 		
 		Vertex dependentVertex = null;
 		if (items.size() == 1) {
 			dependentVertex = items.get(0);
			this.verifyResourceVersion("update", dependentObj.getDbName(), dependentVertex.<String>property(AAIProperties.RESOURCE_VERSION).orElse(null), (String)dependentObj.getValue(AAIProperties.RESOURCE_VERSION), (String)dependentObj.getURI());
 		} else {
			this.verifyResourceVersion("create", dependentObj.getDbName(), "", (String)dependentObj.getValue(AAIProperties.RESOURCE_VERSION), (String)dependentObj.getURI());
 			dependentVertex = createNewVertex(dependentObj);
 		}

 		return reflectDependentVertex(v, dependentVertex, dependentObj);
				
	}
 	
 	/**
	  * Reflect dependent vertex.
	  *
	  * @param parent the parent
	  * @param child the child
	  * @param obj the obj
	  * @return the vertex
	  * @throws IllegalAccessException the illegal access exception
	  * @throws IllegalArgumentException the illegal argument exception
	  * @throws InvocationTargetException the invocation target exception
	  * @throws InstantiationException the instantiation exception
	  * @throws NoSuchMethodException the no such method exception
	  * @throws SecurityException the security exception
	  * @throws AAIException the AAI exception
	  * @throws UnsupportedEncodingException the unsupported encoding exception
	  */
	 private Vertex reflectDependentVertex(Vertex parent, Vertex child, Introspector obj) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException, NoSuchMethodException, SecurityException, AAIException, UnsupportedEncodingException {
		
		
		processObject(obj, child);
		
		Edge e = this.getEdgeBetween(parent, child);
		if (e == null) {
			edgeRules.addTreeEdge(parent, child);
		}
		
		//e.setProperty("property-name", obj.getDbName());
		this.totalCount.getAndAdd(2);
		return child;
		
	}
 	 
 	/**
	  * Db to object.
	  *
	  * @param vertices the vertices
	  * @param obj the obj
	  * @param depth the depth
	  * @param cleanUp the clean up
	  * @return the introspector
	  * @throws AAIException the AAI exception
	  * @throws IllegalAccessException the illegal access exception
	  * @throws IllegalArgumentException the illegal argument exception
	  * @throws InvocationTargetException the invocation target exception
	  * @throws SecurityException the security exception
	  * @throws InstantiationException the instantiation exception
	  * @throws NoSuchMethodException the no such method exception
	  * @throws UnsupportedEncodingException the unsupported encoding exception
	  * @throws MalformedURLException the malformed URL exception
	  */
	 public Introspector dbToObject(List<Vertex> vertices, Introspector obj, int depth, String cleanUp) throws AAIException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, InstantiationException, NoSuchMethodException, UnsupportedEncodingException, MalformedURLException {
		
		if (vertices.size() > 1 && !obj.isContainer()) {
			throw new AAIException("AAI_6136", "query object mismatch: this object cannot hold multiple items." + obj.getDbName());
		} else if (obj.isContainer()) {
			List getList = null;
			String listProperty = "";
			for (String property : obj.getProperties()) {
				if (obj.isListType(property) && obj.isComplexGenericType(property)) {
					listProperty = property;
					break;
				}
			}

			getList = (List)obj.getValue(listProperty);

			for (Vertex v : vertices) {
				Set<Vertex> seen = new HashSet<>();
				Introspector childObject = obj.newIntrospectorInstanceOfNestedProperty(listProperty);
				dbToObject(childObject, v, seen, depth, cleanUp);
				getList.add(childObject.getUnderlyingObject());
			}
		} else if (vertices.size() == 1) {
			Set<Vertex> seen = new HashSet<>();
			obj = dbToObject(obj, vertices.get(0), seen, depth, cleanUp);
		} else {
			obj = null;
		}
		
		
		if (engine.shouldShutdown()) {
			try {
				graph.close();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return obj;
 	}
	
	/**
	 * Db to object.
	 *
	 * @param obj the obj
	 * @param v the v
	 * @param seen the seen
	 * @param depth the depth
	 * @param cleanUp the clean up
	 * @return the introspector
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws SecurityException the security exception
	 * @throws InstantiationException the instantiation exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 * @throws MalformedURLException the malformed URL exception
	 */
	private Introspector dbToObject(Introspector obj, Vertex v, Set<Vertex> seen, int depth, String cleanUp) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, InstantiationException, NoSuchMethodException, UnsupportedEncodingException, AAIException, MalformedURLException {
		
		if (depth < 0) {
			return null;
		}
		depth--;
		seen.add(v);
		List getList = null;
		List<Vertex> vertices = null;
		boolean modified = false;
		for (String property : obj.getProperties()) {
			

			if (!(obj.isComplexType(property) || obj.isListType(property))) {
				this.copySimpleProperty(property, obj, v);
				modified = true;
			} else {
				if (obj.isComplexType(property)) {
				/* container case */
	
					if (!property.equals("relationship-list") && depth >= 0) {
						Introspector argumentObject = obj.newIntrospectorInstanceOfProperty(property);
						Object result  = dbToObject(argumentObject, v, seen, depth+1, cleanUp);
						if (result != null) {
							obj.setValue(property, argumentObject.getUnderlyingObject());
							modified = true;
						}
					} else if (property.equals("relationship-list")){
						/* relationships need to be handled correctly */
						Object relationshipList = obj.newInstanceOfProperty(property);
						relationshipList = createRelationshipList(v, relationshipList, cleanUp);
						if (relationshipList != null) {
							modified = true;
							obj.setValue(property, relationshipList);
							modified = true;
						}
						
					}
				} else if (obj.isListType(property)) {
					
					if (property.equals("any")) {
						continue;
					}
					String genericType = obj.getGenericType(property);
					String childDbName = "";
					if (obj.isComplexGenericType(property) && depth >= 0) {
						childDbName = convertFromCamelCase(genericType);
						String vType = v.<String>property(AAIProperties.NODE_TYPE).orElse(null);
						EdgeRule rule = edgeRules.getEdgeRule(vType, childDbName);
						if (rule.getDirection().equals(Direction.OUT)) {
							vertices = this.engine.getQueryEngine().findRelatedVertices(v, Direction.OUT, rule.getLabel(), childDbName);
							
							if (vertices.size() > 0) {
								getList = (List)obj.getValue(property);
							}
							int removed = 0;
							for (Vertex childVertex : vertices) {
								if (!seen.contains(childVertex)) {
									Introspector argumentObject = obj.newIntrospectorInstanceOfNestedProperty(property);
									
									Object result = dbToObject(argumentObject, childVertex, seen, depth, cleanUp);
									if (result != null) { 
										getList.add(argumentObject.getUnderlyingObject());
									}
								} else {
									removed++;
									LogLine line = llBuilder.build("db to object", "cycle has been found");
									line.add("vertex id", childVertex.id().toString());
									aaiLogger.info(line, true, "AAI_6144");
								}
							}
							if (removed == vertices.size()) {
								//vertices were all seen, reset the list
								getList = null;
							} else if (vertices.size() > 0) {
								modified = true;
							}
						}
					} else if (obj.isSimpleGenericType(property)) {
						List temp = this.engine.getListProperty(v, property);
						if (temp != null) {
							getList = (List)obj.getValue(property);
							getList.addAll(temp);
							modified = true;
						}

					}

				}

			}
		}
		
		//no changes were made to this obj, discard the instance
		if (!modified) {
			return null;
		}
		
		return obj;
		
	}
	public Introspector getVertexProperties(Vertex v) throws AAIException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, InstantiationException, NoSuchMethodException, UnsupportedEncodingException, MalformedURLException {
		String nodeType = v.<String>property(AAIProperties.NODE_TYPE).orElse(null);
		if (nodeType == null) {
			throw new AAIException("AAI_6143");
		}
		Introspector obj = this.latestLoader.introspectorFromName(nodeType);
		Set<Vertex> seen = new HashSet<>();
		int depth = 0;
		String cleanUp = "false";
		this.dbToObject(obj, v, seen, depth, cleanUp);
		
		return obj;
		
	}
	public Introspector getLatestVersionView(Vertex v) throws AAIException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, InstantiationException, NoSuchMethodException, UnsupportedEncodingException, MalformedURLException {
		String nodeType = v.<String>property(AAIProperties.NODE_TYPE).orElse(null);
		if (nodeType == null) {
			throw new AAIException("AAI_6143");
		}
		Introspector obj = this.latestLoader.introspectorFromName(nodeType);
		Set<Vertex> seen = new HashSet<>();
		int depth = Integer.MAX_VALUE;
		String cleanUp = "false";
		this.dbToObject(obj, v, seen, depth, cleanUp);
		
		return obj;
	}
	/**
	 * Copy simple property.
	 *
	 * @param property the property
	 * @param obj the obj
	 * @param v the v
	 * @throws InstantiationException the instantiation exception
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws SecurityException the security exception
	 */
	private void copySimpleProperty(String property, Introspector obj, Vertex v) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		Object temp = v.<Object>property(property).orElse(null);
		if (temp != null) {
			/* this whole section is because of a disconnect between the types told to titan
			 * and the types in the POJOs. This may not be an issue anymore.
			 * 
			 */
			if (!temp.getClass().getName().equals(obj.getType(property))) {
				temp = temp.toString();
				Class<?> argumentClass = obj.getClass(property);
				if (argumentClass.isPrimitive()) {
					argumentClass = ClassUtils.primitiveToWrapper(argumentClass);		
				}
				temp = argumentClass.getConstructor(String.class).newInstance(temp.toString());
			}
			obj.setValue(property, temp);
		}
	}
	
	/**
	 * Simple db to object.
	 *
	 * @param obj the obj
	 * @param v the v
	 * @throws InstantiationException the instantiation exception
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws SecurityException the security exception
	 */
	private void simpleDbToObject (Introspector obj, Vertex v) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		for (String property : obj.getProperties()) {
			

			if (!(obj.isComplexType(property) || obj.isListType(property))) {
				this.copySimpleProperty(property, obj, v);
			}
		}
	}
	
	/**
	 * Creates the relationship list.
	 *
	 * @param v the v
	 * @param obj the obj
	 * @param cleanUp the clean up
	 * @return the object
	 * @throws InstantiationException the instantiation exception
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws SecurityException the security exception
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 * @throws MalformedURLException the malformed URL exception
	 */
	private Object createRelationshipList(Vertex v, Object obj, String cleanUp) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, UnsupportedEncodingException, AAIException, MalformedURLException {
		
		Iterator<Edge> inEdges = v.edges(Direction.IN);
		Iterator<Edge> outEdges = v.edges(Direction.OUT);

		Introspector wrappedRelationshipList = IntrospectorFactory.newInstance(introspectionType, obj, llBuilder);
		List<Object> relationshipObjList = (List<Object>)wrappedRelationshipList.getValue("relationship");
		Object temp = null;
		Object isParent = null;
		Edge edge = null;
		while (inEdges.hasNext()) {
			edge = inEdges.next();
			isParent = edge.<Boolean>property("isParent").orElse(null);
			if (isParent == null || isParent.equals(Boolean.FALSE)) {
				temp = wrappedRelationshipList.newInstanceOfNestedProperty("relationship");
				Introspector relationshipObj = IntrospectorFactory.newInstance(introspectionType, temp, llBuilder);
				Object result = processEdgeRelationship(relationshipObj, edge, Direction.OUT, cleanUp);
				if (result != null) {
					relationshipObjList.add(result);
				}
			}
		}
		
		while (outEdges.hasNext()) {
			edge = outEdges.next();
			isParent = edge.<Boolean>property("isParent").orElse(null);
			if (isParent == null || isParent.equals(Boolean.FALSE)) {
				temp = wrappedRelationshipList.newInstanceOfNestedProperty("relationship");
				Introspector relationshipObj = IntrospectorFactory.newInstance(introspectionType, temp, llBuilder);
				Object result = processEdgeRelationship(relationshipObj, edge, Direction.IN, cleanUp);
				if (result != null) {
					relationshipObjList.add(result);
				}
			}
		}
		
		if (relationshipObjList.isEmpty()) {
			obj = null;
		}
		return obj;
	}
	
	/**
	 * Process edge relationship.
	 *
	 * @param relationshipObj the relationship obj
	 * @param edge the edge
	 * @param direction the direction
	 * @param cleanUp the clean up
	 * @return the object
	 * @throws InstantiationException the instantiation exception
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws SecurityException the security exception
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 * @throws MalformedURLException the malformed URL exception
	 */
	private Object processEdgeRelationship(Introspector relationshipObj, Edge edge, Direction direction, String cleanUp) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, UnsupportedEncodingException, AAIException, MalformedURLException {
		Vertex cousin = null;

		if (direction.equals(Direction.IN)) {
			cousin = edge.inVertex();
		} else if (direction.equals(Direction.OUT)) {
			cousin = edge.outVertex();
		}
		Pair<Vertex, List<Introspector>> tuple = this.getParents(cousin, true);
		//damaged vertex found, ignore
		if (tuple == null) {
			return null;
		}
		List<Introspector> list = tuple.getValue1();
		URI uri = this.getURIFromList(list);
		
		URIToRelationshipObject uriParser = null;
		try {
			uriParser = new URIToRelationshipObject(this.loader, uri);
		} catch (AAIException e) {
			LogLine line = llBuilder.build("seralizer", "processing edge relationship");
			line.add("bad vertex id", tuple.getValue0().id().toString());
			aaiLogger.error(e.getErrorObject(), line, e);
			if ("true".equals(cleanUp)) {
				this.deleteWithTraversal(tuple.getValue0());
			}
			return null;
		}
		Introspector result = uriParser.getResult();
		if (list.size() > 0) {
			this.addRelatedToProperty(result, list.get(0));
		}
		return result.getUnderlyingObject();
	}
	
	/**
	 * Gets the URI for vertex.
	 *
	 * @param v the v
	 * @return the URI for vertex
	 * @throws InstantiationException the instantiation exception
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws SecurityException the security exception
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 */
	public URI getURIForVertex(Vertex v) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, UnsupportedEncodingException {
		Pair<Vertex, List<Introspector>> tuple = this.getParents(v, false);
		List<Introspector> list = tuple.getValue1();
		
		return this.getURIFromList(list);
	}
	
	/**
	 * Gets the URI from list.
	 *
	 * @param list the list
	 * @return the URI from list
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 */
	private URI getURIFromList(List<Introspector> list) throws UnsupportedEncodingException {
		String uri = "";
		StringBuilder sb = new StringBuilder();
		for (Introspector i : list) {
			sb.insert(0, i.getURI());
		}
		
		uri = sb.toString();
		URI result = UriBuilder.fromPath(uri).build();
		return result;
	}
	
	/**
	 * Gets the parents.
	 *
	 * @param start the start
	 * @param removeDamaged the remove damaged
	 * @return the parents
	 * @throws InstantiationException the instantiation exception
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws SecurityException the security exception
	 */
	private Pair<Vertex, List<Introspector>> getParents(Vertex start, boolean removeDamaged) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		String uri = "";
		List<Vertex> results = this.engine.getQueryEngine().findParents(start);
		List<Introspector> objs = new ArrayList<>();
		String nodeType = "";
		boolean shortCircuit = false;
		for (Vertex v : results) {
			nodeType = v.<String>property(AAIProperties.NODE_TYPE).orElse(null);
			//vertex on the other end of this edge is bad
			if (nodeType == null) {
				//log something here about what was found and that it was removed
				LogLine line = llBuilder.build(className, "getParents");
				line.add("vertexid", v.id().toString());
				AAIException e = new AAIException("AAI_6143");
				aaiLogger.error(e.getErrorObject(), line, e);
				if (removeDamaged) {
					this.deleteWithTraversal(v);
				}
				shortCircuit = true;
			} else {
				Introspector obj = this.loader.introspectorFromName(nodeType);
				if (obj != null) {
					this.simpleDbToObject(obj, v);
					objs.add(obj);
				}
			}
		}
		
		
		
		//stop processing and don't return anything for this bad vertex
		if (shortCircuit) {
			return null;
		}
		
		return new Pair<>(results.get(results.size()-1), objs);
	}
	
	/**
	 * Adds the related to property.
	 *
	 * @param relationship the relationship
	 * @param child the child
	 */
	public void addRelatedToProperty(Introspector relationship, Introspector child) {
		String nameProps = child.getMetadata("nameProps");
		List<Introspector> relatedToProperties = new ArrayList<>();
		
		if (nameProps != null) {
			String[] props = nameProps.split(",");
			for (String prop : props) {
				Introspector relatedTo = this.loader.introspectorFromName("related-to-property");
				relatedTo.setValue("property-key", child.getDbName() + "." + prop);
				relatedTo.setValue("property-value", child.getValue(prop));
				relatedToProperties.add(relatedTo);
			}
		}
		
		if (relatedToProperties.size() > 0) {
			List relatedToList = (List)relationship.getValue("related-to-property");
			for (Introspector obj : relatedToProperties) {
				relatedToList.add(obj.getUnderlyingObject());
			}
		}
		
	}
	
	/**
	 * Creates the edge.
	 *
	 * @param relationship the relationship
	 * @param inputVertex the input vertex
	 * @return true, if successful
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 */
	public boolean createEdge(Introspector relationship, Vertex inputVertex) throws UnsupportedEncodingException, AAIException {
		
		Vertex relatedVertex = null;
		
		QueryParser parser = engine.getQueryBuilder().createQueryFromRelationship(relationship);
		
		List<Vertex> results = engine.getQueryEngine().executeQuery(graph, parser.getQueryBuilder());
		if (results.size() == 0) {
			AAIException e = new AAIException("AAI_6129", "Node of type " + parser.getResultType() + ". Could not find object at: " + parser.getUri());
			List<String> templateVars = new ArrayList<>();
			templateVars.add(parser.getResultType());
			templateVars.add(parser.getUri().toString());
			e.setTemplateVars(templateVars);
			throw e;
		} else { 
			//still an issue if there's more than one
			relatedVertex = results.get(0);
		}

		if (relatedVertex != null) {

			Edge e = this.getEdgeBetween(inputVertex, relatedVertex);
			if (e == null) {				
				Edge edge = null;
				edgeRules.addEdge(inputVertex, relatedVertex);
				
			} else {
				//attempted to link two vertexes already linked
			}
			
		}
		
		return true;
	}
	
	/**
	 * Gets the edges between.
	 *
	 * @param outVertex the out vertex
	 * @param inVertex the in vertex
	 * @return the edges between
	 * @throws AAIException the AAI exception
	 */
	private List<Edge> getEdgesBetween(Vertex outVertex, Vertex inVertex) throws AAIException {
		
		List<Edge> result = new ArrayList<>();
		
		if (inVertex != null) {
				EdgeRule rule = edgeRules.getEdgeRule(outVertex, inVertex);
				GraphTraversal<Vertex, Edge> findEdgesBetween = null;
				findEdgesBetween = graph.traversal().V(outVertex).bothE().filter(__.otherV().hasId(inVertex.id()));
				List<Edge> edges = findEdgesBetween.toList();
				for (Edge edge : edges) {
					if (edge.label().equals(rule.getLabel())) {
						result.add(edge);
					}
				}

		}
		
		return result;
	}
	
	/**
	 * Gets the edge between.
	 *
	 * @param outVertex the out vertex
	 * @param inVertex the in vertex
	 * @return the edge between
	 * @throws AAIException the AAI exception
	 */
	private Edge getEdgeBetween(Vertex outVertex, Vertex inVertex) throws AAIException {
		
		
		
		if (inVertex != null) {

				List<Edge> edges = this.getEdgesBetween(outVertex, inVertex);
				
				if (edges.size() > 0) {
					return edges.get(0);
				}

		}
		
		return null;
	}
	

	/**
	 * Delete edge.
	 *
	 * @param relationship the relationship
	 * @param inputVertex the input vertex
	 * @return true, if successful
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 */
	public boolean deleteEdge(Introspector relationship, Vertex inputVertex) throws UnsupportedEncodingException, AAIException {
		
		Vertex relatedVertex = null;

		QueryParser parser = engine.getQueryBuilder().createQueryFromRelationship(relationship);
		
		List<Vertex> results = engine.getQueryEngine().executeQuery(graph, parser.getQueryBuilder());
		//dangerous
		relatedVertex = results.get(0);
		
		Edge edge = this.getEdgeBetween(inputVertex, relatedVertex);
		if (edge != null) {
			edge.remove();
			return true;
		} else {
			return false;
		}
		
	}
	
	
	/**
	 * Gets the dependent vertexes of type.
	 *
	 * @param start the start
	 * @param nodeType the node type
	 * @return the dependent vertexes of type
	 */
	private List<Vertex> getDependentVertexesOfType(Vertex start, String nodeType) {
		
		List<Vertex> p = graph.traversal().V(start).outE().has("isParent", true).inV().has(AAIProperties.NODE_TYPE, nodeType).dedup().toList();

		return p;
	}
	
	/**
	 * Delete items with traversal.
	 *
	 * @param vertexes the vertexes
	 * @throws IllegalStateException the illegal state exception
	 */
	public void deleteItemsWithTraversal(List<Vertex> vertexes) throws IllegalStateException {
		for (Vertex v : vertexes) {
			deleteWithTraversal(v);
		}
	}
	
	/**
	 * Delete with traversal.
	 *
	 * @param startVertex the start vertex
	 */
	public void deleteWithTraversal(Vertex startVertex) {
		
		List<Vertex> results = this.engine.getQueryEngine().findDeletable(startVertex);
		
		for (Vertex v : results) {
			v.remove();
		}
		
	}

	/**
	 * Delete.
	 *
	 * @param v the v
	 * @param resourceVersion the resource version
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws AAIException the AAI exception
	 * @throws InterruptedException the interrupted exception
	 */
	public void delete(Vertex v, String resourceVersion) throws IllegalArgumentException, AAIException, InterruptedException {
	
		boolean result = verifyDeleteSemantics(v, resourceVersion);
		if (result) {
			int maxRetries = 10;
			int retry = 0;
			for (retry = 0; retry < maxRetries; ++retry) {
				try {
					try {
						deleteWithTraversal(v);
						// deleteHelper(vertices);
						break;
					} catch (IllegalStateException e) {
						throw new AAIException("AAI_6110", e);
					}
				} catch (TitanException e) {
					graph.tx().rollback();
					AAIException ex = new AAIException("AAI_6142", e);
					aaiLogger.error(ex.getErrorObject(), llBuilder.build(className, "delete vertex"), e);
					Thread.sleep((retry + 1) * 20);
					graph = engine.getGraph().newTransaction();
				}
			}
			if (retry == maxRetries) {
				throw new AAIException("AAI_6134");
			}
			if (engine.shouldShutdown()) {
				try {
					graph.close();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
	}
	

	/**
	 * Verify delete semantics.
	 *
	 * @param vertex the vertex
	 * @param resourceVersion the resource version
	 * @return true, if successful
	 * @throws AAIException the AAI exception
	 */
	private boolean verifyDeleteSemantics(Vertex vertex, String resourceVersion) throws AAIException {
		boolean result = false;
		String nodeType = "";
		DeleteSemantic semantic = null;
		List<Edge> inEdges = null;
		List<Edge> outEdges = null;
		String errorDetail = " unknown delete semantic found";
		String aaiExceptionCode = "";
		nodeType = vertex.<String>property(AAIProperties.NODE_TYPE).orElse(null);
		if (!this.verifyResourceVersion("delete", nodeType, vertex.<String>property(AAIProperties.RESOURCE_VERSION).orElse(null), resourceVersion, nodeType)) {
		}
		semantic = edgeRules.getDeleteSemantic(nodeType);
		inEdges = (List<Edge>)IteratorUtils.toList(vertex.edges(Direction.IN));
		outEdges = (List<Edge>)IteratorUtils.toList(vertex.edges(Direction.OUT));
		if (semantic.equals(DeleteSemantic.CASCADE_TO_CHILDREN)) {
			result = true;
		} else if (semantic.equals(DeleteSemantic.ERROR_IF_ANY_EDGES)) {
			if (inEdges.size() == 0 && outEdges.size() == 0) {
				result = true;
			} else {
				errorDetail = " Node cannot be deleted because it still has Edges and the " + semantic + " scope was used.\n";
				aaiExceptionCode = "AAI_6110";
			}
		} else if (semantic.equals(DeleteSemantic.ERROR_IF_ANY_IN_EDGES) || semantic.equals(DeleteSemantic.ERROR_4_IN_EDGES_OR_CASCADE)) {
			
			if (inEdges.size() == 0) {
				result = true;
			} else if (inEdges.size() == 1) {
				//the only in edge is a parent edge, this node is still safe to delete
				Boolean isParent = inEdges.get(0).<Boolean>property("isParent").orElse(null);
				if (isParent != null && isParent) {
					result = true;
				}
			}
			
			if (!result) {
				errorDetail = " Node cannot be deleted because it still has Edges and the " + semantic + " scope was used.\n";
				aaiExceptionCode = "AAI_6110";
			}
		} else if (semantic.equals(DeleteSemantic.THIS_NODE_ONLY)) {
			if (outEdges.size() == 0) {
				result = true;
			} else {
				result = true;
				for (Edge edge : outEdges) {
					Object property = edge.<Boolean>property("isParent").orElse(null);
					if (property != null && property.equals(Boolean.TRUE)) {
						Vertex v = edge.inVertex();
						String vType = v.<String>property(AAIProperties.NODE_TYPE).orElse(null);
						errorDetail = " Node cannot be deleted using scope = " + semantic + 
								" another node (type = " + vType + ") depends on it for uniqueness.";
						aaiExceptionCode = "AAI_6110";
						result = false;
						break;
					}
				}
			}
		}
		
		
		if (!result) {
			throw new AAIException(aaiExceptionCode, errorDetail); 
		}
		return result;
	}

	/**
	 * Verify resource version.
	 *
	 * @param action the action
	 * @param nodeType the node type
	 * @param currentResourceVersion the current resource version
	 * @param resourceVersion the resource version
	 * @param uri the uri
	 * @return true, if successful
	 * @throws AAIException the AAI exception
	 */
	public boolean verifyResourceVersion(String action, String nodeType, String currentResourceVersion, String resourceVersion, String uri) throws AAIException {
		String enabled = "";
		String errorDetail = "";
		String aaiExceptionCode = "";
		if (currentResourceVersion == null) {
			currentResourceVersion = "";
		}
		
		if (resourceVersion == null) {
			resourceVersion = "";
		}
		try {
			enabled = AAIConfig.get(AAIConstants.AAI_RESVERSION_ENABLEFLAG);
		} catch (AAIException e) {
			aaiLogger.error(e.getErrorObject(), llBuilder.build(className, "read property file"), e);

		}

		if (enabled.equals("true")) {
			if (!currentResourceVersion.equals(resourceVersion)) {
				if (action.equals("create") && !resourceVersion.equals("")) {
					errorDetail = "resource-version passed for " + action + " of " + uri;
					aaiExceptionCode = "AAI_6135";
				} else if (resourceVersion.equals("")) {
					errorDetail = "resource-version not passed for " + action + " of " + uri;
					aaiExceptionCode = "AAI_6130";
				} else {
					errorDetail = "resource-version MISMATCH for " + action + " of " + uri;
					aaiExceptionCode = "AAI_6131";
				}
				
				throw new AAIException(aaiExceptionCode, errorDetail); 
				
			}
		}
		return true;
	}
	
	/**
	 * Convert from camel case.
	 *
	 * @param name the name
	 * @return the string
	 */
	private String convertFromCamelCase (String name) {
		
		Pattern p = Pattern.compile("(?:[A-Z][a-z0-9]+)|(?:[A-Z]+(?=[A-Z]))");
		Matcher m = p.matcher(name);
		
		ArrayList<String> list = new ArrayList<String>();
		String result = "";
		while(m.find()) {
			list.add(m.group(0));
		}
		if (list.size() > 0) {
			result = Joiner.on("-").join(list).toLowerCase();
			if (result.equals("cvlan-tag-entry")) {
				result = "cvlan-tag";
			}
			return result;
		} else {
			return name;
		}
	}
	
	
	/**
	 * Gets the key.
	 *
	 * @return the key
	 */
	public String getKey() {
		return this.key;
	}
}
