package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.allCombinationsOf;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.elementName;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.getSource;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.getTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
import org.camunda.bpm.model.bpmn.instance.IoSpecification;
import org.camunda.bpm.model.bpmn.instance.OutputSet;
import org.cpntools.accesscpn.model.Arc;
import org.cpntools.accesscpn.model.Transition;

import de.uni_potsdam.hpi.bpt.fcm2cpn.dataelements.DataElementWrapper;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.DataObjectWrapperIOSet;
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
	
	private boolean accessesAssociationPlace = false;
	

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
        
        //Data Stores are (at least in Signavio) not part of input or output sets
        List<StatefulDataAssociation<DataInputAssociation, ?>> dataStoreInputs = element.getDataInputAssociations().stream()
			   .filter(assoc -> getSource(assoc) instanceof DataStoreReference)
			   .flatMap(Utils::splitDataAssociationByState)
			   .collect(Collectors.toList());

        List<StatefulDataAssociation<DataOutputAssociation, ?>> dataStoreOutputs = element.getDataOutputAssociations().stream()
 			   .filter(assoc -> getTarget(assoc) instanceof DataStoreReference)
 			   .flatMap(Utils::splitDataAssociationByState)
 			   .collect(Collectors.toList());
        
        // All possible combinations of input and output sets, either defined by io-specification or *all* possible combinations are used
        int transputSetIndex = 0;
        for (DataObjectWrapperIOSet ioSet : ioSets()) {
        	Transition ioSetTransition = compileIOSet(ioSet, transputSetIndex);
        	transputSetIndex++;
            dataStoreInputs.forEach(input -> inputtingTransitions.get(input).add(ioSetTransition));
            dataStoreOutputs.forEach(output -> outputtingTransitions.get(output).add(ioSetTransition));
        }
        
        createDataAssociationArcs(outputtingTransitions, inputtingTransitions);
	}
	
	private List<DataObjectWrapperIOSet> ioSets() {
		return ioSetsFromIoSpecification(element.getIoSpecification());
	}
	
    private List<DataObjectWrapperIOSet> ioSetsFromIoSpecification(IoSpecification ioSpecification) {
        List<DataObjectWrapperIOSet> transputSets = new ArrayList<>();
        
        // Output sets mapped to data assocs and split by "|" state shortcuts
    	Map<OutputSet, List<List<StatefulDataAssociation<DataOutputAssociation, DataObjectReference>>>> translatedOutputSets = ioSpecification.getOutputSets().stream().collect(Collectors.toMap(Function.identity(), outputSet -> {
            Map<Pair<DataElementWrapper<?,?>, Boolean>, List<StatefulDataAssociation<DataOutputAssociation, DataObjectReference>>> outputsPerObject = outputSet.getDataOutputRefs().stream()
            		.map(Utils::getAssociation)
            		.flatMap(Utils::splitDataAssociationByState)
            		.map(StatefulDataAssociation::asDataObjectReference).map(Optional::get)
            		.collect(Collectors.groupingBy(assoc -> new Pair<>(parent.wrapperFor(assoc), assoc.isCollection())));
            return allCombinationsOf(outputsPerObject.values());
        }));
    	
    	ioSpecification.getInputSets().forEach(inputSet -> {
            Map<Pair<DataElementWrapper<?,?>, Boolean>, List<StatefulDataAssociation<DataInputAssociation, DataObjectReference>>> inputsPerObject = inputSet.getDataInputs().stream()
            		.map(Utils::getAssociation)
            		.flatMap(Utils::splitDataAssociationByState)
            		.map(StatefulDataAssociation::asDataObjectReference).map(Optional::get)
            		.collect(Collectors.groupingBy(assoc -> new Pair<>(parent.wrapperFor(assoc), assoc.isCollection())));
            List<List<StatefulDataAssociation<DataInputAssociation, DataObjectReference>>> translatedInputSets = allCombinationsOf(inputsPerObject.values());
            for(OutputSet outputSet : inputSet.getOutputSets()) {
            	for(var translatedOutputSet : translatedOutputSets.get(outputSet)) {
                	for(var translatedInputSet : translatedInputSets) {
                		transputSets.add(new DataObjectWrapperIOSet(translatedInputSet, translatedOutputSet));
                	}
            	}
            }
        });
    
    	return transputSets;
    }
 
    
    private Transition compileIOSet(DataObjectWrapperIOSet ioSet, int transputSetIndex) {

        Transition subpageTransition = elementPage.createTransition(elementName(element) + "_" + transputSetIndex);
        IOSetCompiler ioSetCompiler = new IOSetCompiler(this, ioSet, subpageTransition);
        ioSetCompiler.compile();
        
        ioSet.first.forEach(input -> inputtingTransitions.get(input).add(subpageTransition));
        ioSet.second.forEach(output -> outputtingTransitions.get(output).add(subpageTransition));
        return subpageTransition;
    }
	

	public DataElementWrapper<?,?> wrapperFor(StatefulDataAssociation<?, ?> assoc) {
		return parent.wrapperFor(assoc);
	}
	
	public Arc createAssociationReadArc(Transition transition, String annotation) {
		assertAccessToAssociationPlace();
		return elementPage.createArcFrom(parent.getAssociationsPlace(), transition, annotation);
	}
	
	public Arc createAssociationWriteArc(Transition transition, String annotation) {
		assertAccessToAssociationPlace();
		return elementPage.createArcTo(parent.getAssociationsPlace(), transition, annotation);
	}
	
	private void assertAccessToAssociationPlace() {
		if(!accessesAssociationPlace) {
			parent.createArc(parent.getAssociationsPlace(), node());
			parent.createArc(node(), parent.getAssociationsPlace());
			accessesAssociationPlace = true;
		}
		
	}
}
