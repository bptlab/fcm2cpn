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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.DataInput;
import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataOutput;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;
import org.camunda.bpm.model.bpmn.instance.InputSet;
import org.camunda.bpm.model.bpmn.instance.IoSpecification;
import org.camunda.bpm.model.bpmn.instance.ItemAwareElement;
import org.camunda.bpm.model.bpmn.instance.OutputSet;
import org.camunda.bpm.model.bpmn.instance.Task;

import de.uni_potsdam.hpi.bpt.fcm2cpn.StatefulDataAssociation;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.ObjectLifeCycle.State;

public class ObjectLifeCycleParser {

    public static ObjectLifeCycle[] getOLCs(DataModel domainModel, BpmnModelInstance bpmn) {
    	return new ObjectLifeCycleParser(domainModel, bpmn).getOLCs();
    }

	private final BpmnModelInstance bpmn;
	private final DataModel domainModel;

    private ObjectLifeCycleParser (DataModel domainModel, BpmnModelInstance bpmn) {
    	this.bpmn = bpmn;
    	this.domainModel = domainModel;
    }


    private ObjectLifeCycle[] getOLCs() {
        Collection<Task> tasks = bpmn.getModelElementsByType(Task.class);
        Map<String, ObjectLifeCycle> olcForClass = new HashMap<>();
        for (Task task : tasks) {
            IoSpecification io = task.getIoSpecification();
            if (io == null) continue;
            Collection<InputSet> inputSets = io.getInputSets();
            Collection<OutputSet> outputSets = io.getOutputSets();
            for (OutputSet outputSet : outputSets) {
                Map<String, Set<String>> outputStatesForObject = new HashMap<>();
                Map<String, Set<String>> allStatesForObject = new HashMap<>();
                for (DataOutput oRef : outputSet.getDataOutputRefs()) {
                    String outputName = getDataObjectName(oRef);
                    Set<String> outputStates = getDataObjectStates(oRef);
                    allStatesForObject.put(outputName, outputStates);
                    outputStatesForObject.put(outputName, outputStates);
                }
                Map<String, Set<String>> inputStatesForObject = new HashMap<>();
                for (InputSet inputSet : inputSets) {
                    for (DataInput input : inputSet.getDataInputs()) {
                        String inputName = getDataObjectName(input);
                        Set<String> inputStates = getDataObjectStates(input);
                        inputStatesForObject.put(inputName, inputStates);
                        allStatesForObject
                        	.computeIfAbsent(inputName, s -> new HashSet<>())
                        	.addAll(inputStates);
                    }
                }
                outputStatesForObject.keySet().forEach(k -> olcForClass.computeIfAbsent(k,s -> new ObjectLifeCycle(k)));
                inputStatesForObject.keySet().forEach(k -> olcForClass.computeIfAbsent(k,s -> new ObjectLifeCycle(k)));
                for (Map.Entry<String, Set<String>> e : allStatesForObject.entrySet()) {
                    ObjectLifeCycle olc = olcForClass.get(e.getKey());
                    olc.getStates().addAll(e.getValue().stream()
                            .map(State::new)
                            .collect(Collectors.toSet()));
                }
                for (Map.Entry<String, Set<String>> e : inputStatesForObject.entrySet()) {
                    ObjectLifeCycle olc = olcForClass.get(e.getKey());
                    if (outputStatesForObject.containsKey(e.getKey())) {
                        for (String stateName : e.getValue()) {
                            State source = olc.getStates().stream().filter(state -> state.stateName.equals(stateName)).findFirst().get();
                            e.getValue().stream().map(s -> olc.getStates().stream().filter(state -> s.equals(state.stateName)).findFirst().get()).forEach(source::addSuccessor);
                        }
                    }
                }
            }
        }
        addToEachStateReferencesToAssociatedObjectsThatCanBeCreated(olcForClass);
        return olcForClass.values().toArray(new ObjectLifeCycle[0]);
    }

    private void addToEachStateReferencesToAssociatedObjectsThatCanBeCreated(Map<String, ObjectLifeCycle> olcForClass) {
        for (String object : olcForClass.keySet()) {
            List<AssociationEnd> relevantAssociatedObjects = domainModel.getAssociations().stream()
                    .filter(a -> a.first.getDataObject().equals(object) || a.second.getDataObject().equals(object))
                    .map(a -> a.first.getDataObject().equals(object) ? a.second : a.first)
                    .filter(e -> e.getGoalLowerBound() > e.getLowerBound())
                    .collect(Collectors.toList());
            for (Task task : bpmn.getModelElementsByType(Task.class)) {
                for (AssociationEnd assocEnd : relevantAssociatedObjects) {
                    if (!taskCreatesObject(task, assocEnd.getDataObject())) continue;
                    Set<String> requiredStates = getRequiredStates(task, object);
                    ObjectLifeCycle olc = olcForClass.get(object);
                    olc.getStates().stream()
                            .filter(state -> requiredStates.contains(state.getStateName()))
                            .forEach(state -> state.updateableAssociations.add(assocEnd));
                }
            }
        }
    }

    private Set<String> getRequiredStates(Task task, String dataObject) {
        // TODO generalize to consider I-O-relation
        IoSpecification io = task.getIoSpecification();
        Collection<InputSet> inputSets = task.getIoSpecification().getInputSets();
        for (InputSet inputSet : inputSets) {
            Collection<DataInput> inputs = inputSet.getDataInputs();
            return inputs.stream()
                    .filter(i -> getDataObjectName(i).equals(dataObject))
                    .flatMap(i -> getDataObjectStates(i).stream())
                    .collect(Collectors.toSet());
        }
        return new HashSet<>(0);
    }

    private boolean taskCreatesObject(Task task, String object) {
        IoSpecification io = task.getIoSpecification();
        Collection<OutputSet> outputSets = io.getOutputSets();
        for (OutputSet outputSet : outputSets) {
            Collection<DataOutput> outputs = outputSet.getDataOutputRefs();
            Set<String> objectsWritten =  outputs.stream().map(o -> getDataObjectName(o)).collect(Collectors.toSet());
            if (!objectsWritten.contains(object)) continue;
            Collection<InputSet> inputSets = outputSet.getInputSetRefs();
            for (InputSet inputSet : inputSets) {
                if (inputSet.getDataInputs().stream()
                        .map(input -> getDataObjectName(input))
                        .noneMatch(name -> name.equals(object))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getDataObjectName(DataInput iRef) {
        DataInputAssociation inputAssoc = getAssociation(iRef);
        ItemAwareElement dataElement = getReferencedElement(inputAssoc.getSources().stream().findAny().get());
        return normalizeElementName(elementName(dataElement));
    }
    
    private String getDataObjectName(DataOutput oRef) {
        DataOutputAssociation outputAssoc = getAssociation(oRef);
        ItemAwareElement dataElement = getReferencedElement(dataElementReferenceOf(outputAssoc));
        return normalizeElementName(elementName(dataElement));
    }

    private Set<String> getDataObjectStates(DataInput iRef) {
        DataInputAssociation inputAssoc = getAssociation(iRef);
        return splitDataAssociationByState(inputAssoc)
        		.map(StatefulDataAssociation::getStateName)
        		.flatMap(Optional::stream)
        		.collect(Collectors.toSet());
    }

    private Set<String> getDataObjectStates(DataOutput oRef) {
        DataOutputAssociation outputAssoc = getAssociation(oRef);
        return splitDataAssociationByState(outputAssoc)
        		.map(StatefulDataAssociation::getStateName)
        		.flatMap(Optional::stream)
        		.collect(Collectors.toSet());
    }

}
