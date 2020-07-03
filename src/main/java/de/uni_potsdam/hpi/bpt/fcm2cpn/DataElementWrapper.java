package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.ItemAwareElement;
import org.cpntools.accesscpn.model.Arc;
import org.cpntools.accesscpn.model.Place;

public abstract class DataElementWrapper<Element extends ItemAwareElement, Reference> {

	protected final CompilerApp compilerApp;
	protected final String normalizedName;
	protected final Place place;
	private final List<Element> mappedElements = new ArrayList<>();
	private final List<Reference> mappedReferences = new ArrayList<>();

	protected final Map<BaseElement, Arc> outgoingArcs;
	protected final Map<BaseElement, Arc> incomingArcs;

	public DataElementWrapper(CompilerApp compilerApp, String trimmedName) {
		this.compilerApp = compilerApp;
		this.normalizedName = trimmedName;
		this.place = createPlace();
		
		outgoingArcs = new HashMap<>();
		incomingArcs = new HashMap<>();
	}
	
	protected abstract Place createPlace();
	
	public String getNormalizedName() {
		return normalizedName;
	}
	
	public String namePrefix() {
		return normalizedName.replaceAll("\\s", "_");
	}

	public String dataElementId() {
		return namePrefix() + "Id";
	}

	public String dataElementCount() {
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
	
	public boolean isForReference(ItemAwareElement reference) {
		return mappedReferences.contains(reference);
	}

	public abstract boolean isDataObjectWrapper();
	public abstract boolean isDataStoreWrapper();
	
	
	public Arc assertMainPageArcTo(BaseElement element) {
		return outgoingArcs.computeIfAbsent(element, _element -> compilerApp.createArc(place, compilerApp.nodeFor(_element)));
	}
	
	public Arc assertMainPageArcFrom(BaseElement element) {
		return incomingArcs.computeIfAbsent(element, _element -> compilerApp.createArc(compilerApp.nodeFor(_element), place));
	}
	


}
