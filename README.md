# SANDGenerator

A Java-based tool that generates Word documents from configuration files and extracts table data from XML files.

## Table of Contents
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Usage](#usage)
- [Technical Overview](#technical-overview)
- [License](#license)

##  Getting Started
### Prerequisites
  1. Java Development Kit (JDK) version 8 or later (https://www.oracle.com/java/technologies/downloads/)
  2. Apache POI library (https://poi.apache.org/)

###  Installation
  1. Download the latest SANDGenerator.jar from [releases or project repository]
  2. Place the config.properties file in the same directory as the JAR file.

##  Configuration
  1. Edit the config.properties file to adjust the following parameters:

    mode: Set the operational mode (e.g., test or production)
    template.folder: Path to the folder containing your configuration template files.
    component.folder: Path to the folder containing component XML files.
    output.folder: Path to the folder where the generated Word documents will be saved.
  2. Configuration Example (config.properties):
```properties
Properties

mode=test
template.folder=C:\Path\To\Templates
component.folder=C:\Path\To\Component\XMLs
output.folder=C:\Output
```

##  Usage
  1. Navigate to the directory containing the SANDGenerator.jar using the command line.
  2. Execute the following command:
```shell
Bash

java -jar SANDGenerator.jar
```

##  Technical Overview
- The SANDGenerator class is the primary entry point, responsible for:
  - Loading configuration from the config.properties file.
  - Finding the configuration files in the template folder.
  - Processing the XML component files
  - Generating a Word document for each configuration file using the Apache POI library.
  - Adding tables and content into the relevant sections of the Word document.

- Key XML Structures: The tool primarily processes the following XML node elements:
1. Multiple Tables (<tab>):  Indicates the creation of separate tables within the Word document. Example:
  ```XML
  XML
  
  <tab name="Traditional Chinese">...</tab>
  <tab name="Simplified Chinese">...</tab>
  ```
2. Inline Components with Tabs (<inline> within <tab>): ** Represents components that need to be rendered across multiple language tabs
   (e.g., English, Traditional Chinese, Simplified Chinese). Example:
```XML
XML

<tab name="English">
   <inline command="data_comps.ipl">...</inline>
</tab>
<tab name="Traditional Chinese">
   <inline command="data_comps.ipl">...</inline>
</tab>
```

3. Containers (<container>): Denotes a grouping of elements within a table and may contain nested elements.
 > Note that container elements do not directly represent tabs.
> 
## License
This project is licensed under the MIT License: https://opensource.org/licenses/MIT - see the LICENSE file for details.
