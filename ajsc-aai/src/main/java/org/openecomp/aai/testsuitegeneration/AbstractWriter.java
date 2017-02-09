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

package org.openecomp.aai.testsuitegeneration;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.introspection.IntrospectorFactory;
import org.openecomp.aai.introspection.Loader;
import org.openecomp.aai.introspection.LoaderFactory;
import org.openecomp.aai.introspection.ModelType;
import org.openecomp.aai.introspection.Version;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.rest.CustomJacksonJaxBJsonProvider;
import org.openecomp.aai.testing.LocateEndPoints;
import org.openecomp.aai.testsuitegeneration.TestSuite.TestSuiteBuilder;
import org.springframework.web.util.UriUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CaseFormat;
import com.google.common.collect.Multimap;

import freemarker.template.Configuration;
import freemarker.template.Template;

public abstract class AbstractWriter {

	protected final String PROPERTIES_XML = "properties_xml.ftl";
	
	protected final String TEMPLATE_PATH = "src/main/java/org/openecomp/aai/testsuitegeneration/";
	
	public static final String USERNAME_PLACEHOLDER = "#!#username.placeholder#!#";
	
	public final Integer START_PORT = 42671;
	protected Map<String, Object> supplimentalMap = new HashMap<>();
	protected Configuration configuration = new Configuration();
	protected String outputPath;
	protected Version apiVersion;
	protected Version currentApiVersion;
	protected String tearDownPath;
	protected String tearDownPathChildren;
	protected String deletePage;
	protected String parentCleanUpPage;
	protected String classPackagePath;
	protected String cleanUpPage;
	protected Multimap<String, String> DeleteScope;
	protected Multimap<String, String> EdgeRules;
	protected final LogLineBuilder llBuilder = new LogLineBuilder();
	protected final Loader loader;

	
	/**
	 * Instantiates a new abstract writer.
	 *
	 * @param file the file
	 * @param builder the builder
	 */
	// Abstract constructor
	public AbstractWriter(File file, TestSuiteBuilder builder) {
		this.outputPath = file.getAbsolutePath();
		this.apiVersion = builder.getApiVersion();
		this.currentApiVersion = builder.getCurrentApiVersion();
		this.DeleteScope = builder.getDeleteScope();
		this.EdgeRules = builder.getEdgeMap();
		this.classPackagePath = builder.getPojoPackage();
		this.deletePage = "";
		this.tearDownPath = "";
		this.parentCleanUpPage = "";
		this.loader = LoaderFactory.createLoaderForVersion(ModelType.MOXY, apiVersion, llBuilder);
		createVersionSuite();
	}
	
	
	/**
	 * Creates a suite for the writer's specified API version.
	 */
	private void createVersionSuite() {
		Map<String, Object> suiteMap = new HashMap<>();
		
		// Set the PUT expected result
		String createStatus = "200";
		Pattern p = Pattern.compile("v(\\d+)");
		Matcher m = p.matcher(apiVersion.toString());
		Integer versionNumber = 0;
		if (m.find()) {
			versionNumber = Integer.parseInt(m.group(1));
		}
		
		createStatus = "201";
		// Add the placeholder values to the map
		suiteMap.put("version", apiVersion);
		suiteMap.put("currentVersion", currentApiVersion);
		suiteMap.put("createStatus", createStatus);
		suiteMap.put("type", "suite");
		suiteMap.put("slimPort", START_PORT + (versionNumber * 20));
		
		// Create the directory
		File dir = new File(this.outputPath);
		dir.mkdir();
		
		// Process the freemarker templates
		this.processDataModel("version_suite_content_txt.ftl", "content.txt", dir, suiteMap);
		this.processDataModel("properties_xml.ftl", "properties.xml", dir, suiteMap);
	}
		
	
	/**
	 * Generates the necessary pages to recursively clean up children objects.
	 *
	 * @param path the path
	 * @param fitnessePath the fitnesse path
	 * @param topLevel the top level
	 * @param lep the lep
	 * @param additionalName the additional name
	 * @param urlVariableName the url variable name
	 * @return the string
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws SecurityException the security exception
	 */
	protected String generateRecursiveCleanUp(String path, String fitnessePath, Introspector topLevel, LocateEndPoints lep, String additionalName, String urlVariableName) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		String childPath = "";
		String fitnessePageName =  CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, topLevel.getDbName());

		String folderName = fitnessePageName + "AdditionalTests";
		String additionalPath = fitnessePath + "/" + folderName;
		File dir = new File(additionalPath);
		dir.mkdir();
		Map<String, Object> additionalMap = new HashMap<>();
		additionalMap.put("type", "static");
		additionalMap.put("skip", "true");
		processDataModel("static_page_content_txt.ftl", "content.txt", dir, additionalMap);
		processDataModel("properties_xml.ftl", "properties.xml", dir, additionalMap);
		List<String> pages = findCascadeDeleteChildren(topLevel, additionalPath, path, lep, additionalName);
		
		Map<String, Object> map = new HashMap<>();		
		
		String testPageName = "ChildrenTearDown" + additionalName;
		String tearDownPath = additionalPath + "/" + testPageName;
		dir = new File(tearDownPath);
		dir.mkdir();
		
		map.put("type", "test");
		map.put("pages", pages);
		map.put("parentCleanUpPage", ".AAI.Tests.GenericTests.FauxRecursiveDelete");
		map.put("errorCheckPage", ".AAI.Tests.GenericTests.DeleteErrorCheck");
		String decodedUrl = urlVariableName;
		try {
			decodedUrl = UriUtils.decode(decodedUrl, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
		map.put("urlVariableName", this.replaceUsernamePlaceholders(decodedUrl));
		this.processDataModel("recursive_teardown_content_txt.ftl", "content.txt", dir, map);
		this.processDataModel("properties_xml.ftl", "properties.xml", dir, map);
		
		
		
		return folderName + "." + testPageName;
	}
	
	
	/**
	 * Searches for children to be deleted via cascade delete.
	 *
	 * @param obj the obj
	 * @param fitNessePath the fit nesse path
	 * @param restPath the rest path
	 * @param lep the lep
	 * @param additionalName the additional name
	 * @return the list
	 */
	protected List<String> findCascadeDeleteChildren(Introspector obj, String fitNessePath, String restPath, LocateEndPoints lep, String additionalName) {
		List<String> list = new ArrayList<>();
		try {
			findHelper(obj, list, fitNessePath, restPath, lep, additionalName);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | InstantiationException | UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return list;
	}
	
	
	/**
	 * Helper to the find method.
	 *
	 * @param obj the obj
	 * @param list the list
	 * @param fitNessePath the fit nesse path
	 * @param restPath the rest path
	 * @param lep the lep
	 * @param additionalName the additional name
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws InstantiationException the instantiation exception
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 */
	protected void findHelper(Introspector obj, List<String> list, String fitNessePath, String restPath, LocateEndPoints lep, String additionalName) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException, UnsupportedEncodingException {
		
		if (obj == null) {
			return;
		}
		List<String> properties = obj.getProperties();
		properties.remove("relationship-list");
		for (String prop : properties) {
			
			if (obj.isComplexType(prop)) {
				Introspector child = IntrospectorFactory.newInstance(obj.getModelType(), obj.getValue(prop), llBuilder);
				
				findHelper(IntrospectorFactory.newInstance(obj.getModelType(), obj.getValue(prop), llBuilder), list, fitNessePath, restPath, lep, additionalName);
			} else if (obj.isListType(prop)) {
				if (DeleteScope.get(
						CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, this.trimClassName(obj.getGenericType(prop))))
						.contains("CASCADE_TO_CHILDREN")) {
					List<?> children = (List<?>)obj.getValue(prop);
					Introspector temp = null;
					List<String> urls = new ArrayList<>();
					for (Object child : children) {
						temp = IntrospectorFactory.newInstance(obj.getModelType(), child, llBuilder);
						String decodedUrl = restPath + temp.getURI();
						try {
							decodedUrl = UriUtils.decode(decodedUrl, "UTF-8");
						} catch (UnsupportedEncodingException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					
						urls.add(replaceUsernamePlaceholders(decodedUrl));
					}
					
					Map<String, Object> map = new HashMap<>();

					map.put("deleteChildren", ".AAI.Tests.GenericTests.CleanUp");
					map.put("urls", urls);
					map.put("type", "test");
					String childrenTestPage = "RemoveChildren" + additionalName;
					String removeChildrenPath = fitNessePath + "/" + childrenTestPage;
					File dir = new File(removeChildrenPath);
					dir.mkdir();
					this.processDataModel("remove_children_content_txt.ftl", "content.txt", dir, map);
					this.processDataModel("properties_xml.ftl", "properties.xml", dir, map);
					list.add(childrenTestPage);
				} else {
					//still wrong 
					Introspector temp = obj.newIntrospectorInstanceOfNestedProperty(prop);
					if (this.hasChildren(temp) && DeleteScope.get(
						CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, this.trimClassName(obj.getGenericType(prop))))
						.contains("THIS_NODE_ONLY")) {
						List<?> children = (List<?>)obj.getValue(prop);
						String result = "";
						for (int i = 0; i < children.size(); i++) {
							Introspector child = IntrospectorFactory.newInstance(obj.getModelType(), children.get(i), llBuilder);
						
							try {
								result = generateRecursiveCleanUp(restPath + child.getURI(), fitNessePath, child, lep, additionalName + new Integer(i).toString(), "!-" + restPath + child.getURI() + "-!");
							} catch (NoSuchMethodException | SecurityException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							list.add(result);

						}

					}
				}
				
			}
		}
		
	}
	
	/**
	 * Checks for children.
	 *
	 * @param obj the obj
	 * @return true, if successful
	 */
	protected boolean hasChildren(Introspector obj) {
		
		for (String prop : obj.getProperties()) {
			if (!prop.equals("relationship-list")) {

				if (obj.isComplexType(prop) || (obj.isListType(prop) && obj.isComplexGenericType(prop))) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	
	/**
	 * Processes the template file through freemarker.
	 *
	 * @param templateFilename The filename of the template to process.
	 * @param outputFilename THe filename of the output file to generate.
	 * @param outputDirectory The directory of the output file.
	 * @param variableMap The variable map where key is the placeholder variable name.
	 */
	protected void processDataModel(String templateFilename, String outputFilename, File outputDirectory, Map<String, Object> variableMap) {

		try {
			Template template = configuration.getTemplate(TEMPLATE_PATH + templateFilename);
			Writer file = new FileWriter(new File(outputDirectory.getPath() + "/" + outputFilename));
			template.process(variableMap, file);
			file.flush();
		} catch(Exception e) {
			System.out.println("Failed to process file {" + outputFilename + "}");
			e.printStackTrace();
		}
	}
	
	
	/**
	 * Marshals an object into a JSON string.
	 *
	 * @param obj The object to marshal
	 * @return A string representation of the object in JSON format.
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	protected String jsonMarshal(Object obj) throws IOException {
		CustomJacksonJaxBJsonProvider provider = new CustomJacksonJaxBJsonProvider();
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		provider.writeTo(obj, obj.getClass(), null, null, null, null, os);
		String output = new String(os.toByteArray(), "UTF-8");
		ObjectMapper mapper = provider.getMapper();
		Object json = mapper.readValue(output, obj.getClass());
		output = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
		return output;
	}
	
	
	/**
	 * Retrieves the root to FitNesse from the full path.
	 *
	 * @param fullPath The full output path.
	 * @return The root to FitNesseRoot
	 */
	protected String getFitNesseRoot(String fullPath) {
		String result = fullPath;
		Pattern p = Pattern.compile("Root(?:/|\\\\)(.*)");
		Matcher m = p.matcher(fullPath);
		if(m.find()) {
			result = m.group(1);
		}
		return result;
	}
	
	
	/**
	 * Converts an UpperCamel class name to a lower-hyphen class name.
	 * @param className The class name in UpperCamel format.
	 * @return The class name is lower-hyphen format.
	 */
	protected String getHyphenatedClassName(String className) {
		String output = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, className);
		
		// Special case for cvlan-tag
		if(output.equals("cvlan-tag-entry")) {
			output = "cvlan-tag";
		}
		
		return output;
	}
	
	/**
	 * Checks if is plural.
	 *
	 * @param word the word
	 * @return true, if is plural
	 */
	protected boolean isPlural(String word) {
		return word.matches(".*?(es|(?<!s)s)$");
	}
	
	/**
	 * Trim class name.
	 *
	 * @param name the name
	 * @return the string
	 */
	protected String trimClassName(String name) {
		
		return name.substring(name.lastIndexOf('.') + 1, name.length());
	}
	
	/**
	 * Gets the key from path.
	 *
	 * @param path the path
	 * @return the key from path
	 */
	protected String getKeyFromPath(String path) {
		Pattern p = Pattern.compile("(\\{(.+?)\\}\\/?)+$");
		Matcher m = p.matcher(path);
		String key = "";
		if (m.matches()) {
			key = m.group();
		}
		return key;
	}
	protected String encodeUsernamePlaceholders(String body) {
		String encode = "";
		try {
			encode = UriUtils.encodePath(USERNAME_PLACEHOLDER, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return body.replace(USERNAME_PLACEHOLDER, encode);
	}
	protected String decodeUserNamePlaceholders(String body) {
		String decode = "";
		try {
			decode = UriUtils.decode(body, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return decode;
	}
	/**
	 * Replace username placeholders.
	 *
	 * @param body the body
	 * @return the string
	 */
	protected String replaceUsernamePlaceholders(String body) {
		return body.replace(USERNAME_PLACEHOLDER, "--!${username}!-");
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
}
