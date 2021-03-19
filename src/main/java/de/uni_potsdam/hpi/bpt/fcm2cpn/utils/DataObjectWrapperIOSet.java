package de.uni_potsdam.hpi.bpt.fcm2cpn.utils;

import java.util.List;

import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;

import de.uni_potsdam.hpi.bpt.fcm2cpn.DataObjectWrapper;
import de.uni_potsdam.hpi.bpt.fcm2cpn.StatefulDataAssociation;

/** {@link DataObjectIOSet} with data object wrappers as identifier*/
public class DataObjectWrapperIOSet extends DataObjectIOSet<DataObjectWrapper> {

	public DataObjectWrapperIOSet(List<StatefulDataAssociation<DataInputAssociation, DataObjectReference>> first,
			List<StatefulDataAssociation<DataOutputAssociation, DataObjectReference>> second) {
		super(first, second);
	}

	@Override
	protected boolean matches(StatefulDataAssociation<?, DataObjectReference> assoc, DataObjectWrapper dataObject) {
		return dataObject.isForReference(assoc.getDataElement());
	}

}
