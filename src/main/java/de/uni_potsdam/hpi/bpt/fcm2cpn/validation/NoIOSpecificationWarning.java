package de.uni_potsdam.hpi.bpt.fcm2cpn.validation;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.elementName;

import org.camunda.bpm.model.bpmn.instance.Activity;


public class NoIOSpecificationWarning extends Warning {
	private static final long serialVersionUID = 1L;
	
	public final Activity activity;

	public NoIOSpecificationWarning(Activity activity) {
		super("No IO specification found for activity \""+elementName(activity)+"\". Using generated specification instead.");
		this.activity = activity;
	}

}
