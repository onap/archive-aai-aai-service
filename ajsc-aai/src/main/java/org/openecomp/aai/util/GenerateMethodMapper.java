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

package org.openecomp.aai.util;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openecomp.aai.audit.ListEndpoints;
import org.openecomp.aai.introspection.Version;

public class GenerateMethodMapper {

	private final static String filePath = "bundleconfig-local/etc/appprops/methodMapper.properties";

	/**
	 * The main method.
	 *
	 * @param args the arguments
	 * @throws Exception the exception
	 */
	public static void main(String[] args) throws Exception {

		ListEndpoints le = null;
		JSONObject jo = new JSONObject();
		JSONArray ja = new JSONArray();

		for (Version version : Version.values()) {

			le = new ListEndpoints(version);
			Map<String, String> ln = le.getLogicalNames();
			List<String> keys = new ArrayList<String>(ln.keySet());
			Collections.sort(keys);
			for (String key : keys) {
				addEndpointToJsonArray(key, ln.get(key), ja, version.toString());
			}

		}

		addUniqueEndpoints(ja);

		jo.put("ActiveAndAvailableInventory-CloudNetwork", ja);
		try (FileWriter file = new FileWriter(filePath)) {
			file.write(jo.toString(4));
		}

		System.exit(0);

	}

	/**
	 * Adds the unique endpoints.
	 *
	 * @param ja the ja
	 * @throws JSONException the JSON exception
	 */
	private static void addUniqueEndpoints(JSONArray ja) throws JSONException {
		JSONObject joItem = new JSONObject();
		joItem.put("url", "/aai/{version}/service-design-and-creation/models*");
		joItem.put("method", "get");
		joItem.put("logicalName", "GetModel");
		ja.put(joItem);
		joItem = new JSONObject();
		joItem.put("url", "/aai/{version}/service-design-and-creation/models*");
		joItem.put("method", "put");
		joItem.put("logicalName", "PutModel");
		ja.put(joItem);
		joItem = new JSONObject();
		joItem.put("url", "/aai/{version}/service-design-and-creation/models*");
		joItem.put("method", "delete");
		joItem.put("logicalName", "DeleteModel");
		ja.put(joItem);
		joItem = new JSONObject();
		joItem.put("url", "/aai/{version}/service-design-and-creation/named-queries/*");
		joItem.put("method", "get");
		joItem.put("logicalName", "GetNamedQuery");
		ja.put(joItem);
	}

	/**
	 * Adds the endpoint to json array.
	 *
	 * @param url the url
	 * @param name the name
	 * @param ja the ja
	 * @param apiVersion the api version
	 * @throws JSONException the JSON exception
	 */
	private static void addEndpointToJsonArray(String url, String name, JSONArray ja, String apiVersion)
			throws JSONException {

		JSONObject joGet = new JSONObject();
		JSONObject joPut = new JSONObject();
		JSONObject joDel = new JSONObject();

		if (!url.endsWith("relationship")) {
			joGet.put("url", url);
			joGet.put("method", "get");
			joGet.put("logicalName", apiVersion + "Get" + name);
			ja.put(joGet);
		}

		if (url.endsWith("}") || url.endsWith("relationship")) {
			joPut.put("url", url);
			joPut.put("method", "put");
			joPut.put("logicalName", apiVersion + "Put" + name);
			ja.put(joPut);

			joDel.put("url", url);
			joDel.put("method", "delete");
			joDel.put("logicalName", apiVersion + "Delete" + name);
			ja.put(joDel);

		}

	}

}
