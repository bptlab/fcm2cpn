package de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel;

import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;

import java.util.*;
import java.util.stream.Collectors;

public class ObjectLifeCycle {
    private String className;
    private Set<State> states;

    public ObjectLifeCycle(String className) {
        this.states = new HashSet<>();
        this.className = className;
    }

    public static ObjectLifeCycle[] getOLCs(DataModel domainModel, BpmnModelInstance bpmn) {
        Collection<Task> tasks = bpmn.getModelElementsByType(Task.class);
        Map<String, ObjectLifeCycle> olcForClass = new HashMap<>();
        for (Task task : tasks) {
            IoSpecification io = task.getIoSpecification();
            Collection<InputSet> inputSets = io.getInputSets();
            Collection<OutputSet> outputSets = io.getOutputSets();
            for (OutputSet outputSet : outputSets) {
                Map<String, Set<String>> outputStatesForObject = new HashMap<>();
                Map<String, Set<String>> allStatesForObject = new HashMap<>();
                for (DataOutput oRef : outputSet.getDataOutputRefs()) {
                    String outputName = getDataObjectName(oRef, bpmn);
                    Set<String> outputStates = getDataObjectStates(oRef, bpmn);
                    allStatesForObject.put(outputName, outputStates);
                    outputStatesForObject.put(outputName, outputStates);
                }
                Map<String, Set<String>> inputStatesForObject = new HashMap<>();
                for (InputSet inputSet : inputSets) {
                    for (DataInput input : inputSet.getDataInputs()) {
                        String inputName = getDataObjectName(input, bpmn);
                        Set<String> inputStates = getDataObjectStates(input, bpmn);
                        inputStatesForObject.put(inputName, inputStates);
                        allStatesForObject.compute(inputName, (k, s) -> {
                            if (s == null) {
                                return new HashSet<>(inputStates);
                            } else {
                                s.addAll(inputStates);
                                return s;
                            }});
                    }
                }
                outputStatesForObject.keySet().forEach(k -> olcForClass.computeIfAbsent(k,s -> new ObjectLifeCycle(k)));
                inputStatesForObject.keySet().forEach(k -> olcForClass.computeIfAbsent(k,s -> new ObjectLifeCycle(k)));
                for (Map.Entry<String, Set<String>> e : allStatesForObject.entrySet()) {
                    ObjectLifeCycle olc = olcForClass.get(e.getKey());
                    olc.states.addAll(e.getValue().stream()
                            .map(State::new)
                            .collect(Collectors.toSet()));
                }
                for (Map.Entry<String, Set<String>> e : inputStatesForObject.entrySet()) {
                    ObjectLifeCycle olc = olcForClass.get(e.getKey());
                    if (outputStatesForObject.containsKey(e.getKey())) {
                        for (String stateName : e.getValue()) {
                            State source = olc.states.stream().filter(state -> state.stateName.equals(stateName)).findFirst().get();
                            e.getValue().stream().map(s -> olc.getStates().stream().filter(state -> s.equals(state.stateName)).findFirst().get()).forEach(source::addSuccessor);
                        }
                    }
                }
            }
        }
        addToEachStateReferencesToAssociatedObjectsThatCanBeCreated(olcForClass, domainModel, bpmn);
        return olcForClass.values().toArray(new ObjectLifeCycle[0]);


    }

    private static void addToEachStateReferencesToAssociatedObjectsThatCanBeCreated(Map<String, ObjectLifeCycle> olcForClass, DataModel domainModel, BpmnModelInstance bpmn) {
        for (String object : olcForClass.keySet()) {
            List<AssociationEnd> relevantAssociatedObjects = domainModel.getAssociations().stream()
                    .filter(a -> a.first.getDataObject().equals(object) || a.second.getDataObject().equals(object))
                    .map(a -> a.first.getDataObject().equals(object) ? a.second : a.first)
                    .filter(e -> e.getGoalLowerBound() > e.getLowerBound())
                    .collect(Collectors.toList());
            for (Task task : bpmn.getModelElementsByType(Task.class)) {
                for (AssociationEnd assocEnd : relevantAssociatedObjects) {
                    if (!taskCreatesObject(task, assocEnd.getDataObject(), bpmn)) continue;
                    Set<String> requiredStates = getRequiredStates(task, object, bpmn);
                    ObjectLifeCycle olc = olcForClass.get(object);
                    olc.getStates().stream()
                            .filter(state -> requiredStates.contains(state.getStateName()))
                            .forEach(state -> state.updateableAssociations.add(assocEnd));
                }
            }
        }
    }

    private static Set<String> getRequiredStates(Task task, String dataObject, BpmnModelInstance bpmn) {
        // TODO generalize to consider I-O-relation
        IoSpecification io = task.getIoSpecification();
        Collection<InputSet> inputSets = task.getIoSpecification().getInputSets();
        for (InputSet inputSet : inputSets) {
            Collection<DataInput> inputs = inputSet.getDataInputs();
            return inputs.stream()
                    .filter(i -> getDataObjectName(i, bpmn).equals(dataObject))
                    .flatMap(i -> getDataObjectStates(i, bpmn).stream())
                    .collect(Collectors.toSet());
        }
        return new HashSet<>(0);
    }

    private static boolean taskCreatesObject(Task task, String object, BpmnModelInstance bpmn) {
        IoSpecification io = task.getIoSpecification();
        Collection<OutputSet> outputSets = io.getOutputSets();
        for (OutputSet outputSet : outputSets) {
            Collection<DataOutput> outputs = outputSet.getDataOutputRefs();
            Set<String> objectsWritten =  outputs.stream().map(o -> getDataObjectName(o, bpmn)).collect(Collectors.toSet());
            if (!objectsWritten.contains(object)) continue;
            Collection<InputSet> inputSets = outputSet.getInputSetRefs();
            for (InputSet inputSet : inputSets) {
                if (inputSet.getDataInputs().stream()
                        .map(input -> getDataObjectName(input, bpmn))
                        .noneMatch(name -> name.equals(object))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String getDataObjectName(DataInput iRef, BpmnModelInstance bpmn) {
        String iRefId = iRef.getId();
        Collection<DataInputAssociation> inputAssociations = bpmn.getModelElementsByType(DataInputAssociation.class);
        DataInputAssociation inputAssoc = inputAssociations.stream().filter(assoc -> assoc.getTarget().getId().equals(iRefId)).findFirst().get();
        String objId = inputAssoc.getSources().stream().findFirst().get().getAttributeValue("dataObjectRef");
        return Utils.normalizeElementName(bpmn.getModelElementById(objId).getAttributeValue("name"));
    }

    private static Set<String> getDataObjectStates(DataInput iRef, BpmnModelInstance bpmn) {
        String iRefId = iRef.getId();
        Collection<DataInputAssociation> inputAssociations = bpmn.getModelElementsByType(DataInputAssociation.class);
        DataInputAssociation inputAssoc = inputAssociations.stream().filter(assoc -> assoc.getTarget().getId().equals(iRefId)).findFirst().get();
        return Arrays.stream(inputAssoc.getSources().stream().findFirst().get().getDataState().getName().replaceAll("[\\[\\]]", "").split("\\|")).collect(Collectors.toSet());
    }

    private static Set<String> getDataObjectStates(DataOutput oRef, BpmnModelInstance bpmn) {
        String oRefId = oRef.getId();
        Collection<DataOutputAssociation> outputAssociations = bpmn.getModelElementsByType(DataOutputAssociation.class);
        DataOutputAssociation outputAssoc = outputAssociations.stream().filter(assoc -> assoc.getSources().stream().anyMatch(src -> src.getId().equals(oRefId))).findAny().get();
        return Arrays.stream(outputAssoc.getTarget().getDataState().getName()
                .replaceAll("[\\[\\]]", "")
                .replaceAll("\\s", " ")
                .split("\\|")).collect(Collectors.toSet());
    }

    private static String getDataObjectName(DataOutput oRef, BpmnModelInstance bpmn) {
        String oRefId = oRef.getId();
        Collection<DataOutputAssociation> outputAssociations = bpmn.getModelElementsByType(DataOutputAssociation.class);
        DataOutputAssociation outputAssoc = outputAssociations.stream().filter(assoc -> assoc.getSources().stream().anyMatch(src -> src.getId().equals(oRefId))).findAny().get();
        String objId = outputAssoc.getTarget().getAttributeValue("dataObjectRef");
        return Utils.normalizeElementName(bpmn.getModelElementById(objId).getAttributeValue("name"));
    }

    public static DataState getDataStateForInputAssociation(DataInputAssociation assoc) {
        Collection<ItemAwareElement> references = assoc.getSources();
        for (ItemAwareElement reference : references) {
            if (reference.getItemSubject() instanceof DataObject) {
                return reference.getDataState();
            }
        }
        return null;
    }


    public static DataState getDataStateForOutputAssociation(DataOutputAssociation assoc) {
        ItemAwareElement reference = assoc.getTarget();
        if (reference.getItemSubject() instanceof DataObject) {
            return reference.getDataState();
        }
        return null;
    }

    public static DataObject getDataObjectForInputAssociation(DataInputAssociation assoc) {
        Collection<ItemAwareElement> references = assoc.getSources();
        for (ItemAwareElement reference : references) {
            if (reference.getItemSubject() instanceof DataObject) {
                return (DataObject) reference.getItemSubject();
            }
        }
        return null;
    }

    public static DataObject getDataObjectForOutputAssociation(DataOutputAssociation assoc) {
        ItemAwareElement reference = assoc.getTarget();
        if (reference.getItemSubject() instanceof DataObject) {
            return (DataObject) reference.getItemSubject();
        }
        return null;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public Set<State> getStates() {
        return states;
    }

    public void setStates(Set<State> states) {
        this.states = states;
    }

    public static class State {
        protected String stateName;
        protected Set<State> successors;
        protected Set<AssociationEnd> updateableAssociations;

        public State(String stateName) {
            this.stateName = stateName;
            this.successors = new HashSet<>();
            this.updateableAssociations = new HashSet<>();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof State &&
            stateName.equals(((State)obj).stateName);
        }

        public void addSuccessor(State successor) {
            successors.add(successor);
        }

        public String getStateName() {
            return stateName;
        }

        public void setStateName(String stateName) {
            this.stateName = stateName;
        }

        public Set<State> getSuccessors() {
            return successors;
        }

        public void setSuccessors(Set<State> successors) {
            this.successors = successors;
        }

        public Set<AssociationEnd> getUpdateableAssociations() {
            return updateableAssociations;
        }

        public void setUpdateableAssociations(Set<AssociationEnd> updateableAssociations) {
            this.updateableAssociations = updateableAssociations;
        }

        @Override
        public int hashCode() {
            return stateName.hashCode();
        }
    }
}
