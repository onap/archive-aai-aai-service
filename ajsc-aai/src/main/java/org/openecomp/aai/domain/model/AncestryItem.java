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

package org.openecomp.aai.domain.model;

import com.thinkaurelius.titan.core.TitanVertex;

public class AncestryItem {
		
		private String fullResourceName;
		
		private AAIResource aaiResource;

		private TitanVertex vertex;
		
		private Object obj;
		
		/**
		 * Gets the full resource name.
		 *
		 * @return the full resource name
		 */
		public String getFullResourceName() {
			return fullResourceName;
		}

		/**
		 * Sets the full resource name.
		 *
		 * @param fullResourceName the new full resource name
		 */
		public void setFullResourceName(String fullResourceName) {
			this.fullResourceName = fullResourceName;
		}

		/**
		 * Gets the aai resource.
		 *
		 * @return the aai resource
		 */
		public AAIResource getAaiResource() {
			return aaiResource;
		}

		/**
		 * Sets the aai resource.
		 *
		 * @param aaiResource the new aai resource
		 */
		public void setAaiResource(AAIResource aaiResource) {
			this.aaiResource = aaiResource;
		}

		/**
		 * Gets the vertex.
		 *
		 * @return the vertex
		 */
		public TitanVertex getVertex() {
			return vertex;
		}

		/**
		 * Sets the vertex.
		 *
		 * @param vertex the new vertex
		 */
		public void setVertex(TitanVertex vertex) {
			this.vertex = vertex;
		}

		/**
		 * Gets the obj.
		 *
		 * @return the obj
		 */
		public Object getObj() {
			return obj;
		}

		/**
		 * Sets the obj.
		 *
		 * @param obj the new obj
		 */
		public void setObj(Object obj) {
			this.obj = obj;
		}

}
