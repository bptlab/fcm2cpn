package de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.dataElementReferenceOf;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.elementName;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.getAssociation;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.getReferencedElement;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.normalizeElementName;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.splitDataAssociationByState;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.DataInput;
import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataOutput;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;
import org.camunda.bpm.model.bpmn.instance.InputSet;
import org.camunda.bpm.model.bpmn.instance.IoSpecification;
import org.camunda.bpm.model.bpmn.instance.ItemAwareElement;
import org.camunda.bpm.model.bpmn.instance.OutputSet;

import de.uni_potsdam.hpi.bpt.fcm2cpn.StatefulDataAssociation;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils;

public class ObjectLifeCycleParser {

    public static ObjectLifeCycle[] getOLCs(DataModel domainModel, BpmnModelInstance bpmn) {
    	return new ObjectLifeCycleParser(domainModel, bpmn).getOLCs();
    }

	private final BpmnModelInstance bpmn;
	private final DataModel domainModel;
	private final Map<String, ObjectLifeCycle> olcForClass;

    private ObjectLifeCycleParser (DataModel domainModel, BpmnModelInstance bpmn) {
    	this.bpmn = bpmn;
    	this.domainModel = domainModel;
        this.olcForClass = new HashMap<>();
    }

    private ObjectLifeCycle[] getOLCs() {
    	determineDataClasses();    	
    	determineStates();
    	determineTransitions();
        addToEachStateReferencesToAssociatedObjectsThatCanBeCreated();
        return olcForClass.values().toArray(new ObjectLifeCycle[0]);
    }

	private void determineDataClasses() {
        bpmn.getModelElementsByType(DataObject.class).stream()
	    	.map(DataObject::getName)
	    	.map(Utils::normalizeElementName)
	    	.forEach(className -> olcForClass.put(className, new ObjectLifeCycle(className)));
    }
	
    private void determineStates() {
    	bpmn.getModelElementsByType(DataObjectReference.class).forEach(dataObjectRef -> {
            ObjectLifeCycle olc = olcForClass.get(normalizeElementName(elementName(getReferencedElement(dataObjectRef))));
    		Utils.dataElementStates(dataObjectRef)
	    		.filter(Objects::nonNull)
	    		.filter(stateName -> olc.getState(stateName).isEmpty())
	    		.forEach(olc::addState);
    	});
	}
    
	private void determineTransitions() {
        bpmn.getModelElementsByType(Activity.class).stream()
	    	.map(Activity::getIoSpecification)
	    	.forEach(this::extractTransitionsFromIoSpecification);
	}
    
    private void extractTransitionsFromIoSpecification(IoSpecification io) {
        for (InputSet inputSet : io.getInputSets()) {
        	for(OutputSet outputSet : inputSet.getOutputSets()) {
                //Determine in which states which object appears at the current io set combination
        		
        		//Map from data objects to states that they are read in the io spec
                Map<String, Set<String>> inputStatesForObject = new HashMap<>();
                for (DataInput input : inputSet.getDataInputs()) {
                    String inputName = getDataObjectName(input);
                    Set<String> inputStates = getDataObjectStates(input);
                    inputStatesForObject
                    	.computeIfAbsent(inputName, s -> new HashSet<>())
                    	.addAll(inputStates);
                }

        		//Map from data objects to states that they are written in the io spec
            	Map<String, Set<String>> outputStatesForObject = new HashMap<>();
                for (DataOutput oRef : outputSet.getDataOutputRefs()) {
                    String outputName = getDataObjectName(oRef);
                    Set<String> outputStates = getDataObjectStates(oRef);
                    outputStatesForObject	                    	
                        .computeIfAbsent(outputName, s -> new HashSet<>())
                    	.addAll(outputStates);
                }
                
                //Create successor relations
                olcForClass.keySet().forEach(dataObject -> {
                    if (inputStatesForObject.containsKey(dataObject) && outputStatesForObject.containsKey(dataObject)) {
                    	Set<String> inputStates = inputStatesForObject.get(dataObject);
                    	Set<String> outputStates = outputStatesForObject.get(dataObject);
                        ObjectLifeCycle olc = olcForClass.get(dataObject);
                        for (String inputState : inputStates) {
                            for (String outputState : outputStates) {
                                olc.getState(inputState).get().addSuccessor(olc.getState(outputState).get());                            
                            }
                        }
                    }
                });
        	}
        }
    }

    private void addToEachStateReferencesToAssociatedObjectsThatCanBeCreated() {
        for (String object : olcForClass.keySet()) {
        	// All associated data objects that have a tighter goal lower bound than lower bound
            List<AssociationEnd> associatedObjectsWithGoalLowerBound = domainModel.getAssociationsForDataObject(object)
                    .map(assoc -> assoc.getOtherEnd(object))
                    .filter(end -> end.getGoalLowerBound() > end.getLowerBound())
                    .collect(Collectors.toList());
            
            for (Activity activity : bpmn.getModelElementsByType(Activity.class)) {
                for (AssociationEnd assocEnd : associatedObjectsWithGoalLowerBound) {
                    if (!activityCreatesObject(activity, assocEnd.getDataObject())) continue;
                    //Whenever an object is created, where a tight goal lower bound to exists, TODO
                    Set<String> requiredStates = getRequiredStates(activity, object);
                    ObjectLifeCycle olc = olcForClass.get(object);
                    requiredStates.stream()
                    	.map(state -> olc.getState(state).get())
                        .forEach(state -> state.addUpdateableAssociation(assocEnd));
                }
            }
        }
    }

    //Returns for one inputset of a certain activity the states that a dataobject is read 
    private static Set<String> getRequiredStates(Activity activity, String dataObject) {
        // TODO generalize to consider I-O-relation
        IoSpecification io = activity.getIoSpecification();
        
        Collection<InputSet> inputSets = activity.getIoSpecification().getInputSets();
        for (InputSet inputSet : inputSets) {
            Collection<DataInput> inputs = inputSet.getDataInputs();
            //Return breaks for loop!
            return inputs.stream()
                    .filter(input -> getDataObjectName(input).equals(dataObject))
                    .flatMap(input -> getDataObjectStates(input).stream())
                    .collect(Collectors.toSet());
        }
        return new HashSet<>(0);
    }

    private static boolean activityCreatesObject(Activity activity, String object) {
        IoSpecification io = activity.getIoSpecification();
        for (OutputSet outputSet : io.getOutputSets()) {
            for (InputSet inputSet : outputSet.getInputSetRefs()) {
	            boolean writesObject = outputSet.getDataOutputRefs().stream()
	            		.map(ObjectLifeCycleParser::getDataObjectName)
	            		.anyMatch(writtenObject -> writtenObject.equals(object));
            	boolean readsObject = inputSet.getDataInputs().stream()
            			.map(ObjectLifeCycleParser::getDataObjectName)
                        .anyMatch(readObject -> readObject.equals(object));
            	if(writesObject && !readsObject) return true;
            }
        }
        return false;
    }

    private static String getDataObjectName(DataInput iRef) {
        DataInputAssociation inputAssoc = getAssociation(iRef);
        ItemAwareElement dataElement = getReferencedElement(inputAssoc.getSources().stream().findAny().get());
        return normalizeElementName(elementName(dataElement));
    }
    
    private static String getDataObjectName(DataOutput oRef) {
        DataOutputAssociation outputAssoc = getAssociation(oRef);
        ItemAwareElement dataElement = getReferencedElement(dataElementReferenceOf(outputAssoc));
        return normalizeElementName(elementName(dataElement));
    }

    private static Set<String> getDataObjectStates(DataInput iRef) {
        DataInputAssociation inputAssoc = getAssociation(iRef);
        return splitDataAssociationByState(inputAssoc)
        		.map(StatefulDataAssociation::getStateName)
        		.flatMap(Optional::stream)
        		.collect(Collectors.toSet());
    }

    private static Set<String> getDataObjectStates(DataOutput oRef) {
        DataOutputAssociation outputAssoc = getAssociation(oRef);
        return splitDataAssociationByState(outputAssoc)
        		.map(StatefulDataAssociation::getStateName)
        		.flatMap(Optional::stream)
        		.collect(Collectors.toSet());
    }

}
