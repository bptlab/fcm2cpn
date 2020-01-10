package de.uni_potsdam.hpi.bpt.fcm2cpn;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.DataState;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.cpntools.accesscpn.model.ModelPrinter;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.PetriNet;
import org.cpntools.accesscpn.model.cpntypes.CPNEnum;
import org.cpntools.accesscpn.model.cpntypes.CPNRecord;
import org.cpntools.accesscpn.model.cpntypes.CpntypesFactory;
import org.cpntools.accesscpn.model.cpntypes.impl.CpntypesFactoryImpl;
import org.cpntools.accesscpn.model.exporter.DOMGenerator;
import org.cpntools.accesscpn.model.util.BuildCPNUtil;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


public class CompilerApp {

        public static void main(final String[] args) throws Exception {
            if (args.length != 1) {
                System.out.println("USAGE: fcm2cpn single_bpmn_file");
                System.exit(0);
            }
            BpmnModelInstance bpmn = loadBPMNFile(args[0]);
            Page page = translateBPMN2CPN(bpmn);

        }

    private static Page translateBPMN2CPN(BpmnModelInstance bpmn) throws Exception {
        System.out.print("Initalizing CPN model... ");
        BuildCPNUtil builder = new BuildCPNUtil();
        PetriNet petriNet = builder.createPetriNet();
        Page page = builder.addPage(petriNet, "new Page");
        builder.declareStandardColors(petriNet);
        System.out.println("DONE");
        ModelElementType objectType = bpmn.getModel().getType(DataObject.class);
        Collection<ModelElementInstance> objectInstances = bpmn.getModelElementsByType(objectType);
        Set<String> dataObjectNames = objectInstances.stream()
                .map(obj -> (DataObject)obj)
                .map(DataObject::getName)
                .map(s -> s.trim().toLowerCase())
                .collect(Collectors.toSet());
        dataObjectNames.forEach(s -> {
            builder.addPlace(page, s, "DATA_OBJECT");
        });
        ModelElementType dataStateType = bpmn.getModel().getType(DataState.class);
        Collection<ModelElementInstance> dataStates = bpmn.getModelElementsByType(dataStateType);
        CpntypesFactory cpntypesFactory = CpntypesFactoryImpl.init();
        CPNEnum cpnEnum = cpntypesFactory.createCPNEnum();
        CPNRecord dataObject = cpntypesFactory.createCPNRecord();
        dataStates.stream().map(state -> state.getAttributeValue("name").replaceAll("\\[", "").replaceAll("\\]", "").replaceAll("\\s","")).collect(Collectors.toSet())
                .forEach(s -> {
                    if (!s.contains("|")) {
                        cpnEnum.addValue(s.toUpperCase());
                    }
                });
        builder.declareColorSet(petriNet, "STATE", cpnEnum);
        dataObject.addValue("id", "STRING");
        dataObject.addValue("caseId", "STRING");
        dataObject.addValue("state", "STATE");
        ModelElementType activtiyType = bpmn.getModel().getType(Activity.class);
        Collection<ModelElementInstance> activities = bpmn.getModelElementsByType(activtiyType);
        activities.stream().map(a -> a.getAttributeValue("name")).forEach(s -> builder.addTransition(page, s));
        ModelElementType sequenceFlowType = bpmn.getModel().getType(SequenceFlow.class);
        Collection<ModelElementInstance> sequenceFlows = bpmn.getModelElementsByType(sequenceFlowType);
        sequenceFlows.forEach(sf -> {

        });
        builder.declareColorSet(petriNet, "DATA_OBJECT", dataObject);
        ModelPrinter.printModel(petriNet);
        DOMGenerator.export(petriNet, "C:\\Users\\stephan.haarmann\\Desktop\\test.cpn");
        return page;
    }

    private static BpmnModelInstance loadBPMNFile(String bpmnFileUri) {
        System.out.print("Load and parse BPMN file... ");
        File bpmnFile = new File(bpmnFileUri);
        System.out.println("DONE");
        return Bpmn.readModelFromFile(bpmnFile);
    }

    private static class ActivityNode {
            String name;
            String id;
            List<List<DataCondition>> preCondition;
            List<List<DataCondition>> postCondition;
    }

    private static class DataCondition {
            String dataObjectName;
            String dataObjectId;
            String dataObjectState;
            String dataObjectStateId;
    }
}
