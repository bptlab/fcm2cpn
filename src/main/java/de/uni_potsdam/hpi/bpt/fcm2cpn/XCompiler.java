package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.addGuardCondition;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;
import org.cpntools.accesscpn.model.Transition;

public abstract class XCompiler<T extends BaseElement> {
	
	
	protected T element;
	protected CompilerApp parent;
	protected SubpageElement elementPage;
	
	public XCompiler(CompilerApp parent, T element, SubpageElement elementPage) {
		this.element = element;
		this.parent = parent;
		this.elementPage = elementPage;
	}
	
	protected void createCreationRegistrationArcs(Transition transition, Set<DataObjectWrapper> createdObjects) {
		if(!createdObjects.isEmpty()) {
			elementPage.createArcFrom(parent.getRegistryPlace(), transition, "registry");
			elementPage.createArcTo(parent.getRegistryPlace(), transition, 
					"registry^^"+createdObjects.stream().map(DataObjectWrapper::dataElementId).collect(Collectors.toList()).toString()
			);
		}
	}
    
    
    /**
     * Creates arcs for all data associations of one element, for all transitions of that element
     * @param element: The element that writes or reads, usually an activity or event
     * @param outputs: For each stateful data assoc: Which (inputset x outputset)-Transitions write this data object in this state
     * @param inputs: For each stateful data assoc: Which (inputset x outputset)-Transitions read this data object in this state
     */
    protected void createDataAssociationArcs(Map<StatefulDataAssociation<DataOutputAssociation, ?>, List<Transition>> outputs, Map<StatefulDataAssociation<DataInputAssociation, ?>, List<Transition>> inputs) {
    	Set<DataElementWrapper<?,?>> readElements = inputs.keySet().stream().map(parent::wrapperFor).collect(Collectors.toSet());
    	Set<DataElementWrapper<?,?>> writtenElements = outputs.keySet().stream().map(parent::wrapperFor).collect(Collectors.toSet());
    	
    	
        outputs.forEach((assoc, transitions) -> {
        	DataElementWrapper<?,?> dataElement = parent.wrapperFor(assoc);
        	String annotation = dataElement.annotationForDataFlow(element, assoc);
        	linkWritingTransitions(dataElement, annotation, transitions);
    		/*Assert that when writing a data store and not reading, the token read before*/
        	if(!readElements.contains(dataElement) && dataElement.isDataStoreWrapper()) {
        		linkReadingTransitions(dataElement, annotation, transitions);
            	readElements.add(dataElement);
        	}
        });
        
        inputs.forEach((assoc, transitions) -> {
        	DataElementWrapper<?,?> dataElement = parent.wrapperFor(assoc);
            String annotation = dataElement.annotationForDataFlow(element, assoc);
            if(assoc.isCollection()) {
                String guard = dataElement.collectionCreationGuard(element, assoc);
                transitions.stream().forEach(transition -> addGuardCondition(transition, guard));
            }
    		linkReadingTransitions(dataElement, annotation, transitions);

    		/*Assert that when reading and not writing, the unchanged token is put back*/
    		List<Transition> readOnlyTransitions = transitions.stream()
    				.filter(transition -> outputs.entrySet().stream().noneMatch(entry -> parent.wrapperFor(entry.getKey()).equals(dataElement) && entry.getValue().contains(transition)))
    				.collect(Collectors.toList());
    		linkWritingTransitions(dataElement, annotation, readOnlyTransitions);
        });
    }
    

    protected void linkWritingTransitions(DataElementWrapper<?,?> dataElement, String annotation, List<Transition> transitions) {
    	dataElement.assertMainPageArcFrom(element);
    	transitions.forEach(subPageTransition -> {
    		parent.createArc(subPageTransition.getPage(), subPageTransition, elementPage.refPlaceFor(dataElement.place), annotation);
    	});
    }    
    
    protected void linkReadingTransitions(DataElementWrapper<?,?> dataElement, String annotation, List<Transition> transitions) {
    	dataElement.assertMainPageArcTo(element);
    	transitions.forEach(subPageTransition -> {
    		parent.createArc(subPageTransition.getPage(), elementPage.refPlaceFor(dataElement.place), subPageTransition, annotation);
    	});
    }
    
    public abstract void compile();
    



}
