package de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.Utils.normalizeElementName;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;

public class DataModelParser {

	
	public static void main(String[] args) {
		DataModel dataModel = parse("./testDiagram.uml");
		System.out.println(dataModel.getAssociations());
	}
	
	public static DataModel parse(String path) {
		try {
			return new DataModelParser(path).parse();
		} catch (JDOMException | IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private Map<String, List<Element>> elements;
	private Map<String, String> idsToDataObjectNames;
	private String path;
	
	private DataModelParser(String path) {
		this.path = path;
	}
	
	private DataModel parse() throws JDOMException, IOException {
        SAXBuilder builder = new SAXBuilder();
		Document document = builder.build(path);
		Element root = document.getRootElement();
		Namespace xmiNamespace = root.getNamespace("xmi");
        elements = root.getChildren("packagedElement").stream().collect(Collectors.groupingBy(element -> element.getAttributeValue("type", xmiNamespace)));
        
        idsToDataObjectNames = elements.get("uml:Class").stream().collect(Collectors.toMap(
        		element -> element.getAttributeValue("id", xmiNamespace), 
        		element -> normalizeElementName(element.getAttributeValue("name"))));
        

    	Set<Association> associations = elements.get("uml:Association").stream().map(assoc -> {
        	List<Element> endpoints = assoc.getChildren("ownedEnd");
        	assert endpoints.size() == 2;
        	return new Association(
        			parseEndpoint(endpoints.get(0)), 
        			parseEndpoint(endpoints.get(1)));
		}).collect(Collectors.toSet());
		
    	return new DataModel(idsToDataObjectNames.values(), associations);
	}
	
	private AssociationEnd parseEndpoint(Element endpointElement) {
		String id = endpointElement.getAttributeValue("type");
		AssociationEnd endpoint = new AssociationEnd(idsToDataObjectNames.get(id));
		Optional.ofNullable(endpointElement.getChild("lowerValue")).map(this::parseMultiplicity).ifPresent(endpoint::setLowerBound);
		Optional.ofNullable(endpointElement.getChild("upperValue")).map(this::parseMultiplicity).ifPresent(endpoint::setUpperBound);
		return endpoint;
	}
	
	private int parseMultiplicity(Element threshold) {
		String multiplicity = threshold.getAttributeValue("value");
		return multiplicity.equals("*") ? Integer.MAX_VALUE : Integer.parseInt(multiplicity);
	}

}
