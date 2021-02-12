package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.util.Objects;
import java.util.Optional;

import org.camunda.bpm.model.bpmn.instance.DataAssociation;
import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
import org.camunda.bpm.model.bpmn.instance.ItemAwareElement;

import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Pair;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils;

public class StatefulDataAssociation<AssociationType extends DataAssociation, DataElement extends ItemAwareElement> {
	private final Optional<String> stateName;
	private final DataElement dataElement;//DataObjectReference or DataStoreReference
	private final AssociationType bpmnAssociation;//DataInputAssociation or DataOutputAssociation
	private final boolean isCollection;
	public StatefulDataAssociation(AssociationType bpmnAssociation, String stateName, DataElement dataElement, boolean isCollection) {
		this.bpmnAssociation = bpmnAssociation;
		this.stateName = Optional.ofNullable(stateName);
		this.dataElement = dataElement;
		this.isCollection = isCollection;
		
		assert isDataObjectReference() ^ isDataStoreReference();
		assert isInput() ^ isOutput();
	}
	
	public Optional<String> getStateName() {
		return stateName;
	}

	public DataElement getDataElement() {
		return dataElement;
	}
	
	public Pair<String, Boolean> dataElementNameAndCollection() {
		return new Pair<>(dataElementName(), isCollection());
	}
	
	public boolean equalsDataElementAndCollection(StatefulDataAssociation<?, ?> otherAssoc) {
		return this.dataElementNameAndCollection().equals(otherAssoc.dataElementNameAndCollection());
	}
	
	public String dataElementName() {
		return Utils.normalizeElementName(Utils.elementName(Utils.getReferencedElement(dataElement)));
	}

	public AssociationType getBpmnAssociation() {
		return bpmnAssociation;
	}

	public boolean isCollection() {
		return isCollection;
	}
	
	public boolean isDataObjectReference() {
		return dataElement instanceof DataObjectReference;
	}
	
	public boolean isDataStoreReference() {
		return dataElement instanceof DataStoreReference;
	}
	
	public boolean isInput() {
		return bpmnAssociation instanceof DataInputAssociation;
	}
	
	public boolean isOutput() {
		return bpmnAssociation instanceof DataOutputAssociation;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Objects.hash(bpmnAssociation, stateName, isCollection);
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		StatefulDataAssociation<?,?> other = (StatefulDataAssociation<?,?>) obj;
		return Objects.equals(bpmnAssociation, other.bpmnAssociation) && Objects.equals(stateName, other.stateName) && Objects.equals(isCollection, other.isCollection);
	}
	
	@Override
	public String toString() {
		return this.getClass().getSimpleName()+"("+dataElement.getAttributeValue("name")+", "+stateName.orElse(null)+")";
	}
}