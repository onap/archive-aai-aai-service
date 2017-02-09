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

package org.openecomp.aai.testing;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBException;

import org.apache.commons.lang.StringUtils;
import org.json.JSONException;
import org.openecomp.aai.audit.ListEndpoints;
import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.introspection.IntrospectorFactory;
import org.openecomp.aai.introspection.Loader;
import org.openecomp.aai.introspection.LoaderFactory;
import org.openecomp.aai.introspection.ModelType;
import org.openecomp.aai.introspection.Version;
import org.openecomp.aai.logging.LogLineBuilder;

import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;

public class LocateEndPoints {

	private String restPath = "";
	private String pojoPath = "";
	
	private PopulateObject populator = null;
	private ArrayList<String> parentEndPoints = null;
	private ArrayList<String> childEndPoints = null;
	private final Pattern parentPattern = Pattern.compile("\\/(?:[\\w\\-]+?\\/)+?(?:\\{[\\w\\-]+?\\}\\/?)+");

	private Map<String, List<String>> deprecatedEndpoints = new HashMap<>();
	private Pattern apiVersionPattern =  Pattern.compile("\\/(v\\d+)\\/");
	private Version version = null;
	private ModelType modelType = ModelType.MOXY;
	private Loader loader = null;
	protected final LogLineBuilder llBuilder = new LogLineBuilder();

	/**
	 * Instantiates a new locate end points.
	 *
	 * @param version the version
	 * @throws FileNotFoundException the file not found exception
	 * @throws JSONException the JSON exception
	 */
	public LocateEndPoints (Version version) throws FileNotFoundException, JSONException {

		this.version = version;
		populator = new PopulateObject();
		loader = LoaderFactory.createLoaderForVersion(modelType, version, llBuilder);
		this.parentEndPoints = new ArrayList<>();
		this.childEndPoints = new ArrayList<>();
		String[] versions = {"v2","v3", "v4", "v5"};
		deprecatedEndpoints.put("site-pair-set", Arrays.asList(versions));

		this.process();
		
	}
	
	/**
	 * Instantiates a new locate end points.
	 */
	@SuppressWarnings("unused")
	private LocateEndPoints() {}
	
	/**
	 * Process.
	 */
	private void process(){

		ListEndpoints endpoints = new ListEndpoints(this.version);
		String filterOut = "relationship-list";
		List<String> uris = endpoints.getEndpoints(filterOut);
		
		for (String uri : uris) {
			this.addEndPointName(uri);
		}

	}
	
	/**
	 * Checks if is deprecated.
	 *
	 * @param name the name
	 * @return true, if is deprecated
	 */
	private boolean isDeprecated(String name) {

		Matcher m = apiVersionPattern.matcher(name);
		name = name.substring(name.lastIndexOf('.') + 1, name.length());
		String apiVersion = "";
		
		if (m.find()) {
			apiVersion = m.group(1);
		}
		for (String key : deprecatedEndpoints.keySet()) {
			if (name.contains(key)) {
				List<String> deprecatedVersions = this.deprecatedEndpoints.get(key);
				
				if (deprecatedVersions != null && deprecatedVersions.contains(apiVersion)) {
					return true;
				}
			}
		}
	
		return false;
	}

	/**
	 * Gets the parent paths.
	 *
	 * @return the parent paths
	 */
	public List<String> getParentPaths() {
		
		return (List<String>)this.parentEndPoints.clone();
	}
	
	/**
	 * Gets the child paths.
	 *
	 * @return the child paths
	 */
	public List<String> getChildPaths() {
		
		return (List<String>)this.childEndPoints.clone();
	}
	
	/**
	 * Gets the all paths.
	 *
	 * @return the all paths
	 */
	public List<String> getAllPaths() {
		List<String> returnList = new ArrayList<>();
		returnList.addAll(getParentPaths());
		returnList.addAll(getChildPaths());
		return returnList;
	}
	
	/**
	 * Adds the end point name.
	 *
	 * @param path the path
	 */
	private void addEndPointName(String path) {
		
		if (!this.isDeprecated(path)) {
			if (parentPattern.matcher(path).matches()) {
				parentEndPoints.add(path);		
			} else {
				childEndPoints.add(path);
			}
		}
	}
	
	/**
	 * Child path.
	 *
	 * @param path the path
	 * @return the string
	 */
	public String childPath(String path) {
		
		String childPath = "";
		String[] pathSegments = path.split("/");
		boolean foundNonVariable = false;
		for (int i = pathSegments.length-1; i >= 0; i--) {
			
			if (!foundNonVariable && pathSegments[i].contains("{")) {
				childPath = pathSegments[i] + "/" + childPath;
			} else if (foundNonVariable && pathSegments[i].contains("{")) {
				break;
			} else {
				foundNonVariable = true;
				childPath = pathSegments[i] + "/" + childPath;
			}
			
		}
		
		childPath = childPath.substring(0, childPath.length()-1).replace("//", "/");
		
		return childPath;
		
	}
	
	/**
	 * Creates the child for path.
	 *
	 * @param path the path
	 * @param min the min
	 * @param max the max
	 * @return the introspector
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws InstantiationException the instantiation exception
	 * @throws JSONException the JSON exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws JAXBException the JAXB exception
	 */
	public Introspector createChildForPath(String path, int min, int max) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException, JSONException, IOException, JAXBException {
		return createChildForPath(path, min, max, false);
	}
	
	/**
	 * Creates the child for path.
	 *
	 * @param path the path
	 * @param min the min
	 * @param max the max
	 * @param minimized the minimized
	 * @return the introspector
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws InstantiationException the instantiation exception
	 * @throws JSONException the JSON exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws JAXBException the JAXB exception
	 */
	public Introspector createChildForPath(String path, int min, int max, boolean minimized) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException, JSONException, IOException, JAXBException {
		Pattern p = Pattern.compile("([\\w\\-]+?)\\/(\\{[\\w\\-]+?\\}\\/?)+$");
		Matcher m = p.matcher(path);
		String objectName = "";
		if (m.find()) {
			objectName = m.group(1);
		}
		if (objectName.equals("cvlan-tag")) {
			objectName = "cvlan-tag-entry";
		}
		
		return createChildFromClassName(objectName, min, max, minimized);
	}
	
	/**
	 * Creates the child from class name.
	 *
	 * @param objectName the object name
	 * @param min the min
	 * @param max the max
	 * @return the introspector
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws InstantiationException the instantiation exception
	 * @throws JSONException the JSON exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws JAXBException the JAXB exception
	 */
	public Introspector createChildFromClassName(String objectName, int min, int max) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException, JSONException, IOException, JAXBException {
		return createChildFromClassName(objectName, min, max, false);
	}
	
	/**
	 * Creates the child from class name.
	 *
	 * @param objectName the object name
	 * @param min the min
	 * @param max the max
	 * @param minimized the minimized
	 * @return the introspector
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws InstantiationException the instantiation exception
	 * @throws JSONException the JSON exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws JAXBException the JAXB exception
	 */
	public Introspector createChildFromClassName(String objectName, int min, int max, boolean minimized) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException, JSONException, IOException, JAXBException {
		Introspector returnObject = null;
	
		returnObject = loader.introspectorFromName(objectName);
		populator.populateRecursively(returnObject, min, max, minimized);
		
		
		return returnObject;
	}
	
	/**
	 * Populate relationship.
	 *
	 * @param parent the parent
	 * @param dbName the db name
	 * @param fullPath the full path
	 * @return the introspector
	 */
	public Introspector populateRelationship(Introspector parent, String dbName, String fullPath) {
		
		String formattedClassName = parent.getDbName();
		
		String advancedPath = StringUtils.substringAfter(fullPath, formattedClassName + "/");
		String[] pathTokens = advancedPath.split("/");
		
		// Create and format the relationship
		Introspector relationship = loader.introspectorFromName("relationship");
		relationship.setValue("related-to", dbName);
		populateRelationshipHelper(parent, pathTokens, 0, relationship);
		
		return relationship;
	}
	
	/**
	 * Populate relationship helper.
	 *
	 * @param parent the parent
	 * @param pathTokens the path tokens
	 * @param index the index
	 * @param relationship the relationship
	 */
	// Recursively iterate through each token and add the necessary information to the relationship data array
	private void populateRelationshipHelper(Introspector parent, String[] pathTokens, int index, Introspector relationship) {
		Pattern p = Pattern.compile("\\{(.+?)\\}");
		for(int i = index; i < pathTokens.length; ++i) {
			Matcher m = p.matcher(pathTokens[i]);
			if(m.find()) {
				String pathVariable = m.group(1);
				String trimmedClassName = parent.getDbName();
				pathVariable = pathVariable.replaceFirst(trimmedClassName+ "-", "");
				String key = this.callGetMethod(parent, pathVariable).toString();
				pathTokens[i] = pathTokens[i].replaceAll("\\{.+?\\}", key);
				
				// Add the relationship data object from token information
				Introspector data = relationship.newIntrospectorInstanceOfNestedProperty("relationship-data");
				data.setValue("relationship-key", trimmedClassName + "." + pathVariable);
				data.setValue("relationship-value", key);
				((List)relationship.getValue("relationship-data")).add(data.getUnderlyingObject());
				if (i == pathTokens.length-1) {
					List<Introspector> relatedTos = new ArrayList<>();
					String nameProps = parent.getMetadata("nameProps");
					if (nameProps != null) {
						String[] props = nameProps.split(",");
						for (String prop : props) {
							Introspector relatedTo = this.loader.introspectorFromName("related-to-property");
							relatedTo.setValue("property-key", parent.getDbName() + "." + prop);
							relatedTo.setValue("property-value", parent.getValue(prop));
							relatedTos.add(relatedTo);
						}
						
						if (relatedTos.size() > 0) {
							List list = (List)relationship.getValue("related-to-property");
							for (Introspector obj : relatedTos) {
								list.add(obj.getUnderlyingObject());
							}
						}
					}
				}
			} else {
				String getMethodName;
				if (pathTokens[i].equals("cvlan-tag")) {
					getMethodName = "cvlan-tag-entry";
				} else {
					getMethodName = pathTokens[i];
				}
				Object child = this.callGetMethod(parent, getMethodName);
				if (child != null) {
					if (child.getClass().getName().contains("List")) {
						child = ((List)child).get(0);
					}
					populateRelationshipHelper(IntrospectorFactory.newInstance(parent.getModelType(), child, llBuilder), pathTokens, i+1, relationship);
					break;
				}
			}
		}
	}
	
	/**
	 * Populate path.
	 *
	 * @param parent the parent
	 * @param path the path
	 * @return the list
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws SecurityException the security exception
	 */
	public List<Object> populatePath(Introspector parent, String path) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		
		String formattedClassName = parent.getDbName();

		String prefix = StringUtils.substringBefore(path, "{");
		String advancedPath = StringUtils.substringAfter(path, formattedClassName+"/");
		String[] pathTokens = advancedPath.split("/");
		
		Introspector end = populatePathHelper(parent, pathTokens, 0);
		String parentPath = prefix + Joiner.on("/").join(pathTokens);
		List<Object> result = new ArrayList<>();
		result.add(parentPath);
		result.add(end.clone());
		return result;
	}
	
	/**
	 * Populate child path.
	 *
	 * @param topLevelObj the top level obj
	 * @param obj the obj
	 * @param path the path
	 * @return the string
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws SecurityException the security exception
	 */
	public String populateChildPath(Introspector topLevelObj, Introspector obj, String path) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		String childPath = "/" + this.childPath(path);
		String parentPath = path.replace(childPath, "");
		String resultPath = (String)this.populatePath(topLevelObj, parentPath).get(0) + (String)this.populatePath(obj, childPath).get(0);
		
		return resultPath;
	}
	
	/**
	 * Populate path helper.
	 *
	 * @param parent the parent
	 * @param pathTokens the path tokens
	 * @param index the index
	 * @return the introspector
	 */
	private Introspector populatePathHelper(Introspector parent, String[] pathTokens, int index) {

		Pattern p = Pattern.compile("\\{(.+?)\\}");
		Matcher m = null;
		String pathVariable = "";
		String key = "";
		Object child = null;
		String getMethodName = "";
		for (int i = index; i < pathTokens.length; i++) {
			m = p.matcher(pathTokens[i]);
			if (m.find()) {
				pathVariable = m.group(1);
				
				pathVariable = pathVariable.replaceFirst(parent.getDbName() + "-", "");
				
				key = this.callGetMethod(parent, pathVariable).toString();
				pathTokens[i] = pathTokens[i].replaceAll("\\{.+?\\}", key);
			} else {
				
				if (pathTokens[i].equals("cvlan-tag")) {
					getMethodName = "cvlan-tag-entry";
				} else {
					getMethodName = pathTokens[i];
				}
				child = this.callGetMethod(parent, getMethodName);
				if (child != null) {
					if (child.getClass().getName().contains("List")) {
						child = ((List)child).get(0);
					}
					return populatePathHelper(IntrospectorFactory.newInstance(parent.getModelType(), child, llBuilder), pathTokens, i+1);
				}
			}
		}
		
		return parent;
	}
	
	/**
	 * Gets the path for object.
	 *
	 * @param parentPath the parent path
	 * @param obj the obj
	 * @return the path for object
	 */
	public String getPathForObject(String parentPath, Object obj) {
		
		String name = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, this.trimClassName(obj));
		String result = "";
		Pattern p = Pattern.compile(".*?\\/" + name + "\\/\\{.*?\\}");
		Matcher m = null;
		for (String childPath : this.childEndPoints) {
			
			m = p.matcher(childPath);
			if (m.find()) {
				result = m.group();
				break;
			}
			
		}
		
		return result;
		
	}

	/**
	 * Format path string.
	 *
	 * @param item the item
	 * @return the string
	 */
	private String formatPathString(String item) {
		
		String value = CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, item);
		
		return value;
	}
	
	/**
	 * Call get method.
	 *
	 * @param obj the obj
	 * @param variableName the variable name
	 * @return the object
	 */
	private Object callGetMethod(Introspector obj, String variableName) {
		Object key = null;
		
		key = obj.getValue(variableName);
	
		return key;
	}
	
	/**
	 * Make singular.
	 *
	 * @param word the word
	 * @return the string
	 */
	private String makeSingular(String word) {
		
		String result = word;
		result = result.replaceAll("ies$", "y");
		result = result.replaceAll("(?:s|([hox])es)$", "$1");
		
		return result;
	}
	
	/**
	 * Trim class name.
	 *
	 * @param obj the obj
	 * @return the string
	 */
	public String trimClassName (Object obj) {
		String returnValue = "";
		String name = obj.getClass().getName();
		returnValue = name.substring(name.lastIndexOf('.') + 1, name.length());
	
		if (returnValue.equals("CvlanTagEntry")) {
			returnValue = "CvlanTag";
		}
		return returnValue;
	}

	
	
}
