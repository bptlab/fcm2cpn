package de.uni_potsdam.hpi.bpt.fcm2cpn.utils;

import java.util.List;
import java.util.stream.Stream;

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

	public boolean reads(String dataObject) {
		return readsAsCollection(dataObject) || readsAsNonCollection(dataObject);
	}

	public boolean readsAsCollection(String dataObject) {
		return inputsForName(dataObject).anyMatch(StatefulDataAssociation::isCollection);
	}

	public boolean readsAsNonCollection(String dataObject) {
		return inputsForName(dataObject).anyMatch(each -> !each.isCollection());
	}

	public boolean readsInState(String dataObject, State state) {
		return inputsForName(dataObject).anyMatch(each -> state.getOLC().getState(each.getStateName()).get().equals(state));
	}

	public boolean writes(String dataObject) {
		return writesAsNonCollection(dataObject) || writesAsCollection(dataObject);
	}

	public boolean writesAsCollection(String dataObject) {
		return outputsForName(dataObject).anyMatch(StatefulDataAssociation::isCollection);
	}

	public boolean writesAsNonCollection(String dataObject) {
		return outputsForName(dataObject).anyMatch(each -> !each.isCollection());
	}

	public boolean writesInState(String dataObject, State state) {
		return outputsForName(dataObject).anyMatch(each -> state.getOLC().getState(each.getStateName()).get().equals(state));
	}

	public boolean creates(String dataObject) {
		return writesAsNonCollection(dataObject) && !readsAsNonCollection(dataObject);
	}

	public boolean createsAssociationBetween(String first, String second) {
		return reads(first) && creates(second) || reads(second) && creates(first);
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
	
	
	private Stream<StatefulDataAssociation<DataInputAssociation, DataObjectReference>> inputsForName(String dataObject) {
		return first.stream().filter(each -> each.dataElementName().equals(dataObject));
	}
	
	private Stream<StatefulDataAssociation<DataOutputAssociation, DataObjectReference>> outputsForName(String dataObject) {
		return second.stream().filter(each -> each.dataElementName().equals(dataObject));
	}
}