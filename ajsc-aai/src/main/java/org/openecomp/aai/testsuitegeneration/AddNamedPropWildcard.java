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

import java.util.List;

import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.introspection.IntrospectorWalker;
import org.openecomp.aai.introspection.Loader;
import org.openecomp.aai.introspection.Version;
import org.openecomp.aai.introspection.Wanderer;
import org.openecomp.aai.logging.LogLineBuilder;

public class AddNamedPropWildcard implements Wanderer {

	
	private final LogLineBuilder llBuilder;
	private Loader loader;
	public AddNamedPropWildcard(Loader loader) {
		this.llBuilder = new LogLineBuilder();
		this.loader = loader;

	}
	 
	public void process(Introspector obj) {
		
		IntrospectorWalker walker = new IntrospectorWalker(this, llBuilder);
		walker.walk(obj);
		
	}
	@Override
	public void processPrimitive(String propName, Introspector obj) {
		//NO-OP		
	}

	@Override
	public void processPrimitiveList(String propName, Introspector obj) {
		//NO-OP		
	}

	@Override
	public void processComplexObj(Introspector obj) {
		String nameProp = obj.getMetadata("nameProps");
		if (nameProp != null && !nameProp.equals("")) {
			String[] nameProps = nameProp.split(",");
			for (String prop : nameProps) {
				if (obj.getClass(prop).equals(String.class)) {
					
					if (obj.getValue(prop) == null) {
						if (!isDefaultCloudRegion(obj)) {
							obj.setValue(prop, "*");
						}
					}
				}
			}
		}
	}

	private boolean isDefaultCloudRegion(Introspector obj) {
		return false;
	}
	@Override
	public void modifyComplexList(List<Object> list, Introspector parent, Introspector child) {
		//NO-OP
	}

	@Override
	public boolean createComplexObjIfNull() {
		return false;
	}

	@Override
	public int createComplexListSize(Introspector parent, Introspector child) {
		return 0;
	}

	
}
