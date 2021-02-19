package de.uni_potsdam.hpi.bpt.fcm2cpn.utils;

import java.util.List;

import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;

import de.uni_potsdam.hpi.bpt.fcm2cpn.StatefulDataAssociation;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.ObjectLifeCycle.State;

/** TODO */
public class DataObjectIOSet extends Pair<List<StatefulDataAssociation<DataInputAssociation, DataObjectReference>>, List<StatefulDataAssociation<DataOutputAssociation, DataObjectReference>>> {
	
	public DataObjectIOSet(List<StatefulDataAssociation<DataInputAssociation, DataObjectReference>> first,
			List<StatefulDataAssociation<DataOutputAssociation, DataObjectReference>> second) {
		super(first, second);
	}

	public boolean associationShouldAlreadyBeInPlace(String firstDataObject, String secondDataObject) {
		return reads(firstDataObject) && reads(secondDataObject)
				&& (!writes(firstDataObject)
						|| readsAsCollection(firstDataObject) == writesAsCollection(firstDataObject)
								&& readsAsNonCollection(firstDataObject) == writesAsNonCollection(firstDataObject))
				&& (!writes(secondDataObject)
						|| readsAsCollection(secondDataObject) == writesAsCollection(secondDataObject)
								&& readsAsNonCollection(secondDataObject) == writesAsNonCollection(secondDataObject));
	}

	public boolean reads(String dataObject) {
		return first.stream().anyMatch(each -> each.dataElementName().equals(dataObject));
	}

	public boolean readsAsCollection(String dataObject) {
		return first.stream().anyMatch(each -> each.dataElementName().equals(dataObject) && each.isCollection());
	}

	public boolean readsAsNonCollection(String dataObject) {
		return first.stream().anyMatch(each -> each.dataElementName().equals(dataObject) && !each.isCollection());
	}

	public boolean readsInState(String dataObject, State state) {
		return first.stream().anyMatch(each -> each.dataElementName().equals(dataObject)
				&& state.getOLC().getState(each.getStateName()).get().equals(state));
	}

	public boolean writes(String dataObject) {
		return second.stream().anyMatch(each -> each.dataElementName().equals(dataObject));
	}

	public boolean writesAsCollection(String dataObject) {
		return second.stream().anyMatch(each -> each.dataElementName().equals(dataObject) && each.isCollection());
	}

	public boolean writesAsNonCollection(String dataObject) {
		return second.stream().anyMatch(each -> each.dataElementName().equals(dataObject) && !each.isCollection());
	}

	public boolean writesInState(String dataObject, State state) {
		return second.stream().anyMatch(each -> each.dataElementName().equals(dataObject)
				&& state.getOLC().getState(each.getStateName()).get().equals(state));
	}

	public boolean creates(String dataObject) {
		return writes(dataObject) && !reads(dataObject); //writesAsNonCollection(dataObject) && !readsAsNonCollection(dataObject);
	}

	public boolean createsAssociationBetween(String first, String second) {
		return reads(first) && creates(second) || reads(second) && creates(first);
	}
}