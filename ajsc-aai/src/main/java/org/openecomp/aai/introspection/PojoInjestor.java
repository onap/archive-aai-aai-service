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

package org.openecomp.aai.introspection;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.openecomp.aai.db.AAIProperties;

public class PojoInjestor {
	
	private String POJO_CLASSPATH = "org.openecomp.aai.domain.yang";

	public PojoInjestor() {
	}
	
	public JAXBContext getContextForVersion(Version v) {
		JAXBContext context = null;
		try {
			if (!v.equals(AAIProperties.LATEST)) {
				POJO_CLASSPATH += "." + v; 
			}
			context = JAXBContext.newInstance(POJO_CLASSPATH);
		} catch (JAXBException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return context;
	}
	public Version getVersion (String classname) {
		Pattern p = Pattern.compile("\\.(v\\d+)\\.");
		Matcher m = p.matcher(classname);
		String version = "";
		if (m.find()) {
			version = m.group(1);
		}
		
		return Version.valueOf(version);
	}
	
}
