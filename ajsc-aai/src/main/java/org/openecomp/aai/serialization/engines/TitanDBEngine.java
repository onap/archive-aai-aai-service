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

package org.openecomp.aai.serialization.engines;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.thinkaurelius.titan.core.Cardinality;
import com.thinkaurelius.titan.core.TitanProperty;
import com.thinkaurelius.titan.core.TitanVertex;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.openecomp.aai.introspection.Loader;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.serialization.db.TitanGraphSingleton;

public class TitanDBEngine extends TransactionalGraphEngine {

	/**
	 * Instantiates a new titan DB engine.
	 *
	 * @param style the style
	 * @param loader the loader
	 */
	public TitanDBEngine(QueryStyle style, Loader loader) {
		super(style, loader);
		this.singleton = TitanGraphSingleton.getInstance();
		this.graph = singleton.getTxGraph();
	}
	
	/**
	 * Instantiates a new titan DB engine.
	 *
	 * @param style the style
	 * @param loader the loader
	 * @param connect the connect
	 */
	public TitanDBEngine(QueryStyle style, Loader loader, boolean connect) {
		super(style, loader);
		if (connect) {
			this.singleton = TitanGraphSingleton.getInstance();
			this.graph = singleton.getTxGraph();
		}
	}

	/**
	 * {@inheritDoc} 
	 */
	@Override
	public boolean setListProperty(Vertex v, String name, List<?> objs) {

		//clear out list full replace style
		
		Iterator<VertexProperty<Object>> iterator = ((TitanVertex)v).properties(name);
		while (iterator.hasNext()) {
			iterator.next().remove();
		}
		if (objs != null) {
			for (Object obj : objs) {
				v.property(name, obj);
			}
		}
		return true;
	}
	
	/**
	 * {@inheritDoc} 
	 */
	@Override
	public List getListProperty(Vertex v, String name) {

		List result = new ArrayList();
		
		Iterator<VertexProperty<Object>> iterator = ((TitanVertex)v).properties(name);
		
		while (iterator.hasNext()) {
			result.add(iterator.next().value());
		}
		
		if (result.size() == 0) {
			result = null;
		}
		
		return result;

	}
	
}
