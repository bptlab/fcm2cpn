package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.addGuardCondition;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.cpntools.accesscpn.model.Node;
import org.cpntools.accesscpn.model.PlaceNode;
import org.cpntools.accesscpn.model.Transition;

import de.uni_potsdam.hpi.bpt.fcm2cpn.dataelements.DataElementWrapper;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataelements.DataObjectWrapper;

public abstract class FlowElementCompiler<T extends FlowElement> {
	
	/**The bpmn element that is compiled with this object*/
	protected final T element;
	/**The compiler that is responsible for the whole bpmn model*/
	protected final CompilerApp parent;
	/**Handle for target cpn subpage*/
	protected final SubpageElement elementPage;
	
	public FlowElementCompiler(CompilerApp parent, T element) {
		this.element = element;
		this.parent = parent;
		this.elementPage = parent.createSubpage(element);
	}
	
	protected void createCreationRegistrationArcs(Transition transition, Set<DataObjectWrapper> createdObjects) {
		if(!createdObjects.isEmpty()) {
			elementPage.createArcFrom(parent.getRegistryPlace(), transition, "registry");
			elementPage.createArcTo(parent.getRegistryPlace(), transition, 
					"registry^^"+createdObjects.stream().map(DataObjectWrapper::dataObjectToken).collect(Collectors.toList()).toString()
			);
		}
	}
    
    
    /**
     * Creates arcs for all data associations of the compiled element, for all transitions of that element
     * @param outputs: For each stateful data assoc: Which (inputset x outputset)-Transitions write this data object in this state
     * @param inputs: For each stateful data assoc: Which (inputset x outputset)-Transitions read this data object in this state
     */
    protected void createDataAssociationArcs(
    		Map<StatefulDataAssociation<DataOutputAssociation, ?>, List<Transition>> outputs, 
    		Map<StatefulDataAssociation<DataInputAssociation, ?>, List<Transition>> inputs) {
    	
    	Set<DataElementWrapper<?,?>> readElements = inputs.keySet().stream().map(parent::wrapperFor).collect(Collectors.toSet());
    	
        outputs.forEach((assoc, transitions) -> {
        	DataElementWrapper<?,?> dataElement = parent.wrapperFor(assoc);
        	String annotation = dataElement.annotationForDataFlow(assoc.isCollection());
        	linkWritingTransitions(dataElement, annotation, transitions, assoc);
        	/*Assert that when writing a data store and not reading, the token is read before*/
        	if(!readElements.contains(dataElement) && dataElement.isDataStoreWrapper()) {
        		linkReadingTransitions(dataElement, annotation, transitions, assoc);
            	readElements.add(dataElement);
        	}
        });
        
        inputs.forEach((assoc, transitions) -> {
        	DataElementWrapper<?,?> dataElement = parent.wrapperFor(assoc);
            String annotation = dataElement.annotationForDataFlow(assoc.isCollection());
            /*Collections are initialized in guard to make arcs more tidy*/
            if(assoc.isCollection()) {
                String guard = dataElement.collectionCreationGuard(assoc, inputs.keySet());
                transitions.stream().forEach(transition -> addGuardCondition(transition, guard));
            }
    		linkReadingTransitions(dataElement, annotation, transitions, assoc);

    		/*Assert that when reading and not writing, the unchanged token is put back*/
    		List<Transition> readOnlyTransitions = transitions.stream()
    				.filter(transition -> outputs.entrySet().stream()
    						.filter(entry -> entry.getKey().equalsDataElementAndCollection(assoc))
    						.noneMatch(entry -> entry.getValue().contains(transition)))
    				.collect(Collectors.toList());
    		linkWritingTransitions(dataElement, annotation, readOnlyTransitions, assoc);
        });
    }
    

    protected void linkWritingTransitions(DataElementWrapper<?,?> dataElement, String annotation, List<Transition> transitions, StatefulDataAssociation<?, ?> assoc) {
    	if(!transitions.isEmpty()) dataElement.assertMainPageArcFrom(element, assoc.getStateName());
    	transitions.forEach(subPageTransition -> {
    		elementPage.createArc(subPageTransition, elementPage.refPlaceFor(dataElement.getPlace(assoc.getStateName())), annotation);
    	});
    }    
    
    protected void linkReadingTransitions(DataElementWrapper<?,?> dataElement, String annotation, List<Transition> transitions, StatefulDataAssociation<?, ?> assoc) {
    	if(!transitions.isEmpty()) dataElement.assertMainPageArcTo(element, assoc.getStateName());
    	transitions.forEach(subPageTransition -> {
    		elementPage.createArc(elementPage.refPlaceFor(dataElement.getPlace(assoc.getStateName())), subPageTransition, annotation);
    	});
    }
    
	protected void attachObjectCreationCounters(Transition transition, Set<DataObjectWrapper> createObjects) {
		if(createObjects.isEmpty()) return;
        String countVariables = createObjects.stream().map(DataObjectWrapper::dataElementCount).collect(Collectors.joining(",\n"));
        String idVariables = createObjects.stream().map(DataObjectWrapper::dataElementId).collect(Collectors.joining(",\n"));
        String idGeneration = createObjects.stream().map(object -> "("+object.namePrefix() + ", " + object.dataElementCount() +")").collect(Collectors.joining(",\n"));
        setTransitionCode(transition, countVariables, idVariables, idGeneration);
        createObjects.forEach(object -> {
            PlaceNode caseTokenPlace = object.creationCounterForPage(elementPage);
            elementPage.createArc(caseTokenPlace, transition, object.dataElementCount());
            elementPage.createArc(transition, caseTokenPlace, object.dataElementCount() + " + 1");
        });
    }
	
	protected void setTransitionCode(Transition transition, String input, String output, String action) {
        transition.getCode().setText(String.format(
                "input (%s);\n"
                        + "output (%s);\n"
                        + "action (%s);",
                input,
                output,
                action));
	}
    
    protected Node node() {
    	return elementPage.getMainPageTransition();
    }
    
    public abstract void compile();

	public T getElement() {
		return element;
	}
}
