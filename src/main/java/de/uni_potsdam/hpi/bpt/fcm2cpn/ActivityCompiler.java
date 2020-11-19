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

	private ObjectLifeCycle[] olcs;

	private int transputSetIndex = 0;

	private boolean hasCreatedArcToAssocPlace = false;
	private boolean hasCreatedArcFromAssocPlace = false;
	

	public ActivityCompiler(CompilerApp parent, Activity activity, ObjectLifeCycle[] olcs) {
		super(parent, activity);
		this.olcs = olcs;
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
		return Optional.ofNullable(element.getIoSpecification())
			.map(this::transputSetsFromIoSpecification)
			.orElseGet(this::defaultTransputSets);
	}
	
    private List<Pair<InputSetWrapper, OutputSetWrapper>> transputSetsFromIoSpecification(IoSpecification ioSpecification) {
        List<Pair<InputSetWrapper, OutputSetWrapper>> transputSets = new ArrayList<>();
        
        // Output sets mapped to data assocs and split by "|" state shortcuts
    	Map<OutputSet, List<OutputSetWrapper>> translatedOutputSets = ioSpecification.getOutputSets().stream().collect(Collectors.toMap(Function.identity(), outputSet -> {
            Map<DataElementWrapper<?,?>, List<StatefulDataAssociation<DataOutputAssociation, ?>>> outputsPerObject = outputSet.getDataOutputRefs().stream()
            		.map(Utils::getAssociation)
            		.flatMap(Utils::splitDataAssociationByState)
            		.collect(Collectors.groupingBy(parent::wrapperFor));
            return allCombinationsOf(outputsPerObject.values()).stream().map(OutputSetWrapper::new).collect(Collectors.toList());
        }));
    	
    	ioSpecification.getInputSets().forEach(inputSet -> {
            Map<DataElementWrapper<?,?>, List<StatefulDataAssociation<DataInputAssociation, ?>>> inputsPerObject = inputSet.getDataInputs().stream()
            		.map(Utils::getAssociation)
            		.flatMap(Utils::splitDataAssociationByState)
            		.collect(Collectors.groupingBy(parent::wrapperFor));
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
    
    private List<Pair<InputSetWrapper, OutputSetWrapper>> defaultTransputSets() {
    	List<Pair<InputSetWrapper, OutputSetWrapper>> transputSets = new ArrayList<>();
        // All read data elements with all states in that they are read
        Map<DataElementWrapper<?,?>, List<StatefulDataAssociation<DataInputAssociation, ?>>> inputsPerObject = element.getDataInputAssociations().stream()
        		.flatMap(Utils::splitDataAssociationByState)
        		.collect(Collectors.groupingBy(parent::wrapperFor));

        // All written data elements with all states in that they are written
        Map<DataElementWrapper<?,?>, List<StatefulDataAssociation<DataOutputAssociation, ?>>> outputsPerObject = element.getDataOutputAssociations().stream()
        		.flatMap(Utils::splitDataAssociationByState)
        		.collect(Collectors.groupingBy(parent::wrapperFor));
        List<InputSetWrapper> inputSets = allCombinationsOf(inputsPerObject.values()).stream().map(InputSetWrapper::new).collect(Collectors.toList());
        List<OutputSetWrapper> outputSets = allCombinationsOf(outputsPerObject.values()).stream().map(OutputSetWrapper::new).collect(Collectors.toList());
    	for (InputSetWrapper inputSet : inputSets) {
        	for (OutputSetWrapper outputSet : outputSets) {
        		transputSets.add(new Pair<>(inputSet, outputSet));
        	}
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

        Transition subpageTransition = elementPage.createTransition(element.getName() + "_" + transputSetIndex);
        attachObjectCreationCounters(subpageTransition, createdObjects);
        createCreationRegistrationArcs(subpageTransition, createdObjects);
        
        associateDataObjects(subpageTransition, readObjects, writtenObjects, readContext, writeContext);
    	
        inputSet.forEach(input -> inputtingTransitions.get(input).add(subpageTransition));
        outputSet.forEach(output -> outputtingTransitions.get(output).add(subpageTransition));
        
        transputSetIndex++;
    }
	
    private void associateDataObjects(Transition transition, Set<DataObjectWrapper> readDataObjects, Set<DataObjectWrapper> writtenDataObjects, Map<DataObjectWrapper, List<StatefulDataAssociation<DataInputAssociation, DataObjectReference>>> readContext, Map<DataObjectWrapper, List<StatefulDataAssociation<DataOutputAssociation, DataObjectReference>>> writeContext) {
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
    	Set<Pair<DataObjectWrapper, DataObjectWrapper>> stateChangesToPerform = new HashSet<>();
		
		//Associations are created two objects are written together or if one is read and the other one is written
		for(DataObjectWrapper writtenObject : writtenDataObjects) {
			for(DataObjectWrapper readObject : readDataObjects) {
				if(!writtenObject.equals(readObject) && parent.getDataModel().isAssociated(writtenObject.getNormalizedName(), readObject.getNormalizedName())) {
					associationsToWrite.add(new Pair<>(writtenObject, readObject));
				} else if (writtenObject.equals(readObject)) {
					stateChangesToPerform.add(new Pair<>(readObject, writtenObject));
				}
			}
			for(DataObjectWrapper otherWrittenObject : writtenDataObjects) {
				if(!writtenObject.equals(otherWrittenObject) && parent.getDataModel().isAssociated(writtenObject.getNormalizedName(), otherWrittenObject.getNormalizedName())) {
					associationsToWrite.add(new Pair<>(writtenObject, otherWrittenObject));
				}
			}
		}
		

		// Add guards for goal cardinalities
		if (!stateChangesToPerform.isEmpty()) {
			for (Pair<DataObjectWrapper, DataObjectWrapper> stateChange : stateChangesToPerform) {
				Set<String> inputStates = readContext.get(stateChange.first).stream()
					.map(obj -> obj.getStateName().orElse(null))
					.filter(Objects::nonNull)
					.collect(Collectors.toSet());
				Set<String> outputStates = writeContext.get(stateChange.second).stream()
						.map(obj -> obj.getStateName().orElse(null))
						.filter(Objects::nonNull)
						.collect(Collectors.toSet());
				assert(inputStates.size() <= 1);
				assert(outputStates.size() <= 1);
				Optional<ObjectLifeCycle> olc = Arrays.stream(olcs)
						.filter(o -> normalizeElementName(o.getClassName()).equals(stateChange.first.normalizedName))
						.findFirst();
				// if (olc.isEmpty()) continue;
				for (String inputState : inputStates) {
					ObjectLifeCycle.State istate = olc.get().getState(inputState).get();
					if (istate.getUpdateableAssociations().isEmpty()) continue;
					for (String outputState : outputStates) {
						ObjectLifeCycle.State ostate = olc.get().getState(outputState).get();
						for (AssociationEnd assocEnd : istate.getUpdateableAssociations()) {
							if (!ostate.getUpdateableAssociations().contains(assocEnd)) {
								// TODO: generalize: look at all possible successor states
								int goalLowerBound = assocEnd.getGoalLowerBound();
								String newGuard = "(enforceLowerBound "+stateChange.first.dataElementId()+" "+normalizeElementName(assocEnd.getDataObject())+" assoc "+goalLowerBound+")";
								addGuardCondition(transition, newGuard);
							}
						}
					}
				}
			}
			// Create reading arcs
			String readAnnotation = "assoc";
			elementPage.createArcFrom(parent.getAssociationsPlace(), transition, readAnnotation);
			if(!hasCreatedArcFromAssocPlace) {
				parent.createArc(parent.getAssociationsPlace(), node());
				hasCreatedArcFromAssocPlace = true;
			}
		}

		//If either new assocs are created or old assocs are checked, we need arcs from and to the assoc place
		Set<Pair<DataObjectWrapper, DataObjectWrapper>> checkedAssociations = checkAssociationsOfReadDataObjects(transition, readDataObjects, readContext);
		if(!checkedAssociations.isEmpty() || !associationsToWrite.isEmpty()) {
			// Create reading arcs
			if (stateChangesToPerform.isEmpty()) {
				String readAnnotation = "assoc";
				elementPage.createArcFrom(parent.getAssociationsPlace(), transition, readAnnotation);
				if (!hasCreatedArcFromAssocPlace) {
					parent.createArc(parent.getAssociationsPlace(), node());
					hasCreatedArcFromAssocPlace = true;
				}
			}
			//Create write back arcs; if new assocs are create, write the union back; if assocs are checked, they already exist
			String writeAnnotation = "assoc";
			checkedAssociations.forEach(associationsToWrite::remove);
			
			if(!associationsToWrite.isEmpty()) {
				
				writeAnnotation += associationsToWrite.stream()
					.map(pair -> {
						Optional<DataObjectWrapper> collectionDataObject = Stream.of(pair.first, pair.second)
								.filter(dataObject -> readDataObjects.contains(dataObject) && readContext.get(dataObject).stream().anyMatch(StatefulDataAssociation::isCollection))
								.findAny();
						if(!collectionDataObject.isPresent()) {
							return "^^["+Stream.of(pair.first, pair.second).map(DataObjectWrapper::dataElementId).sorted().collect(Collectors.toList()).toString()+"]";
						} else {
							DataObjectWrapper identifier = parent.getDataObjectCollectionIdentifier(element, collectionDataObject.get());
							DataObjectWrapper other = (DataObjectWrapper) pair.otherElement(collectionDataObject.get());
							//TODO does not sort alphabetically
							return "^^(associateWithList "+other.dataElementId()+" "+collectionDataObject.get().getNormalizedName()+" "+identifier.dataElementId()+" assoc)";
						}
					})
					.distinct()
					.collect(Collectors.joining());
				associationsToWrite.forEach(pair -> {
					Association assoc = parent.getDataModel().getAssociation(pair.first.getNormalizedName(), pair.second.getNormalizedName()).get();
					//Create guards for: Cannot create data object if this would violate upper bounds
					Stream.of(pair.first, pair.second).forEach(dataObject -> {
						AssociationEnd end = assoc.getEnd(dataObject.getNormalizedName());
						int limit = end.getUpperBound();
						DataObjectWrapper otherObject = (DataObjectWrapper) pair.otherElement(dataObject);
						
						if(limit > 1 && limit != AssociationEnd.UNLIMITED && readDataObjects.contains(otherObject)) {//If the other object is not read, it is just created - and then no bound that is 1 or higher will be violated
							String newGuard = "(enforceUpperBound "+otherObject.dataElementId()+" "+dataObject.namePrefix()+" assoc "+limit+")";
							addGuardCondition(transition, newGuard);
						}
					});
					
					//Create guards for: Cannot create data object if lower bounds of read list data object are not given
					Stream.of(pair.first, pair.second)
						.filter(dataObject -> readDataObjects.contains(dataObject) && readContext.get(dataObject).stream().anyMatch(StatefulDataAssociation::isCollection))
						.forEach(collectionDataObject -> {
							DataObjectWrapper singleObject = (DataObjectWrapper) pair.otherElement(collectionDataObject);
							/*Try to use the same identifier that is used for the list*/
							DataObjectWrapper identifier = parent.getDataObjectCollectionIdentifier(element, collectionDataObject);
							if(!parent.getDataModel().getAssociation(identifier.getNormalizedName(), singleObject.getNormalizedName())
									.map(linkingAssoc -> linkingAssoc.first.getUpperBound() <= 1 && linkingAssoc.second.getUpperBound() <= 1)
									.orElse(false)) throw new ModelValidationException("Identifier data object "+identifier.getNormalizedName()+" for list data object "+collectionDataObject.getNormalizedName()+" is not associated 1 to 1 with "+singleObject.getNormalizedName()+" in activity "+normalizeElementName(elementName(element)));

							int lowerBound = assoc.getEnd(collectionDataObject.getNormalizedName()).getLowerBound();
							String newGuard = "(enforceLowerBound "+identifier.dataElementId()+" "+collectionDataObject.namePrefix()+" assoc "+lowerBound+")";
							addGuardCondition(transition, newGuard);
					});
				});
				
				
			}
			elementPage.createArcTo(parent.getAssociationsPlace(), transition, writeAnnotation);
			if(!hasCreatedArcToAssocPlace) {
				parent.createArc(node(), parent.getAssociationsPlace());
				hasCreatedArcToAssocPlace = true;
			}
		} else if (!stateChangesToPerform.isEmpty()) {
			// Create writing arcs
			String writeAnnotation = "assoc";
			elementPage.createArcTo(parent.getAssociationsPlace(), transition, writeAnnotation);
			if(!hasCreatedArcToAssocPlace) {
				parent.createArc(node(), parent.getAssociationsPlace());
				hasCreatedArcToAssocPlace = true;
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
		
		//TODO checking of assocs of collections is done elswhere, could be brought together
		Set<Pair<DataObjectWrapper, DataObjectWrapper>> nonCollectionAssocs = associationsToCheck.stream()
				.filter(assoc -> Stream.of(assoc.first, assoc.second).allMatch(dataObject -> !readContext.get(dataObject).stream().allMatch(StatefulDataAssociation::isCollection)))
				.collect(Collectors.toSet());
		if(!nonCollectionAssocs.isEmpty()) {
			String guard = "contains assoc "+ nonCollectionAssocs.stream()
				.map(assoc -> Stream.of(assoc.first, assoc.second).map(DataObjectWrapper::dataElementId).sorted().collect(Collectors.toList()).toString())
				.distinct()
				.collect(Collectors.toList())
				.toString();
			transition.getCondition().setText("[" + guard + "]");
		}
		
		return associationsToCheck;
    }

    

	
	

}
