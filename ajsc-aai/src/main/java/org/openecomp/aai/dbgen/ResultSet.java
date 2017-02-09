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

package org.openecomp.aai.dbgen;

import java.util.ArrayList;
import java.util.HashMap;

import com.thinkaurelius.titan.core.TitanVertex;

public class ResultSet {
	 TitanVertex vert;
	 String newDataDelFlag;  
	 String doNotOutputFlag;
	 String locationInModelSubGraph;
	 ArrayList <ResultSet> subResultSet;
	 String propertyLimitDesc;
	 HashMap <String,Object> propertyOverRideHash;
	 HashMap <String,Object> extraPropertyHash;
	
	 /**
 	 * Instantiates a new result set.
 	 */
 	public ResultSet(){
		 this.vert = null;
		 this.newDataDelFlag = "";
		 this.doNotOutputFlag = "";
		 this.locationInModelSubGraph = "";
		 this.subResultSet = new ArrayList <ResultSet>();
		 this.propertyLimitDesc = "";
		 this.propertyOverRideHash = new HashMap <String,Object> ();
		 this.extraPropertyHash = new HashMap <String,Object> ();
	 }
	 
 	/**
 	 * Gets the vert.
 	 *
 	 * @return the vert
 	 */
 	public TitanVertex getVert(){
		 return this.vert;
	 }
	 
 	/**
 	 * Gets the sub result set.
 	 *
 	 * @return the sub result set
 	 */
 	public ArrayList <ResultSet> getSubResultSet(){
		 return this.subResultSet;
	 }
	 
 	/**
 	 * Gets the new data del flag.
 	 *
 	 * @return the new data del flag
 	 */
 	public String getNewDataDelFlag(){
		 return this.newDataDelFlag;
	 }
	 
 	/**
 	 * Gets the do not output flag.
 	 *
 	 * @return the do not output flag
 	 */
 	public String getDoNotOutputFlag(){
		 return this.doNotOutputFlag;
	 }
	 
 	/**
 	 * Gets the location in model sub graph.
 	 *
 	 * @return the location in model sub graph
 	 */
 	public String getLocationInModelSubGraph(){
		 return this.locationInModelSubGraph;
	 }
	 
 	/**
 	 * Gets the property limit desc.
 	 *
 	 * @return the property limit desc
 	 */
 	public String getPropertyLimitDesc(){
		 return this.propertyLimitDesc;
	 }
	 
 	/**
 	 * Gets the property over ride hash.
 	 *
 	 * @return the property over ride hash
 	 */
 	public HashMap <String,Object> getPropertyOverRideHash(){
		 return this.propertyOverRideHash;
	 }
	 
 	/**
 	 * Gets the extra property hash.
 	 *
 	 * @return the extra property hash
 	 */
 	public HashMap <String,Object> getExtraPropertyHash(){
		 return this.extraPropertyHash;
	 }

}
