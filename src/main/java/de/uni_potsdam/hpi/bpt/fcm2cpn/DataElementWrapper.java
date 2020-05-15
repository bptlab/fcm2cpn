package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.camunda.bpm.model.bpmn.instance.ItemAwareElement;
import org.cpntools.accesscpn.model.Place;

public abstract class DataElementWrapper<Element extends ItemAwareElement, Reference> {

	protected final CompilerApp compilerApp;
	protected final String trimmedName;
	protected final Place place;
	private final List<Element> mappedElements = new ArrayList<>();
	private final List<Reference> mappedReferences = new ArrayList<>();

	public DataElementWrapper(CompilerApp compilerApp, String trimmedName) {
		this.compilerApp = compilerApp;
		this.trimmedName = trimmedName;
		this.place = createPlace();
	}
	
	protected abstract Place createPlace();
	
	public String namePrefix() {
		return trimmedName.replaceAll("\\s", "_");
	}

	public String dataObjectId() {
		return namePrefix() + "Id";
	}

	public String dataObjectCount() {
		return namePrefix() + "Count";
	}
	
    public abstract String annotationForDataFlow(Optional<String> stateName);


	public void addMappedElement(Element dataElement) {
		mappedElements.add(dataElement);
	}
	
	public boolean isForElement(Element dataElement) {
		return mappedElements.contains(dataElement);
	}

	public void addMappedReference(Reference reference) {
		mappedReferences.add(reference);
	}
	
	public boolean isForReference(Reference reference) {
		return mappedReferences.contains(reference);
	}


}
