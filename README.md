# README #
JOB_CONFIG_ANALYZER is a Jenkins project that parses the configuration of other Jenkins jobs, and generates a detailed report of the configuration components. The report summarizes the plugins, parameters, builders, publishers and wrappers of the job. Each build also extracts configuration embedded script code into separate files, and archives the job configuration and other job artifacts (properties, scriptlets, commands etc). The archived artifacts are 'finger-printed' and are useful for versioning the job configuration.

## Motivation
The configuration complexity of Jenkins freestyle jobs has increased, especially as we are now introducing dynamic parameter 
behavior (using the [Active Choices](https://wiki.jenkins-ci.org/display/JENKINS/Active+Choices+Plugin) and other similar Jenkins plugins).
There is a need for a quick and concise way to review and access project parameters, builders, publishers, and Groovy code and plugin dependencies.
This utility Jenkins job allows you to select one of the jobs on your Jenkins server, analyze its configuration, and **create a concise report of the job's main elements** (parameters, scm, builders, publishers, build-wrappers) as well as the Groovy code, scripts and plugin dependencies.
By examining a JOB_CONFIG_ANALYZER build report you can immediately **visualize and access the target project's**:
1. parameters
2. SCM
3. builders
4. publishers
5. scriptlets, and Groovy code used
6. plugins used 
7. order and sequence of these components

### What is this repository for? ###
The repository provides an archive of the key artifacts required to setup (or update) the job on a Jenkins server. Artifacts include:

* Job configuration, and job-specific properties and scripts
* Shared Groovy Scriptlets
* Shared External scripts

### Job Dependencies ###
Templates for the generation of the README.md and build.gradle files. These are located at:
 * $JENKINS_HOME/userContent/templates/README.mdTemplate
 * $JENKINS_HOME/userContent/template/build.gradleTemplate

### Deployment Instructions ###

* Clone the repository ```git clone https://github.com/imoutsatsos/Jenkins-JOB_CONFIG_ANALYZER.git```
* Deploy artifacts with [gradle](https://gradle.org/)
	* Open console in repository folder and execute command ```gradle deploy```
	* Deployment creates a **backup of all original files** (if they exist) in **Jenkins-JOB_CONFIG_ANALYZER/backup** folder
	* Project configuration, scripts and properties are deployed to **$JENKINS_HOME/jobs/JOB_CONFIG_ANALYZER** folder
	* Scriptlets are deployed to **$JENKINS_HOME/scriptlet/scripts** folder

* Review project plugins (shown below with latest version tested) and install as needed
 	* [scriptler@3.1](https://plugins.jenkins.io/scriptler)
  	* [uno-choice@2.3](https://plugins.jenkins.io/uno-choice)
  	* [summary_report@1.15](https://plugins.jenkins.io/summary_report)
  	* [ws-cleanup@0.38](https://plugins.jenkins.io/ws-cleanup)
  	* [build-name-setter@2.1.0](https://plugins.jenkins.io/build-name-setter)
 

### Build Parameters ###

The user needs to select or provide the following parameters on the build form

 * PROJECT_NAME : Name of project to parse
 * BUILD_DESCRIPTION: A description that appears in build history
 * PROJECT_HISTORY : Links to previous JOB_CONFIG_ANALYZER builds for the PROJECT_NAME

### How do I build this job? ###

Carefully review and follow directions and guidance provided in parameter descriptions. 

A top to down form configuration of all required parameters is typically required before clicking on the 'Build' button

## There is a lot more ....
Please see the documentation provided with Jenkins-JobConfigurationAnalyzer at: https://github.com/imoutsatsos/Jenkins-JobConfigurationAnalyzer
Also, please pay attention to some of the listed limitations!

### Who do I talk to? ###

* Ioannis K. Moutsatsos
