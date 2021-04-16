package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.elementName;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.Transition;

import de.uni_potsdam.hpi.bpt.fcm2cpn.dataelements.DataElementWrapper;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataelements.DataObjectWrapper;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils;

public class StartEventCompiler extends FlowElementCompiler<StartEvent> {

	public StartEventCompiler(CompilerApp parent, StartEvent element) {
		super(parent, element);
	}
	
	public void compile() {

    	String name = elementName(element);
    	Transition subpageTransition = elementPage.createTransition(name);
        
        Place caseTokenPlace = elementPage.createPlace("Case_Count", "INT", "1`0");
        elementPage.createArc(caseTokenPlace, subpageTransition, "count");
        elementPage.createArc(subpageTransition, caseTokenPlace, "count + 1");
        
        parent.createArc(node(), parent.getActiveCasesPlace());
        elementPage.createArc(subpageTransition, elementPage.refPlaceFor(parent.getActiveCasesPlace()), parent.caseId());
        
        List<StatefulDataAssociation<DataOutputAssociation, ?>> outputs = element.getDataOutputAssociations().stream()
        		.flatMap(Utils::splitDataAssociationByState)
        		.collect(Collectors.toList());

        List<DataElementWrapper<?,?>> createdDataElements = outputs.stream()
        		.map(parent::wrapperFor)
                .distinct()
        		.collect(Collectors.toList());
        
        Set<DataObjectWrapper> createdObjects = createdDataElements.stream()
        		.filter(DataElementWrapper::isDataObjectWrapper)
        		.map(DataObjectWrapper.class::cast)
        		.collect(Collectors.toSet());
        attachObjectCreationCounters(subpageTransition, createdObjects);
        if(createdObjects.isEmpty()) setTransitionCode(subpageTransition, "", "", "");        
        createCreationRegistrationArcs(subpageTransition, createdObjects);
        
        Map<StatefulDataAssociation<DataOutputAssociation, ?>, List<Transition>> outputTransitions = new HashMap<>();
        outputs.forEach(assoc -> outputTransitions.put(assoc, Arrays.asList(subpageTransition)));
    	createDataAssociationArcs(outputTransitions, Collections.emptyMap());
	}
	
	@Override
	protected void setTransitionCode(Transition transition, String input, String output, String action) {
		String delim = input.isEmpty() ? "" : ", ";
		super.setTransitionCode(
				transition, 
				"count"+delim+input, 
				"caseId"+delim+output, 
				"String.concat[\"case\", Int.toString(count)]"+delim+action);
	}

}
