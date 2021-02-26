package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.addGuardCondition;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.allCombinationsOf;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.elementName;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.getSource;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.getTarget;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.normalizeElementName;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.ObjectLifeCycle;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
import org.camunda.bpm.model.bpmn.instance.IoSpecification;
import org.camunda.bpm.model.bpmn.instance.OutputSet;
import org.cpntools.accesscpn.model.Transition;

import de.uni_potsdam.hpi.bpt.fcm2cpn.TransputSetWrapper.InputSetWrapper;
import de.uni_potsdam.hpi.bpt.fcm2cpn.TransputSetWrapper.OutputSetWrapper;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.Association;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.AssociationEnd;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Pair;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils;


/**
 * Encapsulates the compilation of one bpmn activity
 * @author Leon Bein
 *
 */
public class ActivityCompiler extends FlowElementCompiler<Activity> {
	
	private Map<StatefulDataAssociation<DataOutputAssociation, ?>, List<Transition>> outputtingTransitions;
	private Map<StatefulDataAssociation<DataInputAssociation, ?>, List<Transition>> inputtingTransitions;
	
	private int transputSetIndex = 0;

	private boolean hasCreatedArcToAssocPlace = false;
	private boolean hasCreatedArcFromAssocPlace = false;
	
	/** Placeholder until goal cardinalities are implemented */
	public static final String GOAL_CARDINALITY = "(*goal cardinality*)";
	

	public ActivityCompiler(CompilerApp parent, Activity activity) {
		super(parent, activity);
	}

	public void compile() {
        
        outputtingTransitions = element.getDataOutputAssociations().stream()
        		.flatMap(Utils::splitDataAssociationByState)
        		.collect(Collectors.toMap(Function.identity(), x -> new ArrayList<>()));
        inputtingTransitions = element.getDataInputAssociations().stream()
        		.flatMap(Utils::splitDataAssociationByState)
        		.collect(Collectors.toMap(Function.identity(), x -> new ArrayList<>()));
        // All possible combinations of input and output sets, either defined by io-specification or *all* possible combinations are used
        for (Pair<InputSetWrapper, OutputSetWrapper> transputSet : transputSets()) {
        	compileTransputsSet(transputSet);
        }
        
        createDataAssociationArcs(outputtingTransitions, inputtingTransitions);
	}
	
	private List<Pair<InputSetWrapper, OutputSetWrapper>> transputSets() {
		return transputSetsFromIoSpecification(element.getIoSpecification());
	}
	
    private List<Pair<InputSetWrapper, OutputSetWrapper>> transputSetsFromIoSpecification(IoSpecification ioSpecification) {
        List<Pair<InputSetWrapper, OutputSetWrapper>> transputSets = new ArrayList<>();
        
        // Output sets mapped to data assocs and split by "|" state shortcuts
    	Map<OutputSet, List<OutputSetWrapper>> translatedOutputSets = ioSpecification.getOutputSets().stream().collect(Collectors.toMap(Function.identity(), outputSet -> {
            Map<Pair<DataElementWrapper<?,?>, Boolean>, List<StatefulDataAssociation<DataOutputAssociation, ?>>> outputsPerObject = outputSet.getDataOutputRefs().stream()
            		.map(Utils::getAssociation)
            		.flatMap(Utils::splitDataAssociationByState)
            		.collect(Collectors.groupingBy(assoc -> new Pair<>(parent.wrapperFor(assoc), assoc.isCollection())));
            return allCombinationsOf(outputsPerObject.values()).stream().map(OutputSetWrapper::new).collect(Collectors.toList());
        }));
    	
    	ioSpecification.getInputSets().forEach(inputSet -> {
            Map<Pair<DataElementWrapper<?,?>, Boolean>, List<StatefulDataAssociation<DataInputAssociation, ?>>> inputsPerObject = inputSet.getDataInputs().stream()
            		.map(Utils::getAssociation)
            		.flatMap(Utils::splitDataAssociationByState)
            		.collect(Collectors.groupingBy(assoc -> new Pair<>(parent.wrapperFor(assoc), assoc.isCollection())));
            List<InputSetWrapper> translatedInputSets = allCombinationsOf(inputsPerObject.values()).stream().map(InputSetWrapper::new).collect(Collectors.toList());
            for(OutputSet outputSet : inputSet.getOutputSets()) {
            	for(OutputSetWrapper translatedOutputSet : translatedOutputSets.get(outputSet)) {
                	for(InputSetWrapper translatedInputSet : translatedInputSets) {
                		transputSets.add(new Pair<>(translatedInputSet, translatedOutputSet));
                	}
            	}
            }
        });
        
        //Data Stores are (at least in Signavio) not part of input or output sets
        List<StatefulDataAssociation<DataInputAssociation, ?>> dataStoreInputs = element.getDataInputAssociations().stream()
			   .filter(assoc -> getSource(assoc) instanceof DataStoreReference)
			   .flatMap(Utils::splitDataAssociationByState)
			   .collect(Collectors.toList());

        List<StatefulDataAssociation<DataOutputAssociation, ?>> dataStoreOutputs = element.getDataOutputAssociations().stream()
 			   .filter(assoc -> getTarget(assoc) instanceof DataStoreReference)
 			   .flatMap(Utils::splitDataAssociationByState)
 			   .collect(Collectors.toList());
        
        for(Pair<InputSetWrapper, OutputSetWrapper> transputSet : transputSets) {
        	transputSet.first.addAll(dataStoreInputs);
        	transputSet.second.addAll(dataStoreOutputs);
        }
    
    	return transputSets;
    }
 
    
    private void compileTransputsSet(Pair<InputSetWrapper, OutputSetWrapper> transputSet) {
        InputSetWrapper inputSet = transputSet.first;
        OutputSetWrapper outputSet = transputSet.second;
        
        Map<DataObjectWrapper, List<StatefulDataAssociation<DataInputAssociation, DataObjectReference>>> readContext = inputSet.stream()
        	.filter(StatefulDataAssociation::isDataObjectReference)
        	.map(reference -> (StatefulDataAssociation<DataInputAssociation, DataObjectReference>) reference)
        	.collect(Collectors.groupingBy(reference -> (DataObjectWrapper) parent.wrapperFor(reference)));
        Set<DataObjectWrapper> readObjects = readContext.keySet();
        
        Map<DataObjectWrapper, List<StatefulDataAssociation<DataOutputAssociation, DataObjectReference>>> writeContext = outputSet.stream()
            	.filter(StatefulDataAssociation::isDataObjectReference)
            	.map(reference -> (StatefulDataAssociation<DataOutputAssociation, DataObjectReference>) reference)
            	.collect(Collectors.groupingBy(reference -> (DataObjectWrapper) parent.wrapperFor(reference)));
        Set<DataObjectWrapper> writtenObjects = writeContext.keySet();
            
        Set<DataObjectWrapper> createdObjects = writtenObjects.stream()
                .filter(object -> !readObjects.contains(object) || (readContext.get(object).stream().allMatch(StatefulDataAssociation::isCollection) && !writeContext.get(object).stream().allMatch(StatefulDataAssociation::isCollection)))
                .collect(Collectors.toSet());

        Transition subpageTransition = elementPage.createTransition(elementName(element) + "_" + transputSetIndex);
        attachObjectCreationCounters(subpageTransition, createdObjects);
        createCreationRegistrationArcs(subpageTransition, createdObjects);
        
        associateDataObjects(subpageTransition, readObjects, writtenObjects, readContext, writeContext);
    	
        inputSet.forEach(input -> inputtingTransitions.get(input).add(subpageTransition));
        outputSet.forEach(output -> outputtingTransitions.get(output).add(subpageTransition));
        
        transputSetIndex++;
    }
	
    
    /**Alias class for better readability*/
    private class StateChange extends Pair<StatefulDataAssociation<DataInputAssociation, DataObjectReference>, StatefulDataAssociation<DataOutputAssociation, DataObjectReference>> {
		public StateChange(StatefulDataAssociation<DataInputAssociation, DataObjectReference> first, StatefulDataAssociation<DataOutputAssociation, DataObjectReference> second) {
			super(first, second);
			assert first.equalsDataElementAndCollection(second);
		}
		
		public DataObjectWrapper dataObject() {
			assert parent.wrapperFor(first).equals(parent.wrapperFor(second));
			return (DataObjectWrapper) parent.wrapperFor(first);
		}
	}
    
    private void associateDataObjects(Transition transition, Set<DataObjectWrapper> readDataObjects, Set<DataObjectWrapper> writtenDataObjects, Map<DataObjectWrapper, List<StatefulDataAssociation<DataInputAssociation, DataObjectReference>>> readContext, Map<DataObjectWrapper, List<StatefulDataAssociation<DataOutputAssociation, DataObjectReference>>> writeContext) {

    	Set<Pair<DataObjectWrapper, DataObjectWrapper>> associationsToWrite = determineAssociationsToWrite(readDataObjects, writtenDataObjects, readContext, writeContext);    	
    	Set<StateChange> stateChangesToPerform = determineStateChanges(readContext, writeContext);

		// Add guards for goal cardinalities
		addGuardsForStateChanges(stateChangesToPerform, transition, readDataObjects, writtenDataObjects, readContext, writeContext);
		
		Set<Pair<DataObjectWrapper, DataObjectWrapper>> checkedAssociations = checkAssociationsOfReadDataObjects(transition, readDataObjects, readContext);
		
		//If either new assocs are created or old assocs are checked, we need arcs from and to the assoc place
		if(!checkedAssociations.isEmpty() || !associationsToWrite.isEmpty() || !stateChangesToPerform.isEmpty()) {
			// Create reading arcs
			String readAnnotation = "assoc";
			elementPage.createArcFrom(parent.getAssociationsPlace(), transition, readAnnotation);
			if(!hasCreatedArcFromAssocPlace) {
				parent.createArc(parent.getAssociationsPlace(), node());
				hasCreatedArcFromAssocPlace = true;
			}
			
			//Create write back arcs; if new assocs are create, write the union back; if assocs are checked, they already exist
			String writeAnnotation = writeToAssociationArcInscription(associationsToWrite, readDataObjects, writtenDataObjects, readContext, writeContext);
			elementPage.createArcTo(parent.getAssociationsPlace(), transition, writeAnnotation);
			if(!hasCreatedArcToAssocPlace) {
				parent.createArc(node(), parent.getAssociationsPlace());
				hasCreatedArcToAssocPlace = true;
			}
			
				
			if(!associationsToWrite.isEmpty()) {
				//checkedAssociations.forEach(associationsToWrite::remove);
				associationsToWrite.forEach(createdAssoc -> {
					checkUpperBoundsOfCreatedAssoc(createdAssoc, readDataObjects, transition);
					checkLowerBoundForCreatedObject(createdAssoc, readDataObjects, readContext, transition);
				});
			}
			

		}
    }
    
    private String writeToAssociationArcInscription(Set<Pair<DataObjectWrapper, DataObjectWrapper>> associationsToWrite, Set<DataObjectWrapper> readDataObjects, Set<DataObjectWrapper> writtenDataObjects, Map<DataObjectWrapper, List<StatefulDataAssociation<DataInputAssociation, DataObjectReference>>> readContext, Map<DataObjectWrapper, List<StatefulDataAssociation<DataOutputAssociation, DataObjectReference>>> writeContext) {
    	return "assoc" + associationsToWrite.stream()
			.map(assoc -> {
				Optional<DataObjectWrapper> collectionDataObject = Stream.of(assoc.first, assoc.second)
						.filter(dataObject -> readDataObjects.contains(dataObject) && readContext.get(dataObject).stream().anyMatch(StatefulDataAssociation::isCollection))
						.findAny();
				boolean listReadSingleObjectCreate = false;
				if (collectionDataObject.isPresent()) {
					listReadSingleObjectCreate = Stream.of(assoc.first, assoc.second)
							.filter(dataObject -> readDataObjects.contains(dataObject) && readContext.get(dataObject).stream().anyMatch(StatefulDataAssociation::isCollection))
							.allMatch(dataObject -> writtenDataObjects.contains(dataObject) && !writeContext.get(dataObject).stream().allMatch(StatefulDataAssociation::isCollection));
				}
				if(!collectionDataObject.isPresent() || listReadSingleObjectCreate) {
					return "^^["+Stream.of(assoc.first, assoc.second).map(DataObjectWrapper::dataElementId).sorted().collect(Collectors.toList()).toString()+"]";
				} else {
					DataObjectWrapper identifier = parent.getDataObjectCollectionIdentifier(element, collectionDataObject.get());
					DataObjectWrapper other = (DataObjectWrapper) assoc.otherElement(collectionDataObject.get());
					//TODO does not sort alphabetically
					return "^^(associateWithList "+other.dataElementId()+" "+collectionDataObject.get().getNormalizedName()+" "+identifier.dataElementId()+" assoc)";
				}
			})
			.distinct()
			.collect(Collectors.joining());
    }
    
    private void checkUpperBoundsOfCreatedAssoc(Pair<DataObjectWrapper, DataObjectWrapper> createdAssoc, Set<DataObjectWrapper> readDataObjects, Transition transition) {
		Association dataModelAssoc = parent.getDataModel().getAssociation(createdAssoc.first.getNormalizedName(), createdAssoc.second.getNormalizedName()).get();
		//Create guards for: Cannot create data object if this would violate upper bounds
		Stream.of(createdAssoc.first, createdAssoc.second).forEach(dataObject -> {
			AssociationEnd end = dataModelAssoc.getEnd(dataObject.getNormalizedName());
			int limit = end.getUpperBound();
			DataObjectWrapper otherObject = (DataObjectWrapper) createdAssoc.otherElement(dataObject);
			
			if(limit > 1 && limit != AssociationEnd.UNLIMITED && readDataObjects.contains(otherObject)) {//If the other object is not read, it is just created - and then no bound that is 1 or higher will be violated
				String newGuard = "(enforceUpperBound "+otherObject.dataElementId()+" "+dataObject.namePrefix()+" assoc "+limit+")";
				addGuardCondition(transition, newGuard);
			}
		});
    }
    
    private void checkLowerBoundForCreatedObject(Pair<DataObjectWrapper, DataObjectWrapper> createdAssoc, Set<DataObjectWrapper> readDataObjects, Map<DataObjectWrapper, List<StatefulDataAssociation<DataInputAssociation, DataObjectReference>>> readContext, Transition transition) {
		//Create guards for: Cannot create data object if lower bounds of read list data object are not given
		//When a collection is read, an identifier determines which elements exactly are read, so the check boils down to checking the number of identifier associations
		Association dataModelAssoc = parent.getDataModel().getAssociation(createdAssoc.first.getNormalizedName(), createdAssoc.second.getNormalizedName()).get();
		Stream.of(createdAssoc.first, createdAssoc.second)
			.filter(dataObject -> readDataObjects.contains(dataObject) && readContext.get(dataObject).stream().anyMatch(StatefulDataAssociation::isCollection))
			.forEach(collectionDataObject -> {
				DataObjectWrapper singleObject = (DataObjectWrapper) createdAssoc.otherElement(collectionDataObject);
				/*Try to use the same identifier that is used for the list*/
				DataObjectWrapper identifier = parent.getDataObjectCollectionIdentifier(element, collectionDataObject);
//				if(!parent.getDataModel().getAssociation(identifier.getNormalizedName(), singleObject.getNormalizedName())
//						.map(linkingAssoc -> linkingAssoc.first.getUpperBound() <= 1 && linkingAssoc.second.getUpperBound() <= 1)
//						.orElse(false)) throw new ModelValidationException("Identifier data object "+identifier.getNormalizedName()+" for list data object "+collectionDataObject.getNormalizedName()+" is not associated 1 to 1 with "+singleObject.getNormalizedName()+" in activity "+elementName(element));

				int lowerBound = dataModelAssoc.getEnd(collectionDataObject.getNormalizedName()).getLowerBound();
				//TODO new guard
				//String newGuard = "((length " + collectionDataObject.dataElementList() + ") < " + lowerBound + ") (*requirements "+ singleObject.getNormalizedName() + "*)";
				String newGuard = "(enforceLowerBound "+identifier.dataElementId()+" "+collectionDataObject.namePrefix()+" assoc "+lowerBound+")";
				addGuardCondition(transition, newGuard);
		});
    }
    
    private Set<Pair<DataObjectWrapper, DataObjectWrapper>> determineAssociationsToWrite(Set<DataObjectWrapper> readDataObjects, Set<DataObjectWrapper> writtenDataObjects, Map<DataObjectWrapper, List<StatefulDataAssociation<DataInputAssociation, DataObjectReference>>> readContext, Map<DataObjectWrapper, List<StatefulDataAssociation<DataOutputAssociation, DataObjectReference>>> writeContext) {
    	Set<Pair<DataObjectWrapper, DataObjectWrapper>> associationsToWrite = new HashSet<>() {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean add(Pair<DataObjectWrapper, DataObjectWrapper> e) {
				if(!contains(e) && !contains(e.reversed())) return super.add(e);
				else return false;
			};
			
			@Override
			public boolean remove(Object e) {
				return super.remove(e) || super.remove(((Pair<?,?>)e).reversed());
			};
		};
		
		//New associations are created when an object is created and ...
		//    if another object is written as non-collection
		// OR if another object was read as non-collection
		// OR if another object was read as collection
		for(DataObjectWrapper writtenObject : writtenDataObjects) {
			if(
					(!readContext.containsKey(writtenObject) || readContext.get(writtenObject).stream().allMatch(StatefulDataAssociation::isCollection))
					&& !writeContext.get(writtenObject).stream().allMatch(StatefulDataAssociation::isCollection)) {
				for(DataObjectWrapper otherWrittenObject : writtenDataObjects) {
					if(!writtenObject.equals(otherWrittenObject) && parent.getDataModel().isAssociated(writtenObject.getNormalizedName(), otherWrittenObject.getNormalizedName())
							&& !writeContext.get(otherWrittenObject).stream().allMatch(StatefulDataAssociation::isCollection)) {
						associationsToWrite.add(new Pair<>(writtenObject, otherWrittenObject));
					}
				}
				
				for(DataObjectWrapper readObject : readDataObjects) {
					if(!writtenObject.equals(readObject) && parent.getDataModel().isAssociated(writtenObject.getNormalizedName(), readObject.getNormalizedName())) {
						associationsToWrite.add(new Pair<>(writtenObject, readObject));
					}
				}
			}
		}
		return associationsToWrite;
    }
    

    private Set<StateChange> determineStateChanges(Map<DataObjectWrapper, List<StatefulDataAssociation<DataInputAssociation, DataObjectReference>>> readContext, Map<DataObjectWrapper, List<StatefulDataAssociation<DataOutputAssociation, DataObjectReference>>> writeContext) {
    	Set<StateChange> stateChangesToPerform = new HashSet<>();
    	readContext.values().stream().flatMap(List::stream).forEach(input -> {
        	writeContext.values().stream().flatMap(List::stream).forEach(output -> {
        		if(input.equalsDataElementAndCollection(output) && !input.getStateName().equals(output.getStateName())) {
        			stateChangesToPerform.add(new StateChange(input, output));
        		}
        	});
    	});
    	return stateChangesToPerform;
    }
    
    private void addGuardsForStateChanges(Set<StateChange> stateChangesToPerform, Transition transition, Set<DataObjectWrapper> readDataObjects, Set<DataObjectWrapper> writtenDataObjects, Map<DataObjectWrapper, List<StatefulDataAssociation<DataInputAssociation, DataObjectReference>>> readContext, Map<DataObjectWrapper, List<StatefulDataAssociation<DataOutputAssociation, DataObjectReference>>> writeContext) {
		
    	for (StateChange stateChange : stateChangesToPerform) {
			String inputState = stateChange.first.getStateName();
			String outputState = stateChange.second.getStateName();
			
			ObjectLifeCycle olc = parent.olcFor(stateChange.dataObject());
			ObjectLifeCycle.State istate = olc.getState(inputState).get();
			if (istate.getUpdateableAssociations().isEmpty()) continue;
			ObjectLifeCycle.State ostate = olc.getState(outputState).get();
			// For each updateable association that is in input state but not in output state
			for (AssociationEnd assocEnd : istate.getUpdateableAssociations()) {
				if (!ostate.getUpdateableAssociations().contains(assocEnd)) {

					
					// TODO: generalize: look at all possible successor states
					
					//If the second object is just created, then an additional assoc is created, so the checked goal lower bound must be lower
					int goalLowerBound = assocEnd.getGoalLowerBound();
					int lowerBound = assocEnd.getLowerBound();
					if (writtenDataObjects.stream().anyMatch(o -> o.getNormalizedName().equals(normalizeElementName(assocEnd.getDataObject()))) &&
							readDataObjects.stream().noneMatch(o -> o.getNormalizedName().equals(normalizeElementName(assocEnd.getDataObject())))) {
						goalLowerBound--;
					}
					
					if (goalLowerBound > lowerBound) {
						String newGuard;
						if (stateChange.first.isCollection()) {
							newGuard = "(List.all (fn oId => (enforceLowerBound oId " + normalizeElementName(assocEnd.getDataObject()) + " assoc " + goalLowerBound + ")) (List.map (fn obj => #id obj) " + stateChange.dataObject().dataElementList() + ")) "+GOAL_CARDINALITY;
						} else {
							newGuard = "(enforceLowerBound " + stateChange.dataObject().dataElementId() + " " + normalizeElementName(assocEnd.getDataObject()) + " assoc " + goalLowerBound + ") "+GOAL_CARDINALITY;
						}
						System.out.println(newGuard);
						addGuardCondition(transition, newGuard);
					}
				}
			}
		}
    }
    
    private Set<Pair<DataObjectWrapper, DataObjectWrapper>> checkAssociationsOfReadDataObjects(Transition transition, Set<DataObjectWrapper> readDataObjects, Map<DataObjectWrapper, List<StatefulDataAssociation<DataInputAssociation, DataObjectReference>>> readContext) {
    	Set<Pair<DataObjectWrapper, DataObjectWrapper>> associationsToCheck = new HashSet<>();
		
		for(DataObjectWrapper readObject : readDataObjects) {
			for(DataObjectWrapper otherReadObject : readDataObjects) {
				if(readObject.compareTo(otherReadObject) < 0 && parent.getDataModel().isAssociated(readObject.getNormalizedName(), otherReadObject.getNormalizedName())) {
					associationsToCheck.add(new Pair<>(readObject, otherReadObject));
				}
			}
		}
		
		//TODO checking of assocs of collections is done elsewhere (by checking lower bounds), could be brought together
		Set<Pair<DataObjectWrapper, DataObjectWrapper>> nonCollectionAssocs = associationsToCheck.stream()
				.filter(assoc -> Stream.of(assoc.first, assoc.second).allMatch(dataObject -> !readContext.get(dataObject).stream().allMatch(StatefulDataAssociation::isCollection)))
				.collect(Collectors.toSet());
		if(!nonCollectionAssocs.isEmpty()) {
			String guard = "contains assoc "+ nonCollectionAssocs.stream()
				.map(assoc -> Stream.of(assoc.first, assoc.second).map(DataObjectWrapper::dataElementId).sorted().collect(Collectors.toList()).toString())
				.distinct()
				.collect(Collectors.toList())
				.toString();
			addGuardCondition(transition, guard);
		}
		
		//return nonCollectionAssocs;
		return associationsToCheck;
    }
}
