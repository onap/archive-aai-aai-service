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
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openecomp.aai.introspection.Version;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

public class FitNesseJUnitWriter {

	private Configuration cfg = new Configuration();
	private String templatePath = "src/main/java/org/openecomp/aai/freemarker/";
	private String outputPath = "";

	
	/**
	 * Instantiates a new fit nesse J unit writer.
	 *
	 * @param outputPath the output path
	 */
	public FitNesseJUnitWriter(String outputPath) {
		
		this.outputPath = outputPath;
	}
	
	/**
	 * Instantiates a new fit nesse J unit writer.
	 */
	@SuppressWarnings("unused")
	private FitNesseJUnitWriter() {
		
	}
	
	/**
	 * Creates the J unit class.
	 *
	 * @param packageName the package name
	 * @param apiVersion the api version
	 * @param fullSuitePath the full suite path
	 */
	public void createJUnitClass(String packageName, Version apiVersion, String fullSuitePath) {
		Map<String, Object> map = new HashMap<>();

		packageName = packageName + "." + apiVersion;
		String suiteName = this.getFitNesseRoot(fullSuitePath);
		String[] parts = suiteName.split("\\.");
		String className = parts[parts.length-1] + "IT";
		map.put("packageName", packageName);
		map.put("className", className);
		map.put("suiteName", suiteName);
		File dir = new File(this.outputPath + "/" + packageName.replaceAll("\\.",  "/") + "/");
		dir.mkdirs();
		this.processDataModel("junit_class_java.ftl", className + ".java", dir, map);
		
	}
	
	/**
	 * Gets the template.
	 *
	 * @param name the name
	 * @return the template
	 */
	private Template getTemplate(String name) {
		Template template = null;
		try {
			template = cfg.getTemplate(templatePath + name);
		} catch (IOException e) {
			System.out.println("Template not found: " + e.getMessage());
		}
		return template;
	}
	
	/**
	 * Process data model.
	 *
	 * @param tempFileName the temp file name
	 * @param outFileName the out file name
	 * @param dir the dir
	 * @param map the map
	 */
	public void processDataModel(String tempFileName, String outFileName, File dir, Map<?,?> map){
		Template template = this.getTemplate(tempFileName);
		try {
			Writer file = new FileWriter (new File(dir.getPath()+"/"+outFileName));
			template.process(map, file);
			file.flush();
		} catch (IOException | TemplateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Gets the fit nesse root.
	 *
	 * @param fullPath the full path
	 * @return the fit nesse root
	 */
	private String getFitNesseRoot(String fullPath) {
		String result = fullPath;
		Pattern p = Pattern.compile("FitNesseRoot(?:/|\\\\)(.*)");
		Matcher m = p.matcher(fullPath);
		
		if (m.find()) {
			result = m.group(1);
		}
		
		return result.replaceAll("/|\\\\", ".");
		
	}
}
