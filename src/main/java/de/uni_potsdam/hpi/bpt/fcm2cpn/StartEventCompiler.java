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

import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils;

public class StartEventCompiler extends XCompiler<StartEvent> {

	public StartEventCompiler(CompilerApp parent, StartEvent element, SubpageElement elementPage) {
		super(parent, element, elementPage);
	}
	
	public void compile() {

    	String name = elementName(element);
    	Transition subpageTransition = parent.createTransition(elementPage.getPage(), name);
    	elementPage.getSubpageTransitions().add(subpageTransition);
        
        Place caseTokenPlace = parent.createPlace(elementPage.getPage(), "Case Count", "INT", "1`0");
        parent.createArc(elementPage.getPage(), caseTokenPlace, subpageTransition, "count");
        parent.createArc(elementPage.getPage(), subpageTransition, caseTokenPlace, "count + 1");

        
        List<StatefulDataAssociation<DataOutputAssociation, ?>> outputs = element.getDataOutputAssociations().stream()
        		.flatMap(Utils::splitDataAssociationByState)
        		.collect(Collectors.toList());

        List<DataElementWrapper<?,?>> createdDataElements = outputs.stream()
        		.map(parent::wrapperFor)
                .distinct()
        		.collect(Collectors.toList());
        
        /* 
         * TODO all data outputs of event are using case count, should dedicated counters be created
         * Use Case: When the input event creates a data object that can also be created by a task
         * Then they should both use the same counter, one for the data object, and not the case counter
         */            
        String idVariables = "caseId, "+createdDataElements.stream().map(DataElementWrapper::dataElementId).collect(Collectors.joining(", "));
        String idGeneration = "String.concat[\"case\", Int.toString(count)]"+createdDataElements.stream().map(n -> ",\n("+n.namePrefix() + ", count)").collect(Collectors.joining(""));
        subpageTransition.getCode().setText(String.format(
        	"input (count);\n"
            +"output (%s);\n"
			+"action (%s);",
            idVariables,
            idGeneration));
        
        Set<DataObjectWrapper> createdObjects = createdDataElements.stream()
        		.filter(DataElementWrapper::isDataObjectWrapper)
        		.map(DataObjectWrapper.class::cast)
        		.collect(Collectors.toSet());
        createCreationRegistrationArcs(subpageTransition, createdObjects);
        
        Map<StatefulDataAssociation<DataOutputAssociation, ?>, List<Transition>> outputTransitions = new HashMap<>();
        outputs.forEach(assoc -> outputTransitions.put(assoc, Arrays.asList(subpageTransition)));
    	createDataAssociationArcs(outputTransitions, Collections.emptyMap());
	}

}
