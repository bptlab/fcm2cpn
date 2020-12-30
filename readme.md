# Cross-Case Data Objects in Business Processes: Semantics and Analysis

This page lists complementary files for the paper "CPN-Based Semantics for Cross-Case Data in Case management" accepted for publication at [BPM Forum](https://congreso.us.es/bpm2020/).
All files except the binary are available in the [Github repository](https://github.com/bptlab/fcm2cpn).
The binary of the prototype can be downloaded [here](https://owncloud.hpi.de/s/EII5PnKSQEpu0PI).

## List of Files
* **Examples:**
  * `models/budget_processes.bpmn` contains a BPMN file comprising both the *office supply purchasing*. and *business trip booking* process used in the paper (modeled using Signavio).
  * `models/budget_processes_corrected.bpmn` contains a BPM file comprising the two example processes with boundary events (modeled using Signavio)
  * `models/coloredPN.cpn` contains a complete CPN formalization of the `budget_processes.bpmn` ([CPNtools file](https://cpntools.org))
  * `models/k-soundness.cpn` contains a formalization `budget_processes.bpmn` including extensions for checking *k-soundness* ([CPNtools file](https://cpntools.org)).
  * `models/k-soundnessCorrected.cpn` contains a corrected version of the process including extensions for checking *k-soundness*
  * `models/correlation/*.cpn` contains examples for different correlation mechanisms
* **Translator:**
  * `src/*` contains the source files for the translator that translates a set of fragments to a CPN
  * `lib/*.jar` the [Access/CPN](http://cpntools.org/access-cpn/) libraries required for the prototype
  
## Prototype

### Usage

If you use the [binary](https://owncloud.hpi.de/s/EII5PnKSQEpu0PI), you can run the program using the following command.
````bash
java -jar bpmn2cpn.jar 
````
You are prompted to choose  a single BPMN file containing one or multiple processes.
The program will save a CPN file in the current working directory.
The CPN has two hierarchy levels: on the top-level all processes and their connections are described, on the low-level a subnet for each activity is detailed.

### Assumptions

* We assume that the input is provided as a single BPMN file (you can, for example use the [Signavio](https://academic.signavio.com))
* We assume that, if no input- and output-sets are modeled explicitly, all possible combinations are desired
* We assume that data stores have a label `objectName[state]` or they are assumed to be in state `BLANK`.

### Sources

All the sources are available in `src/main/*`, note that you have to add the Access/CPN libraries (`lib`) to your classpath in order to run/compile the tool.

### Binary

The binary `bpmn2cpn.jar` containing all dependencies is available [here](https://owncloud.hpi.de/s/EII5PnKSQEpu0PI).

### Dependencies

Please note, that the tool has dependencies, and that these dependencies may have different licenses. In the following we list the dependencies
* Camunda bpmn-model for parsing BPMN files. The dependency is linked via maven.
* Access/CPN to create CPNtools compatible CPNs. The dependency is linked as a set of external libraries (see `lib/`)
* Eclipse EMF dependency for using Access/CPN

### Checking k-soundness

The example CPNs `k-soundness.cpn` and `k-soundnessCorrected.cpn` can be used to verify the k-soundness property.
To do so, the user must first load CPN-Tools state space tool, see (http://cpntools.org/2018/01/15/temporal-logic-for-state-spaces/)[http://cpntools.org/2018/01/15/temporal-logic-for-state-spaces/] for more information, and then reevaluate the `FindNodesViolatingKSoundness` function.

### License

*fcm2cpn* is a compiler, translating process fragments to CPNtools compatible Petri nets.
Copyright (C) 2020  Hasso Plattner Institute gGmbH, University of Potsdam

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
