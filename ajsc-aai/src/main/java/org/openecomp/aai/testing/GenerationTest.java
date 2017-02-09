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

import javax.xml.bind.JAXBException;

import org.json.JSONException;
import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.introspection.IntrospectorFactory;
import org.openecomp.aai.introspection.Loader;
import org.openecomp.aai.introspection.LoaderFactory;
import org.openecomp.aai.introspection.ModelType;
import org.openecomp.aai.introspection.Version;
import org.springframework.web.util.UriUtils;

public class GenerationTest {

	/**
	 * The main method.
	 *
	 * @param args the arguments
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws InstantiationException the instantiation exception
	 * @throws JSONException the JSON exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws JAXBException the JAXB exception
	 */
	public static void main(String[] args) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException, JSONException, IOException, JAXBException {
		/*
		PopulateObject pop = new PopulateObject();
		UpdateObject update = new UpdateObject();
		InjectResourceVersion inject = new InjectResourceVersion();
		Loader v7Loader = LoaderFactory.createLoaderForVersion(ModelType.MOXY, Version.v8);
		Introspector wrappedObj = v7Loader.introspectorFromName("tenant");
		pop.populateRecursively(wrappedObj, 1, 3, false);
				
		System.out.println(wrappedObj.marshal(true));
		
		System.out.println(wrappedObj.getURI());
		System.out.println("-----------------------");
		wrappedObj = inject.addResourceVersionDeep(wrappedObj);

		Introspector obj2 = update.randomlyUpdateFieldValues(wrappedObj);
		System.out.println(obj2.marshal(true));
		
		*/
		String encode = UriUtils.encodePath("~hello/:()", "UTF-8");
		String decode = UriUtils.decode(encode, "UTF-8");
		System.out.println(encode);
		System.out.println(decode);

	}

}
