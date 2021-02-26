package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.allCombinationsOf;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.elementName;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.getSource;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.getTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
import org.camunda.bpm.model.bpmn.instance.IoSpecification;
import org.camunda.bpm.model.bpmn.instance.OutputSet;
import org.cpntools.accesscpn.model.Arc;
import org.cpntools.accesscpn.model.Transition;

import de.uni_potsdam.hpi.bpt.fcm2cpn.TransputSetWrapper.InputSetWrapper;
import de.uni_potsdam.hpi.bpt.fcm2cpn.TransputSetWrapper.OutputSetWrapper;
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
    	int transputSetIndex = 0;
        InputSetWrapper inputSet = transputSet.first;
        OutputSetWrapper outputSet = transputSet.second;

        Transition subpageTransition = elementPage.createTransition(elementName(element) + "_" + transputSetIndex);
        IOSetCompiler ioSetCompiler = new IOSetCompiler(this, outputSet, inputSet, subpageTransition);
        ioSetCompiler.compile();
        
        inputSet.forEach(input -> inputtingTransitions.get(input).add(subpageTransition));
        outputSet.forEach(output -> outputtingTransitions.get(output).add(subpageTransition));  
        transputSetIndex++;
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
