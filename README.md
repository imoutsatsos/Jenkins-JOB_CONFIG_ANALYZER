# Jenkins-JOB_CONFIG_ANALYZER
A Jenkins utility job for analyzing and reporting the configuration of other freestyle jobs/projects

## Motivation
The configuration complexity of Jenkins freestyle jobs has increased, especially as we are now introducing dynamic parameter 
behavior (using the [Active Choices](https://wiki.jenkins-ci.org/display/JENKINS/Active+Choices+Plugin) and other similar Jenkins plugins).
There is a need for a quick and concise way to review and access project parameters, builders, publishers, and Groovy code and plugin dependencies.

## What can JOB_CONFIG_ANALYZER do?
This utility Jenkins job allows you to select one of the jobs on your Jenkins server, analyze its configuration, and **create a concise report of the job's main elements** (parameters, scm, builders, publishers, build-wrappers) as well as the Groovy code, scripts and plugin dependencies.

By examining a JOB_CONFIG_ANALYZER build report you can immediately **visualize and access the target project's**:

1. parameters
2. SCM
3. builders
4. publishers
5. scriptlets, and Groovy code used
6. plugins used 
7. order and sequence of these components

In addition, longitudinal builds of a project can be used as an annotated log/archive of the job's configuration changes. 
Since **the configuration file of the analyzed project/job is archived**, it can be used to compare or even revert back to a particular configuration version by simply re-deploying it to the project folder on the server.

## There is a lot more ....
Please see the documentation provided with Jenkins-JobConfigurationAnalyzer at: https://github.com/imoutsatsos/Jenkins-JobConfigurationAnalyzer

Also, please pay attention to some of the listed limitations!
