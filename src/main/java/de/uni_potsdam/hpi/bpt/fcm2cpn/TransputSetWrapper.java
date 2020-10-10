package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.util.ArrayList;
import java.util.Collection;

import org.camunda.bpm.model.bpmn.instance.DataAssociation;
import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;

public class TransputSetWrapper<T extends DataAssociation> extends ArrayList<StatefulDataAssociation<T, ?>> {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -7098156477899969498L;

	public TransputSetWrapper(Collection<StatefulDataAssociation<T, ?>> elements) {
		super(elements);
	}
	
	public static class InputSetWrapper extends TransputSetWrapper<DataInputAssociation> {

		/**
		 * 
		 */
		private static final long serialVersionUID = 6777130743519630012L;

		public InputSetWrapper(Collection<StatefulDataAssociation<DataInputAssociation, ?>> elements) {
			super(elements);
		}
		
	}
	
	public static class OutputSetWrapper extends TransputSetWrapper<DataOutputAssociation> {

		/**
		 * 
		 */
		private static final long serialVersionUID = -8021935134458147013L;

		public OutputSetWrapper(Collection<StatefulDataAssociation<DataOutputAssociation, ?>> elements) {
			super(elements);
		}
		
	}

}
