package de.uni_potsdam.hpi.bpt.fcm2cpn.utils;

import java.util.List;

import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;

import de.uni_potsdam.hpi.bpt.fcm2cpn.StatefulDataAssociation;

/** {@link DataObjectIOSet} with data object names as identifier*/
public class DataObjectIdIOSet extends DataObjectIOSet<String> {

	public DataObjectIdIOSet(List<StatefulDataAssociation<DataInputAssociation, DataObjectReference>> first,
			List<StatefulDataAssociation<DataOutputAssociation, DataObjectReference>> second) {
		super(first, second);
	}

	@Override
	protected boolean matches(StatefulDataAssociation<?, DataObjectReference> assoc, String dataObject) {
		return assoc.dataElementName().equals(dataObject);
	}
	

}