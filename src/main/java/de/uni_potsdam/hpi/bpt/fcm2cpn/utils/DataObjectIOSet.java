package de.uni_potsdam.hpi.bpt.fcm2cpn.utils;

import java.util.List;
import java.util.stream.Stream;

import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;

import de.uni_potsdam.hpi.bpt.fcm2cpn.StatefulDataAssociation;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.ObjectLifeCycle.State;

/** TODO */
public abstract class DataObjectIOSet<DataObject> extends Pair<List<StatefulDataAssociation<DataInputAssociation, DataObjectReference>>, List<StatefulDataAssociation<DataOutputAssociation, DataObjectReference>>> {
	
	public DataObjectIOSet(List<StatefulDataAssociation<DataInputAssociation, DataObjectReference>> first,
			List<StatefulDataAssociation<DataOutputAssociation, DataObjectReference>> second) {
		super(first, second);
	}

	public boolean reads(DataObject dataObject) {
		return readsAsCollection(dataObject) || readsAsNonCollection(dataObject);
	}

	public boolean readsAsCollection(DataObject dataObject) {
		return inputsForName(dataObject).anyMatch(StatefulDataAssociation::isCollection);
	}

	public boolean readsAsNonCollection(DataObject dataObject) {
		return inputsForName(dataObject).anyMatch(each -> !each.isCollection());
	}

	public boolean readsInState(DataObject dataObject, State state) {
		return inputsForName(dataObject).anyMatch(each -> state.getOLC().getState(each.getStateName()).get().equals(state));
	}

	public boolean writes(DataObject dataObject) {
		return writesAsNonCollection(dataObject) || writesAsCollection(dataObject);
	}

	public boolean writesAsCollection(DataObject dataObject) {
		return outputsForName(dataObject).anyMatch(StatefulDataAssociation::isCollection);
	}

	public boolean writesAsNonCollection(DataObject dataObject) {
		return outputsForName(dataObject).anyMatch(each -> !each.isCollection());
	}

	public boolean writesInState(DataObject dataObject, State state) {
		return outputsForName(dataObject).anyMatch(each -> state.getOLC().getState(each.getStateName()).get().equals(state));
	}

	public boolean creates(DataObject dataObject) {
		return writesAsNonCollection(dataObject) && !readsAsNonCollection(dataObject);
	}

	public boolean createsAssociationBetween(DataObject first, DataObject second) {
		return reads(first) && creates(second) || reads(second) && creates(first);
	}
	

	public boolean associationShouldAlreadyBeInPlace(DataObject firstDataObject, DataObject secondDataObject) {
		return reads(firstDataObject) && reads(secondDataObject)
				&& (!writes(firstDataObject)
						|| readsAsCollection(firstDataObject) == writesAsCollection(firstDataObject)
								&& readsAsNonCollection(firstDataObject) == writesAsNonCollection(firstDataObject))
				&& (!writes(secondDataObject)
						|| readsAsCollection(secondDataObject) == writesAsCollection(secondDataObject)
								&& readsAsNonCollection(secondDataObject) == writesAsNonCollection(secondDataObject));
	}
	
	protected abstract boolean matches(StatefulDataAssociation<?, DataObjectReference> assoc, DataObject dataObject);
	
	private Stream<StatefulDataAssociation<DataInputAssociation, DataObjectReference>> inputsForName(DataObject dataObject) {
		return first.stream().filter(each -> matches(each, dataObject));
	}
	
	private Stream<StatefulDataAssociation<DataOutputAssociation, DataObjectReference>> outputsForName(DataObject dataObject) {
		return second.stream().filter(each -> matches(each, dataObject));
	}
}