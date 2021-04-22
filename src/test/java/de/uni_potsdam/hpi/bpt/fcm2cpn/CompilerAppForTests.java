package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.cpntools.accesscpn.model.PetriNet;

import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.DataModel;
import de.uni_potsdam.hpi.bpt.fcm2cpn.terminationconditions.TerminationCondition;

public class CompilerAppForTests extends CompilerApp {

    public CompilerAppForTests(BpmnModelInstance bpmn, Optional<DataModel> dataModel, Optional<TerminationCondition> terminationCondition) {
		super(bpmn, dataModel, terminationCondition);
	}
    

    public PetriNet _translateBPMN2CPN() {
    	Method method;
		try {
			method = CompilerApp.class.getDeclaredMethod("translateBPMN2CPN");
	    	method.setAccessible(true);
	    	return (PetriNet) method.invoke(this);
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
    }
	
}
