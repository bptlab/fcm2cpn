package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.util.Objects;
import java.util.Optional;

import org.camunda.bpm.model.bpmn.instance.DataAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
import org.camunda.bpm.model.bpmn.instance.ItemAwareElement;

public class StatefulDataAssociation<T extends DataAssociation> {
	private final Optional<String> stateName;
	private final ItemAwareElement dataElement;//DataObjectReference or DataStoreReference
	private final T bpmnAssociation;//DataInputAssociation or DataOutputAssociation
	private final boolean isCollection;
	public StatefulDataAssociation(T bpmnAssociation, String stateName, ItemAwareElement dataElement, boolean isCollection) {
		this.bpmnAssociation = bpmnAssociation;
		this.stateName = Optional.ofNullable(stateName);
		
		assert dataElement instanceof DataObjectReference || dataElement instanceof DataStoreReference;
		this.dataElement = dataElement;
		this.isCollection = isCollection;
	}
	
	public Optional<String> getStateName() {
		return stateName;
	}

	public ItemAwareElement getDataElement() {
		return dataElement;
	}

	public T getBpmnAssociation() {
		return bpmnAssociation;
	}

	public boolean isCollection() {
		return isCollection;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Objects.hash(bpmnAssociation, stateName);
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		StatefulDataAssociation<?> other = (StatefulDataAssociation<?>) obj;
		return Objects.equals(bpmnAssociation, other.bpmnAssociation) && Objects.equals(stateName, other.stateName);
	}
	
	@Override
	public String toString() {
		return this.getClass().getSimpleName()+"("+dataElement.getAttributeValue("name")+", "+stateName.orElse(null)+")";
	}
}