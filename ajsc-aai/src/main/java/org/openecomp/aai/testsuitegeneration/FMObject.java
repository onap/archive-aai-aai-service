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
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.openecomp.aai.rest.CustomJacksonJaxBJsonProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

public class FMObject {
	private Configuration cfg;
	private Map<String, Object> root;
	private String jsonString;
	private String key;
	private String featureURL;
	private String className;
	private String dirPath;
	private final String templatePath = "src/main/java/org/openecomp/aai/freemarker/";

	/**
	 * Instantiates a new FM object.
	 *
	 * @param obj the obj
	 * @param dirPath the dir path
	 * @param className the class name
	 * @param key the key
	 * @param featureURL the feature URL
	 */
	public FMObject (Object obj, String dirPath, String className, String key, String featureURL){
		this.cfg = new Configuration();
		this.root = new HashMap<String, Object>();
		this.key = key;
		this.featureURL = featureURL;
		this.className = className.substring(className.lastIndexOf('.')+1, className.length());
		this.dirPath = dirPath;
		try {
			this.jsonString = jsonMarshal(obj);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		this.root.put("fields", jsonString);
		this.root.put("key", this.key);
		this.root.put("featureURL", this.featureURL);
		
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
	 */
	public void processDataModel(String tempFileName, String outFileName, File dir){
		Template template = this.getTemplate(tempFileName);
		try {
			Writer file = new FileWriter (new File(dir.getPath()+"/"+outFileName));
			template.process(this.root, file);
			file.flush();
		} catch (IOException | TemplateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Creates the output files.
	 */
	public void createOutputFiles(){	
			File dir = new File(this.dirPath+"/"+this.className);
			dir.mkdir();
			processDataModel("content_txt.ftl", "content.txt", dir);
			processDataModel("test_properties_xml.ftl", "properties.xml", dir);
	}
	
	/**
	 * Json marshal.
	 *
	 * @param o the o
	 * @return the string
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private String jsonMarshal(Object o) throws IOException {
        CustomJacksonJaxBJsonProvider provider = new CustomJacksonJaxBJsonProvider();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        
        provider.writeTo(o, o.getClass(), null, null, MediaType.APPLICATION_JSON_TYPE, null, os);
        
        String output = new String(os.toByteArray(), "UTF-8");
        
        ObjectMapper mapper = provider.getMapper();
        
        Object json = mapper.readValue(output, o.getClass());
        
        output = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        return output;
	}
}
