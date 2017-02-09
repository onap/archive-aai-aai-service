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

package org.openecomp.aai.workarounds;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.common.base.CaseFormat;

public class ModifyOXMProperties {

	private static String[] versions = new String[]{"v8"};
	
	/**
	 * The main method.
	 *
	 * @param args the arguments
	 * @throws SAXException the SAX exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws ParserConfigurationException the parser configuration exception
	 * @throws XPathExpressionException the x path expression exception
	 * @throws TransformerException the transformer exception
	 */
	public static void main(String[] args) throws SAXException, IOException, ParserConfigurationException, XPathExpressionException, TransformerException {

		for (int i = 0; i < versions.length; i++) {
			process(versions[i]);
		}
		
		
		
	}
	
	/**
	 * Process.
	 *
	 * @param version the version
	 * @throws SAXException the SAX exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws ParserConfigurationException the parser configuration exception
	 * @throws XPathExpressionException the x path expression exception
	 * @throws TransformerException the transformer exception
	 */
	private static void process(String version) throws SAXException, IOException, ParserConfigurationException, XPathExpressionException, TransformerException {


		
		String filepath = "bundleconfig-local/etc/oxm/aai_oxm_" + version + ".xml";
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		docFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		Document doc = docBuilder.parse(filepath);
		XPath xpath = XPathFactory.newInstance().newXPath();
		NodeList list = doc.getElementsByTagName("java-type");
		//Find namespaces - restrict to inventory
		//XPathExpression expr = xpath.compile("//java-type[java-attributes[count(xml-element[contains(@type, 'aai.openecomp.org')])=count(./xml-element)][count(xml-element) > 1]]/xml-root-element");
		XPathExpression expr = xpath.compile("//java-type[@name='Inventory']/java-attributes/xml-element/@name");
		Object result = expr.evaluate(doc, XPathConstants.NODESET);
		NodeList nodes = (NodeList) result;
		String[] temp = null;
		List<String> itemsUnderInventory = new ArrayList<>();
		for (int i = 0; i < nodes.getLength(); i++) {
			itemsUnderInventory.add(nodes.item(i).getTextContent());
		}
		Map<String, String> namespaces = new HashMap<>();

		itemsUnderInventory.remove("Search");
		for (String item : itemsUnderInventory) {
			expr = xpath.compile("//java-type[xml-root-element/@name='" + item + "']");
			result = expr.evaluate(doc, XPathConstants.NODESET);
			nodes = (NodeList) result;
			for (int i = 0; i < nodes.getLength(); i++) {
				String a = "java-attributes/xml-element/@type[contains(.,'aai.openecomp.org')]";
				XPathExpression expr2 = xpath.compile(a);
				Object result2 = expr2.evaluate(nodes.item(i), XPathConstants.NODESET);

				NodeList node2 = (NodeList) result2;
				for (int j = 0; j < node2.getLength(); j++) {
					temp = node2.item(j).getTextContent().split("\\.");
					namespaces.put(temp[temp.length-1], item);
				}
			}
		}
		
		
		
		//go through plurals
		expr = xpath.compile("//java-type[java-attributes[count(xml-element) = 1]/xml-element[contains(@type, 'aai.openecomp.org')]]/xml-root-element");

		result = expr.evaluate(doc, XPathConstants.NODESET);
		nodes = (NodeList) result;
		List<String> children = new ArrayList<>();
		for (int i = 0; i < nodes.getLength(); i++) {
			XPathExpression expr2 = xpath.compile("../java-attributes/xml-element[1]/@type[contains(.,'aai.openecomp.org')]");
			Object result2 = expr2.evaluate(nodes.item(i), XPathConstants.NODESET);

			NodeList node2 = (NodeList) result2;
			temp = node2.item(0).getTextContent().split("\\.");
			String containerName = nodes.item(i).getAttributes().getNamedItem("name").getTextContent();
			String childrenTuple = containerName + "," + temp[temp.length-1];
			if (namespaces.containsKey(CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL,containerName))) {
				childrenTuple += "," + namespaces.get(CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL,containerName));
			}
			children.add(childrenTuple);
		}
		
		//match types up with plurals
		String[] split = null;
		for (String s : children) {
			split = s.split(",");
			expr = xpath.compile("//java-type[@name='"+split[1]+"']/xml-properties");
			result = expr.evaluate(doc, XPathConstants.NODESET);
			nodes = (NodeList) result;
			if (nodes.getLength() > 0) {
				Element property = null;
				
				if (!hasChild(nodes.item(0), "name", "container")) {

					property = doc.createElement("xml-property");
					
					property.setAttribute("name", "container");
					property.setAttribute("value",split[0]);
					nodes.item(0).appendChild(property);

				}
				
				if (split.length == 3) {
					Element property2 = null;
					if (!hasChild(nodes.item(0), "name", "namespace")) {
						property2 = doc.createElement("xml-property");
						property2.setAttribute("name", "namespace");
						property2.setAttribute("value",split[2]);
						nodes.item(0).appendChild(property2);
					}

				}
			}


		}
		
		filepath = "bundleconfig-local/etc/oxm/aai_oxm_" + version + ".xml";
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		Transformer transformer = transformerFactory.newTransformer();
		DOMSource source = new DOMSource(doc);
		StreamResult file = new StreamResult(new File(filepath));
		transformer.transform(source, file);
	}
	
	/**
	 * Checks for child.
	 *
	 * @param node the node
	 * @param name the name
	 * @param value the value
	 * @return true, if successful
	 */
	private static boolean hasChild(Node node, String name, String value) {
		boolean result = false;
		NodeList list = node.getChildNodes();
		Node temp = null;
		for (int i = 0; i < list.getLength(); i++) {

			if (list.item(i).hasAttributes()) {
				temp = list.item(i).getAttributes().getNamedItem(name);
			}
			
			if (temp != null && temp.getTextContent().equals(value)) {
				result =  true;
			}
		}
		
		return result;
	}

}
