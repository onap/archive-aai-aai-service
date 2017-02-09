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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.eclipse.persistence.internal.oxm.mappings.Descriptor;
import org.eclipse.persistence.jaxb.dynamic.DynamicJAXBContext;
import org.json.JSONException;
import org.openecomp.aai.introspection.ModelInjestor;
import org.openecomp.aai.introspection.Version;

public class OXMTest {

	/**
	 * The main method.
	 *
	 * @param args the arguments
	 * @throws JAXBException the JAXB exception
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws InstantiationException the instantiation exception
	 * @throws JSONException the JSON exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static void main(String[] args) throws JAXBException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException, JSONException, IOException {

		
		ModelInjestor injestor = ModelInjestor.getInstance();
		
		DynamicJAXBContext context =injestor.getContextForVersion(Version.v8);
		
		List<Descriptor> test = context.getXMLContext().getDescriptors();
		
		for (Descriptor d : test) {
			System.out.println(context.newDynamicEntity(d.getJavaClass().getSimpleName()).getClass().getName());
		}
	}

}
