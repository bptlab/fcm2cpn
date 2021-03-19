package de.uni_potsdam.hpi.bpt.fcm2cpn.dataelements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.ItemAwareElement;
import org.cpntools.accesscpn.model.Arc;
import org.cpntools.accesscpn.model.Place;

import de.uni_potsdam.hpi.bpt.fcm2cpn.CompilerApp;
import de.uni_potsdam.hpi.bpt.fcm2cpn.StatefulDataAssociation;

public abstract class DataElementWrapper<Element extends ItemAwareElement, Reference> implements Comparable<DataElementWrapper<?,?>> {

	protected final CompilerApp compilerApp;
	protected final String normalizedName;
	protected final Place place;
	private final List<Element> mappedElements = new ArrayList<>();
	private final List<Reference> mappedReferences = new ArrayList<>();

	protected final Map<BaseElement, Arc> outgoingArcs;
	protected final Map<BaseElement, Arc> incomingArcs;

	public DataElementWrapper(CompilerApp compilerApp, String normalizedName, Set<String> states) {
		this.compilerApp = compilerApp;
		this.normalizedName = normalizedName;
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

	public String dataElementList() {
		return namePrefix() + "_list";//The "_" is needed to not collide with reserved identifiers such as "as" and "ms"
	}

	public String dataElementCount() {
		return namePrefix() + "Count";
	}
	
	public Place getPlace() {
		return place;
	}
	
    public abstract String annotationForDataFlow(BaseElement otherEnd, StatefulDataAssociation<?, ?> assoc);


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


	public abstract String collectionCreationGuard(BaseElement otherEnd, StatefulDataAssociation<?, ?> assoc);

	public Arc assertMainPageArcTo(BaseElement element) {
		return outgoingArcs.computeIfAbsent(element, _element -> compilerApp.createArc(place, compilerApp.nodeFor(_element)));
	}
	
	public Arc assertMainPageArcFrom(BaseElement element) {
		return incomingArcs.computeIfAbsent(element, _element -> compilerApp.createArc(compilerApp.nodeFor(_element), place));
	}
	
	@Override
	public int compareTo(DataElementWrapper<?, ?> o) {
		return getNormalizedName().compareTo(o.getNormalizedName());
	}
	
	@Override
	public String toString() {
		return getClass().getSimpleName()+"("+normalizedName+")";
	}
	


}
