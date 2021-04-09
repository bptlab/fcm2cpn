package de.uni_potsdam.hpi.bpt.fcm2cpn.dataelements;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.assumeNameIsNormalized;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.ItemAwareElement;
import org.cpntools.accesscpn.model.Arc;
import org.cpntools.accesscpn.model.Place;

import de.uni_potsdam.hpi.bpt.fcm2cpn.CompilerApp;
import de.uni_potsdam.hpi.bpt.fcm2cpn.StatefulDataAssociation;

public abstract class DataElementWrapper<Element extends ItemAwareElement, Reference> implements Comparable<DataElementWrapper<?,?>> {

	protected final CompilerApp compilerApp;
	protected final String normalizedName;
	private final List<Element> mappedElements = new ArrayList<>();
	private final List<Reference> mappedReferences = new ArrayList<>();

	protected final Map<Place, Map<BaseElement, Arc>> outgoingArcs = new HashMap<>();
	protected final Map<Place, Map<BaseElement, Arc>> incomingArcs = new HashMap<>();

	public DataElementWrapper(CompilerApp compilerApp, String normalizedName) {
		this.compilerApp = compilerApp;
		assumeNameIsNormalized(normalizedName);
		this.normalizedName = normalizedName;
		
		createPlaces();
	}
	
	protected abstract void createPlaces();
	
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
	
	public abstract Place getPlace(String state);
	
    public abstract String annotationForDataFlow(StatefulDataAssociation<?, ?> assoc);


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


	public abstract String collectionCreationGuard(StatefulDataAssociation<?, ?> assoc, Set<StatefulDataAssociation<DataInputAssociation, ?>> availableInputs);

	public Arc assertMainPageArcTo(BaseElement element, String state) {
		Place place = getPlace(state);
		return outgoingArcsFrom(place).computeIfAbsent(element, _element -> compilerApp.createArc(place, compilerApp.nodeFor(_element)));
	}
	
	private Map<BaseElement, Arc> outgoingArcsFrom(Place place) {
		return outgoingArcs.computeIfAbsent(place, x -> new HashMap<>());
	}
	
	public Arc assertMainPageArcFrom(BaseElement element, String state) {
		Place place = getPlace(state);
		return incomingArcsTo(place).computeIfAbsent(element, _element -> compilerApp.createArc(compilerApp.nodeFor(_element), place));
	}
	
	private Map<BaseElement, Arc> incomingArcsTo(Place place) {
		return incomingArcs.computeIfAbsent(place, x -> new HashMap<>());
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
