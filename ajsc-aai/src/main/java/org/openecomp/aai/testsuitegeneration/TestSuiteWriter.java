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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.core.Response.Status;
import javax.xml.bind.JAXBException;

import org.json.JSONException;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.rest.ueb.NotificationEvent;
import org.openecomp.aai.rest.ueb.UEBNotification;
import org.openecomp.aai.serialization.db.EdgeRule;
import org.openecomp.aai.serialization.db.MultiplicityRule;
import org.openecomp.aai.testing.InjectResourceVersion;
import org.openecomp.aai.testing.LocateEndPoints;
import org.openecomp.aai.testing.UpdateObject;
import org.openecomp.aai.testsuitegeneration.TestSuite.TestSuiteBuilder;
import org.springframework.web.util.UriUtils;

import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;

import edu.emory.mathcs.backport.java.util.Arrays;

public class TestSuiteWriter extends AbstractWriter {

	protected final String END_POINT_SUITE_CONTENT = "end_point_suite_content_txt.ftl";
	protected final String CHILD_TEST_CONTENT = "child_test_content_txt.ftl";
	
	protected final String[] TOP_ENTITY_EXCEPTIONS = new String[] { "tenant", "volume-group", "dvs-switch", "image", "flavor", "oam-network", "availability-zone"};
	protected final Set<String> EXCEPTION_SET = new HashSet<String>(Arrays.asList(TOP_ENTITY_EXCEPTIONS));
	
	/**
	 * Instantiates a new test suite writer.
	 *
	 * @param file the file
	 * @param builder the builder
	 */
	public TestSuiteWriter(File file, TestSuiteBuilder builder) {
		
		// Just call the super constructor
		super(file, builder);
		
	}

	/**
	 * Creates a suite for the parent object creation/teardown.
	 *
	 * @param path the path
	 * @param parentObject the parent object
	 * @param lep the lep
	 * @param genericPath the generic path
	 * @return the string
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws SecurityException the security exception
	 */
	protected String createParentSuite(String path, Introspector parentObject, LocateEndPoints lep, String genericPath) throws IOException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		if(genericPath == null) genericPath = path;
		
		// Extract the object name to create the suite path
		String name = CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, parentObject.getDbName());
		String suitePath = outputPath + "/" + name + "Suite";
		
		// Create the suite directory
		File suiteDir = new File(suitePath);
		suiteDir.mkdir();
		
		// Randomly generate field values to update the parent object
		InjectResourceVersion inject = new InjectResourceVersion();
		Introspector injectedObject = inject.addResourceVersionDeep(parentObject);
		Introspector updatedParentObject = (new UpdateObject()).randomlyUpdateFieldValues(injectedObject);

		String populatedPath = (String)lep.populatePath(parentObject, path).get(0);
		//check the child map
		String lowerHyphenName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, name);
		Pattern p = Pattern.compile("^[\\w\\-]+?,,");
		Matcher m = null;
		boolean hasChildren = this.hasChildren(parentObject);

		// Create the variable mapping for the suite
		Map<String, Object> suiteMap = new HashMap<>();
		
		if (hasChildren && DeleteScope.get(lowerHyphenName).contains("THIS_NODE_ONLY")) {

			try {
				tearDownPath = generateRecursiveCleanUp(populatedPath, suitePath, updatedParentObject, lep, "", "${topLevelFeatureURL}");
				//tearDownPathChildren = generateRecursiveCleanUp(path, additionalPath, parentObject, lep, "OfParent");

			} catch (IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException
					| SecurityException e) {
				e.printStackTrace();
			} 
			parentCleanUpPage = this.tearDownPath;

		} else {
			parentCleanUpPage = ".AAI.Tests.GenericTests.SimpleParentCleanUp";
		}
		String encodedPath = this.encodeUsernamePlaceholders(populatedPath);
		encodedPath = encodedPath.replaceFirst("/aai/v\\d+/", "");
		UEBNotification notification = new UEBNotification(loader);
		Introspector uebObj = null;
		Introspector uebUpdatedObj = null;
		String uebLink = "";
		try {
			notification.createNotificationEvent("test-generation", Status.CREATED, new URI(encodedPath), parentObject, new HashMap<String, Introspector>());
			notification.createNotificationEvent("test-generation", Status.OK, new URI(encodedPath), updatedParentObject, new HashMap<String, Introspector>());
			List<NotificationEvent> events = notification.getEvents();
			uebObj = events.get(0).getObj();
			uebUpdatedObj = events.get(1).getObj();
			AddNamedPropWildcard addWildCard = new AddNamedPropWildcard(loader);
			addWildCard.process(uebObj);
			addWildCard.process(uebUpdatedObj);
			uebLink = "-!${address}!-"+new URI((String)events.get(0).getEventHeader().getValue("entity-link")).getPath();
			uebLink = this.decodeUserNamePlaceholders(uebLink);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (AAIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		String entity = this.getHyphenatedClassName(name);
		String topEntity = this.computeTopEntity(entity);
		suiteMap.put("topLevelBody", replaceUsernamePlaceholders(parentObject.marshal(true)));
		suiteMap.put("topLevelUpdatedBody", replaceUsernamePlaceholders(updatedParentObject.marshal(true)));
		suiteMap.put("topUebBody", replaceUsernamePlaceholders(uebObj.marshal(true)));
		suiteMap.put("topUebUpdatedBody", replaceUsernamePlaceholders(uebUpdatedObj.marshal(true)));
		suiteMap.put("topUebLink", replaceUsernamePlaceholders(uebLink));
		suiteMap.put("topLevelFeatureURL", replaceUsernamePlaceholders(populatedPath));
		suiteMap.put("type", "suite");
		suiteMap.put("entity", entity);
		suiteMap.put("topEntity", topEntity);
		suiteMap.put("topLevelCleanUpPage", parentCleanUpPage);
		
		Map<String, String> symLinks = new HashMap<>();
		symLinks.put("SuiteSetUp", ".AAI.Tests.GenericTests.SuiteSetUpTemplate");
		symLinks.put("SuiteTearDown", ".AAI.Tests.GenericTests.SuiteTearDownTemplate");
		suiteMap.put("symLinks", symLinks);
		// Process the freemarker files
		processDataModel(END_POINT_SUITE_CONTENT, "content.txt", suiteDir, suiteMap);
		processDataModel(PROPERTIES_XML, "properties.xml", suiteDir, suiteMap);
		
		// Create a placeholder page for the parent test
		String parentTest = suitePath + "/" + name + "Test";
		suiteDir = new File(parentTest);
		suiteDir.mkdir();
		suiteMap = new HashMap<>();
		suiteMap.put("type", "test");
		processDataModel("empty_context_txt.ftl", "content.txt", suiteDir, suiteMap);
		processDataModel(PROPERTIES_XML, "properties.xml", suiteDir, suiteMap);
		
		return suitePath;
	}
	
	/**
	 * Format test name.
	 *
	 * @param genericPath the generic path
	 * @return the string
	 */
	protected String formatTestName(String genericPath) {
		String dropSuiteName = genericPath.substring(genericPath.indexOf("}")+1);
		String removeVariables = dropSuiteName.replaceAll("\\/\\{.*?\\}", "");
		String[] split = removeVariables.split("/"); 
		List<String> result = new ArrayList<>();
		for (int i = 0; i < split.length; i++) {
			
			if (!this.isPlural(split[i])) {
				result.add(CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, split[i]));
			}
		}
		String addHyphens = Joiner.on("-").join(result).replaceFirst("-", "");
		
		return addHyphens;
	}

	/**
	 * Adds the test case.
	 *
	 * @param suitePath the suite path
	 * @param path the path
	 * @param obj the obj
	 * @param genericPath the generic path
	 * @param lep the lep
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void addTestCase(String suitePath, String path, Introspector obj, String genericPath, LocateEndPoints lep) throws IOException {
		if (genericPath == null){
			genericPath = suitePath;
		}
		supplimentalMap.put("tearDownPath", this.tearDownPathChildren);

		this.addTestCase(suitePath, path, obj, supplimentalMap, CHILD_TEST_CONTENT, genericPath, lep);
	}
	
	/**
	 * Adds the test case.
	 *
	 * @param parentPath the parent path
	 * @param path the path
	 * @param obj the obj
	 * @param supplimentalMap the supplimental map
	 * @param templateName the template name
	 * @param genericPath the generic path
	 * @param lep the lep
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	protected void addTestCase(String parentPath, String path, Introspector obj, Map<String, Object> supplimentalMap,
			String templateName, String genericPath, LocateEndPoints lep) throws IOException {
		
		if (genericPath == null){
			genericPath = parentPath;
		}
		
		InjectResourceVersion inject = new InjectResourceVersion();
		Introspector injectedResource = inject.addResourceVersionDeep(obj);
		Introspector updatedObj = (new UpdateObject()).randomlyUpdateFieldValues(injectedResource);

		String name =  CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, obj.getDbName());
		String entityName = this.getHyphenatedClassName(name);
		String nameWithDepth = this.formatTestName(genericPath);

		if (!nameWithDepth.equals("")) {
			name = nameWithDepth;
		}
		String testPath = parentPath + "/" + name + "Test";

		File dir = new File(testPath);

		dir.mkdir();
		
		Map<String, Object> testMap = new HashMap<>();

		boolean hasChildren = this.hasChildren(obj);
		
		if (hasChildren && DeleteScope.get(obj.getDbName()).contains("THIS_NODE_ONLY")) {

			try {
				tearDownPath = generateRecursiveCleanUp(path, parentPath, updatedObj, lep, "", "${featureURL}");
				//tearDownPathChildren = generateRecursiveCleanUp(path, additionalPath, parentObject, lep, "OfParent");

			} catch (IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException
					| SecurityException e) {
				e.printStackTrace();
			} 
			this.deletePage = this.tearDownPath;		
		} else {
			this.deletePage = ".AAI.Tests.GenericTests.DeleteTop";
		}
		String testPagePath = ".AAI.Tests.GenericTests.GenericTest";
		try {
			Introspector parent = null;
			if (!genericPath.equals(obj.getFullGenericURI())) {
				String parentObjectPath = genericPath.replace(obj.getFullGenericURI(), "");
				parent = lep.createChildForPath(parentObjectPath, 1, 1);
				EdgeRule rule = org.openecomp.aai.serialization.db.EdgeRules.getInstance().getEdgeRule(parent.getDbName(), obj.getDbName());
				
				if (rule.getMultiplicityRule().equals(MultiplicityRule.ONE2ONE)) {
					testPagePath = ".AAI.Tests.GenericTests.PlaceholderTest";
				}
			}
		} catch (IllegalArgumentException | AAIException | IllegalAccessException | InvocationTargetException | InstantiationException | JSONException | JAXBException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		String featureUrl = replaceUsernamePlaceholders(path);
		String encodedPath = this.encodeUsernamePlaceholders(path);
		encodedPath = encodedPath.replaceFirst("/aai/v\\d+/", "");

		UEBNotification notification = new UEBNotification(loader);
		Introspector uebObj = null;
		Introspector uebUpdatedObj = null;
		String uebLink = "";
		try {
			notification.createNotificationEvent("test-generation", Status.CREATED, new URI(encodedPath), obj, new HashMap<String, Introspector>());
			notification.createNotificationEvent("test-generation", Status.OK, new URI(encodedPath), updatedObj, new HashMap<String, Introspector>());
			List<NotificationEvent> events = notification.getEvents();
			uebObj = events.get(0).getObj();
			uebUpdatedObj = events.get(1).getObj();
			AddNamedPropWildcard addWildCard = new AddNamedPropWildcard(loader);
			addWildCard.process(uebObj);
			addWildCard.process(uebUpdatedObj);
			uebLink = "-!${address}!-"+new URI((String)events.get(0).getEventHeader().getValue("entity-link")).getPath();
			uebLink = this.decodeUserNamePlaceholders(uebLink);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (AAIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
 
		
		
		testMap.put("body", replaceUsernamePlaceholders(obj.marshal(true)));
		testMap.put("updatedBody", replaceUsernamePlaceholders(updatedObj.marshal(true)));
		testMap.put("uebBody", replaceUsernamePlaceholders(uebObj.marshal(true)));
		testMap.put("uebUpdatedBody", replaceUsernamePlaceholders(uebUpdatedObj.marshal(true)));
		testMap.put("uebLink", replaceUsernamePlaceholders(uebLink));
		testMap.put("featureURL", featureUrl);
		testMap.put("type", "test");
		testMap.put("testPath", testPagePath);
		testMap.put("deletePage", this.deletePage);
		testMap.put("putPage", ".AAI.Tests.GenericTests.StandardPut");
		testMap.put("entity", entityName);
		testMap.putAll(supplimentalMap);

		this.processDataModel(templateName, "content.txt", dir, testMap);
		this.processDataModel(PROPERTIES_XML, "properties.xml", dir, testMap);
	}
	
	/**
	 * Compute top entity.
	 *
	 * @param entity the entity
	 * @return the string
	 */
	private String computeTopEntity(String entity) {
		String result = entity;
		if (!this.apiVersion.equals("v7")) {
			if (EXCEPTION_SET.contains(entity)) {
				result = "cloud-region";
			}
		}
		
		return result;
	}
}
