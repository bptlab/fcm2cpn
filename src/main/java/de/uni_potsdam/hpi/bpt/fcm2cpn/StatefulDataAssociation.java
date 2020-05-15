package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.util.Objects;
import java.util.Optional;

import org.camunda.bpm.model.bpmn.instance.DataAssociation;
import org.camunda.bpm.model.bpmn.instance.ItemAwareElement;

public class StatefulDataAssociation<T extends DataAssociation> {
	final Optional<String> stateName;
	final ItemAwareElement dataElement;//DataObjectReference or DataStoreReference
	final T bpmnAssociation;
	public StatefulDataAssociation(T bpmnAssociation, String stateName, ItemAwareElement dataElement) {
		this.bpmnAssociation = bpmnAssociation;
		this.stateName = Optional.ofNullable(stateName);
		this.dataElement = dataElement;
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
}