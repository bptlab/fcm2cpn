# CPN-Based Semantics for Cross-Case Data in Case Management

This folder contains complementary files for the paper "CPN-Based Semantics for Cross-Case Data in Case management" submitted to [ICATPN](http://conf-2020.petrinet.net/).

## List of Files
* **Examples:**
  * `models/cat_fragments.bpmn` contains a BPMN file comprising all the fragments of the computer-aided translation example mentioned in the paper.
  * `models/classicalPN.cpn` contains a formalization of the computer-aided translation example using classical Petri Nets ([CPNtools file](https://cpntools.org)).
  * `models/coloredPN.cpn` contains a complete CPN formalization of the computer-aided translation example ([CPNtools file](https://cpntools.org))
  * `models/olc.pdf` depicts the object life cycles of the example case model
  * `models/domain_model.pdf` depicts the domain model of the example case model
  * `models/correlation/*.cpn` contains examples for different correlation mechanisms
* **Translator:**
  * `fcm2cpn.jar` is the binary of the translator
  * `src/*` contains the source files for the translator that translates a set of fragments to a CPN
  * `lib/*.jar` the [Access/CPN](http://cpntools.org/access-cpn/) libraries required for the prototype
  
## Prototype

### Usage

If you use the binary, you can run the program using the following command.
````bash
java -jar fcm2cpn.jar 
````
You are prompted to choose  a single BPMN file containing a set of fragments.
The program will save a CPN file in the current working directory.
The CPN has two hirachy levels: on the top-level all fragments and their connections are described, on the low-level a subnet for each activity is detailed.

### Assumptions

* We assume that the input is provided as a single BPMN file (you can, for example use the [Camunda Modeler](https://camunda.com/download/modeler/) or [Signavio](https://signavio.com))
* We assume that the fragments are object life cycle conform, so no additional input is required
* We assume that input- and output-sets are not modeled explicitly, but that all possible combinations are desired (e.g., an activity *translate text* may require a job object in state *accepted* or *started* and a translation object in state *required* or *in_progress*. This means the activity has four implicit input sets: job[accepted], translation[required] and job[started], translation[required] and job[accepted], translation[in_progress] and job[started], translation[in_progress] and)
* Furthermore, we assume that each data object can only be created by a single activity or event.

### Sources

All the sources are available in `src/main/*`, note that you have to add the Access/CPN libraries (`lib`) to your classpath in order to run/compile the tool.

### Binary

The binary `fcm2cpn.jar` containing all dependencies is available in the project's root folder.

### Dependencies

Please note, that the tool has dependencies, and that these dependencies may have different licenses. In the following we list the dependencies
* Camunda bpmn-model for parsing BPMN files. The dependency is linked via maven.
* Access/CPN to create CPNtools compatible CPNs. The dependency is linked as a set of external libraries (see `lib/`    )
* Eclipse EMF dependency for using Access/CPN

### Licence

*fcm2cpn* is a compiler, translating process fragments to CPNtools compatible Petri nets.
Copyright (C) 2020  Hasso Plattner Institute gGmbH, University of Potsdam

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.