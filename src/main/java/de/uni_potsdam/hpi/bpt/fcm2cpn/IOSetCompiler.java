package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.addGuardCondition;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.normalizeElementName;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.cpntools.accesscpn.model.Transition;

import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.Association;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.AssociationEnd;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.DataModel;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.ObjectLifeCycle;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataelements.DataObjectWrapper;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.DataObjectIOSet.StateChange;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.DataObjectWrapperIOSet;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Pair;

public class IOSetCompiler {
	
	private final ActivityCompiler parent;
	private final Transition transition;
	
	private DataObjectWrapperIOSet ioSet;
	
	/** Comment to show that lower bound check is goal cardinality check.*/
	public static final String GOAL_CARDINALITY_COMMENT = "(*goal cardinality*)";
	
	public IOSetCompiler(ActivityCompiler parent, DataObjectWrapperIOSet ioSet, Transition transition) {
		this.parent = parent;
		this.ioSet = ioSet;
		this.transition = transition;
	}
	

	public void compile() {
		parent.attachObjectCreationCounters(transition, dataObjectsThat(ioSet::creates));
        parent.createCreationRegistrationArcs(transition, dataObjectsThat(ioSet::creates));
        associateDataObjects();
	}

    
    private void associateDataObjects() {

    	Set<Pair<DataObjectWrapper, DataObjectWrapper>> associationsToWrite = determineAssociationsToWrite();    	
    	Set<StateChange> stateChangesToPerform = ioSet.stateChanges();

		// Add guards for goal cardinalities
		addGuardsForStateChanges(stateChangesToPerform);
		
		Set<Pair<DataObjectWrapper, DataObjectWrapper>> checkedAssociations = checkAssociationsOfReadDataObjects();
		
		//If either new assocs are created or old assocs are checked, we need arcs from and to the assoc place
		if(!checkedAssociations.isEmpty() || !associationsToWrite.isEmpty() || !stateChangesToPerform.isEmpty()) {
			// Create reading arcs
			String readAnnotation = "assoc";
			parent.createAssociationReadArc(transition, readAnnotation);
			
			//Create write back arcs; if new assocs are create, write the union back; if assocs are checked, they already exist
			String writeAnnotation = writeToAssociationArcInscription(associationsToWrite);
			parent.createAssociationWriteArc(transition, writeAnnotation);
			
				
			if(!associationsToWrite.isEmpty()) {
				//checkedAssociations.forEach(associationsToWrite::remove);
				associationsToWrite.forEach(createdAssoc -> {
					checkUpperBoundsOfCreatedAssoc(createdAssoc);
					checkLowerBoundForCreatedObject(createdAssoc);
				});
			}
			

		}
    }
    
    private String writeToAssociationArcInscription(Set<Pair<DataObjectWrapper, DataObjectWrapper>> associationsToWrite) {
    	return "assoc" + associationsToWrite.stream()
			.map(assoc -> {
				Optional<DataObjectWrapper> collectionDataObject = Stream.of(assoc.first, assoc.second)
						.filter(dataObject -> ioSet.readsAsCollection(dataObject))
						.findAny();
				boolean listReadSingleObjectCreate = false;
				if (collectionDataObject.isPresent()) {
					listReadSingleObjectCreate = Stream.of(assoc.first, assoc.second)
							.filter(dataObject -> ioSet.readsAsCollection(dataObject))
							.allMatch(dataObject -> ioSet.writesAsNonCollection(dataObject));
				}
				if(!collectionDataObject.isPresent() || listReadSingleObjectCreate) {
					return "^^["+Stream.of(assoc.first, assoc.second).map(DataObjectWrapper::dataElementId).sorted().collect(Collectors.toList()).toString()+"]";
				} else {
					DataObjectWrapper identifier = getDataObjectCollectionIdentifier(collectionDataObject.get());
					DataObjectWrapper other = assoc.otherElement(collectionDataObject.get());
					//TODO does not sort alphabetically
					return "^^(associateWithList "+other.dataElementId()+" "+collectionDataObject.get().getNormalizedName()+" "+identifier.dataElementId()+" assoc)";
				}
			})
			.distinct()
			.collect(Collectors.joining());
    }

	private void checkUpperBoundsOfCreatedAssoc(Pair<DataObjectWrapper, DataObjectWrapper> createdAssoc) {
		Association dataModelAssoc = getDataModel().getAssociation(createdAssoc.first.getNormalizedName(), createdAssoc.second.getNormalizedName()).get();
		//Create guards for: Cannot create data object if this would violate upper bounds
		Stream.of(createdAssoc.first, createdAssoc.second).forEach(dataObject -> {
			AssociationEnd end = dataModelAssoc.getEnd(dataObject.getNormalizedName());
			int limit = end.getUpperBound();
			DataObjectWrapper otherObject = createdAssoc.otherElement(dataObject);
			
			if(limit > 1 && limit != AssociationEnd.UNLIMITED && ioSet.reads(otherObject)) {//If the other object is not read, it is just created - and then no bound that is 1 or higher will be violated
				String newGuard = "(enforceUpperBound "+otherObject.dataElementId()+" "+dataObject.namePrefix()+" assoc "+limit+")";
				addGuardCondition(transition, newGuard);
			}
		});
    }
    
    private void checkLowerBoundForCreatedObject(Pair<DataObjectWrapper, DataObjectWrapper> createdAssoc) {
		//Create guards for: Cannot create data object if lower bounds of read list data object are not given
		//When a collection is read, an identifier determines which elements exactly are read, so the check boils down to checking the number of identifier associations
		Association dataModelAssoc = getDataModel().getAssociation(createdAssoc.first.getNormalizedName(), createdAssoc.second.getNormalizedName()).get();
		Stream.of(createdAssoc.first, createdAssoc.second)
			.filter(dataObject -> ioSet.readsAsCollection(dataObject))
			.forEach(collectionDataObject -> {
				/*Use the same identifier that is used for the list*/
				DataObjectWrapper identifier = getDataObjectCollectionIdentifier(collectionDataObject);
				int lowerBound = dataModelAssoc.getEnd(collectionDataObject.getNormalizedName()).getLowerBound();
				String newGuard = "(enforceLowerBound "+identifier.dataElementId()+" "+collectionDataObject.namePrefix()+" assoc "+lowerBound+")";
				addGuardCondition(transition, newGuard);
		});
    }
    
    private Set<Pair<DataObjectWrapper, DataObjectWrapper>> determineAssociationsToWrite() {
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
		for(DataObjectWrapper createdObject : dataObjectsThat(ioSet::creates)) {
			for(DataObjectWrapper otherWrittenObject : dataObjectsThat(ioSet::writesAsNonCollection)) {
				if(!createdObject.equals(otherWrittenObject) && getDataModel().isAssociated(createdObject.getNormalizedName(), otherWrittenObject.getNormalizedName())) {
					associationsToWrite.add(new Pair<>(createdObject, otherWrittenObject));
				}
			}
			
			for(DataObjectWrapper readObject : dataObjectsThat(ioSet::reads)) {
				if(!createdObject.equals(readObject) && getDataModel().isAssociated(createdObject.getNormalizedName(), readObject.getNormalizedName())) {
					associationsToWrite.add(new Pair<>(createdObject, readObject));
				}
			}
		}
		return associationsToWrite;
    }
    
    
    private void addGuardsForStateChanges(Set<StateChange> stateChangesToPerform) {
		
    	for (StateChange stateChange : stateChangesToPerform) {
			String inputState = stateChange.first.getStateName();
			String outputState = stateChange.second.getStateName();
			
			ObjectLifeCycle olc = olcFor(dataObject(stateChange));
			ObjectLifeCycle.State istate = olc.getState(inputState).get();
			if (istate.getUpdateableAssociations().isEmpty()) continue;
			ObjectLifeCycle.State ostate = olc.getState(outputState).get();
			// For each updateable association that is in input state but not in output state
			for (AssociationEnd assocEnd : istate.getUpdateableAssociations()) {
				if (!ostate.getUpdateableAssociations().contains(assocEnd)) {

					
					// TODO: generalize: look at all possible successor states
					
					//If the second object is just created, then an additional assoc is created, so the checked goal lower bound must be lower
					boolean associationIsCreated = dataObjectsThat(ioSet::writes).stream().anyMatch(o -> o.getNormalizedName().equals(normalizeElementName(assocEnd.getDataObject()))) &&
							dataObjectsThat(ioSet::reads).stream().noneMatch(o -> o.getNormalizedName().equals(normalizeElementName(assocEnd.getDataObject())));
					
					if (assocEnd.hasTightGoalLowerBound(associationIsCreated)) {
						int goalLowerBound = assocEnd.getGoalLowerBound(associationIsCreated);
						String newGuard;
						if (stateChange.first.isCollection()) {
							newGuard = "(List.all (fn oId => (enforceLowerBound oId " + normalizeElementName(assocEnd.getDataObject()) + " assoc " + goalLowerBound + ")) (List.map (fn obj => #id obj) " + dataObject(stateChange).dataElementList() + ")) "+GOAL_CARDINALITY_COMMENT;
						} else {
							newGuard = "(enforceLowerBound " + dataObject(stateChange).dataElementId() + " " + normalizeElementName(assocEnd.getDataObject()) + " assoc " + goalLowerBound + ") "+GOAL_CARDINALITY_COMMENT;
						}
						addGuardCondition(transition, newGuard);
					}
				}
			}
		}
    }

	private Set<Pair<DataObjectWrapper, DataObjectWrapper>> checkAssociationsOfReadDataObjects() {
    	Set<Pair<DataObjectWrapper, DataObjectWrapper>> associationsToCheck = new HashSet<>();
		
		for(DataObjectWrapper readObject : dataObjectsThat(ioSet::reads)) {
			for(DataObjectWrapper otherReadObject : dataObjectsThat(ioSet::reads)) {
				if(readObject.compareTo(otherReadObject) < 0 && getDataModel().isAssociated(readObject.getNormalizedName(), otherReadObject.getNormalizedName())) {
					associationsToCheck.add(new Pair<>(readObject, otherReadObject));
				}
			}
		}
		
		//TODO checking of assocs of collections is done elsewhere (by checking lower bounds), could be brought together
		Set<Pair<DataObjectWrapper, DataObjectWrapper>> nonCollectionAssocs = associationsToCheck.stream()
				.filter(assoc -> Stream.of(assoc.first, assoc.second).allMatch(ioSet::readsAsNonCollection))
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
    
	
	private DataObjectWrapper dataObject(StateChange stateChange) {
		assert parent.wrapperFor(stateChange.first).equals(parent.wrapperFor(stateChange.second));
		return (DataObjectWrapper) parent.wrapperFor(stateChange.first);
	}
	
	//TODO LOD violation
    private Set<DataObjectWrapper> dataObjectsThat(Predicate<DataObjectWrapper> predicate) {
    	return parent.parent.getDataObjects().stream().filter(predicate).collect(Collectors.toSet());
    }
    
    private DataModel getDataModel() {
    	return parent.parent.getDataModel();
    }
    
    private DataObjectWrapper getDataObjectCollectionIdentifier(DataObjectWrapper dataObjectWrapper) {
		return parent.parent.getDataObjectCollectionIdentifier(dataObjectWrapper, new HashSet<>(ioSet.first));
	}

    private ObjectLifeCycle olcFor(DataObjectWrapper dataObject) {
		return parent.parent.olcFor(dataObject);
	}
	

}
