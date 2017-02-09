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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Date;

import com.sun.tools.javac.util.List;

//SWGK - 01/08/2016 - Helper function to deal with concurrency control
public class MergeResource {

	//Merge Assumptions:
	//fromresource and toresource are the same resource type and same resource object
	//fromresource is the latest version from the DB
	//toresource is the version of the same resource with updates
	//merging of child elements are complete overwrite of fromresource corresponding child element
	//merging of relationshiplist is complete overwrite of fromresource corresponding releationship element
	//In case of supplying the only specific child element update, please specify the child element type (need not be in canonical form)
    //For parent only update (not involving child or relationship element update), then all the child elements and relationship list will be set as null in the merged object
	//For setting null to primitive type (including String) you have to do it after the merge is called specifically for parent only copy
	
	
	/**
	 * Merge.
	 *
	 * @param <T> the generic type
	 * @param fromresource the fromresource
	 * @param toresource the toresource
	 * @param bupdateChildren the bupdate children
	 * @param childNamelist the child namelist
	 * @param bupdateRelatedLink the bupdate related link
	 * @return the t
	 */
	public static <T> T merge(T fromresource, T toresource, boolean bupdateChildren, String childNamelist[], boolean bupdateRelatedLink)
	{
		Field[] fields = fromresource.getClass().getDeclaredFields();
		if (fields != null)
		{
		    for (Field field : fields)
		    {
		    	try
		    	{
			    	field.setAccessible(true);
			    	if ( field.getName().equalsIgnoreCase("resourceVersion") )
			    		continue;
			    	if ( isValidMergeType(field.getType()) )
			    	{
			    		 Object obj = field.get(toresource);
			    	     // If the updated resource's any property to be set null then one has to set it separately for the merged object
			    	     if (obj != null)
			    	       field.set(fromresource, obj);
			    	     continue;
			    	}
			    	else
			    		// set the child list or relatedTo link to be null so no updates takes place
			    		field.set(fromresource, null);
			    	//override situation
			    	if (bupdateChildren || bupdateRelatedLink)
			    	{
			    		if (bupdateRelatedLink && field.getName().equalsIgnoreCase("relationshipList"))
			    		{
		    		       Object obj = field.get(toresource);
				    	   field.set(fromresource, obj);
				    	   continue;
			    		}
			    		if (field.getName().equalsIgnoreCase("relationshipList"))
			    			if (!bupdateRelatedLink)
			    				continue;
			    		// not an efficient as it blindly updates all children - onus is on callee to nullify
			    		// specific child(ren) that they don't want to update after the merge call.
			    		// can be optimized to send a list of children class names in canonical form
			    		// but deferring for next release so that only those children can be merged
			    		if (bupdateChildren && (childNamelist != null))
			    		{
			    		  for (String classStringName : childNamelist)
			    		  {
			    			 if ( !classStringName.isEmpty() && field.getType().toString().toLowerCase().endsWith(classStringName.toLowerCase()) )
			    			 {
				    		     Object obj = field.get(toresource);
					    	     field.set(fromresource, obj);
			    			 }
			    		  }
			    		  continue;
			    		}
			    		
			    		if (bupdateChildren && (childNamelist == null))
			    		{
			    		   Object obj = field.get(toresource);
					       field.set(fromresource, obj);
			    		
			    		}
			    	}
			    	
		    	}
		    	catch (Exception e)
	    		{
	    		
	    		}
		    	
		    	
		    }
		}
		return fromresource;
	}
	
	
	/**
	 * Merge.
	 *
	 * @param <T> the generic type
	 * @param fromresource the fromresource
	 * @param toresource the toresource
	 * @return the t
	 */
	public static <T> T merge(T fromresource, T toresource)
	{
		return merge(fromresource, toresource, false, false);
	}
	
	/**
	 * Merge.
	 *
	 * @param <T> the generic type
	 * @param fromresource the fromresource
	 * @param toresource the toresource
	 * @param bupdateChildren the bupdate children
	 * @param bupdateRelatedLink the bupdate related link
	 * @return the t
	 */
	public static <T> T merge(T fromresource, T toresource, boolean bupdateChildren, boolean bupdateRelatedLink)
	{
		return merge(fromresource, toresource, bupdateChildren, null, bupdateRelatedLink);
	}
	
	/**
	 * Checks if is valid merge type.
	 *
	 * @param fieldType the field type
	 * @return true, if is valid merge type
	 */
	public static boolean isValidMergeType(Class<?> fieldType) {
        if (fieldType.equals(String.class)) {
            return true;
        } else if (Date.class.isAssignableFrom(fieldType)) {
            return true;
        } else if (Number.class.isAssignableFrom(fieldType)) {
            return true;
        } else if (fieldType.equals(Integer.TYPE)) {
            return true;
        } else if (fieldType.equals(Long.TYPE)) {
            return true;
        } else if (Enum.class.isAssignableFrom(fieldType)) {
            return true;
        } else if (Boolean.class.isAssignableFrom(fieldType)) {
            return true;
        }
        else {
            return false;
        }
    }
	
	/**
	 * Gets the child return type.
	 *
	 * @param classname the classname
	 * @param methodname the methodname
	 * @return the class
	 */
	public static Class<?> GetChildReturnType(String classname, String methodname) 
	{
		try {
		    Class<?> c = Class.forName(classname);
		    Method[] allMethods = c.getDeclaredMethods();
		    for (Method m : allMethods) {
				if (!m.getName().equals(methodname)) {
				    return m.getReturnType();
				}
		    }
		} catch (ClassNotFoundException x) {
		   
		}
	    
		return null;
	}
}
