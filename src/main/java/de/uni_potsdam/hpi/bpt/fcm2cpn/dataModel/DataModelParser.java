package de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.normalizeElementName;

import java.io.File;
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


	private Namespace xmiNamespace;

	public static void main(String[] args) {
		DataModel dataModel = parse(new File("./testDiagram.uml"));
		System.out.println(dataModel.getAssociations());
	}
	
	public static DataModel parse(File file) {
		try {
			return new DataModelParser(file).parse();
		} catch (JDOMException | IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private Map<String, List<Element>> elements;
	private Map<String, String> idsToDataObjectNames;
	private File file;
	
	private DataModelParser(File file) {
		this.file = file;
	}
	
	private DataModel parse() throws JDOMException, IOException {
		SAXBuilder builder = new SAXBuilder();
		Document document = builder.build(file);
		Element root = document.getRootElement();
		xmiNamespace = root.getNamespace("xmi");
        elements = root.getChildren("packagedElement").stream().collect(Collectors.groupingBy(element -> element.getAttributeValue("type", xmiNamespace)));
        
        idsToDataObjectNames = elements.get("uml:Class").stream().collect(Collectors.toMap(
        		element -> element.getAttributeValue("id", xmiNamespace), 
        		element -> normalizeElementName(element.getAttributeValue("name"))));
        

    	Set<Association> associations = elements.get("uml:Association").stream().map(assoc -> {
        	List<Element> endpoints = assoc.getChildren("ownedEnd");
        	List<Element> comments = assoc.getChildren("ownedComment");
        	assert endpoints.size() == 2;
        	return new Association(
        			parseEndpoint(endpoints.get(0), comments),
        			parseEndpoint(endpoints.get(1), comments));
		}).collect(Collectors.toSet());
		
    	return new DataModel(idsToDataObjectNames.values(), associations);
	}
	
	private AssociationEnd parseEndpoint(Element endpointElement, List<Element> comments) {
		String id = endpointElement.getAttributeValue("id", xmiNamespace);
		String classId = endpointElement.getAttributeValue("type");
		AssociationEnd endpoint = new AssociationEnd(idsToDataObjectNames.get(classId));
		Optional.ofNullable(endpointElement.getChild("lowerValue")).map(this::parseMultiplicity).ifPresent(endpoint::setLowerBound);
		Optional.ofNullable(endpointElement.getChild("upperValue")).map(this::parseMultiplicity).ifPresent(endpoint::setUpperBound);
		comments.stream()
				.filter(element -> element.getAttributeValue("annotatedElement").equals(id))
				.findFirst()
				.map(this::parseGoalLowerBound)
				.ifPresent(endpoint::setGoalLowerBound);
		return endpoint;
	}

	private int parseGoalLowerBound(Element element) {
		String comment = element.getChild("body").getValue();
		return Integer.parseInt(comment.replaceAll("goalLowerBound:\\s+", ""));
	}

	private int parseMultiplicity(Element threshold) {
		String multiplicity = threshold.getAttributeValue("value");
		if(multiplicity == null) multiplicity = "0";
		return multiplicity.equals("*") ? AssociationEnd.UNLIMITED : Integer.parseInt(multiplicity);
	}

}
