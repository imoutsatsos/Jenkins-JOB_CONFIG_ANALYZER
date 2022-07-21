/*** ORIGINAL 
BEGIN META {
 "name" : "jobConfigParser",
 "comment" : "Creates a summary of a Jenkins Project Configuration and writes report data files and groovy code artifacts",
 "parameters" : [ 'vJobName','vBuildNumber','vWorkspace'],
 "core": "1.596",
 "authors" : [
 { name : "Ioannis K. Moutsatsos" }
 ]
 } END META**/

/**
 The console output displayfrom this script can be further improved using the Collapsible Console Sections plugin
 You just need to use the generated Section Start/End keywords in the plugin.
 **/
/* parameters from job config */
jobName = vJobName
buildNumber=vBuildNumber //provides a unique key value in case we need to merge the generated CSV files
workspace = vWorkspace
scriptWorkspace="$workspace/embeddedScripts"
reportWorkspace="$workspace/buildReportData"
infoWorkspace="$workspace/jobInfo"

//make destination if not exists
if (!new File(workspace).exists()){
    new File(workspace).mkdirs()
}

//create required build folders
new File(scriptWorkspace).mkdir()
new File(reportWorkspace).mkdir()
new File(infoWorkspace).mkdir()

env = System.getenv()
JENKINS_HOME = env['JENKINS_HOME']
JENKINS_URL = jenkins.model.Jenkins.instance.getRootUrl()
thisProject=binding.variables['workspace'].replace('\\','/')split('/')[-1]
artifactPath="${JENKINS_URL}job/${thisProject}/${buildNumber}/artifact/"
projectPath ="$JENKINS_HOME/jobs/${jobName}/config.xml"
configList = [] //a list to maintain job element configurations gets reset for each element type
scriptletRunLink="${JENKINS_URL}scriptler/runScript?id="

//create global build environment variable
buildEnv=[jobName:jobName,buildNumber:buildNumber,workspace:workspace,scriptWorkspace:scriptWorkspace,reportWorkspace:reportWorkspace,infoWorkspace:infoWorkspace,
jenkinsHome:JENKINS_HOME,jenkinsUrl:JENKINS_URL,thisProject:thisProject,artifactPath:artifactPath,projectPath:projectPath,scriptletRunLink:scriptletRunLink,]


// read xml configuration file from server local path
println "\n---------------- REVIEWING: $jobName ----------------\n"
def configFile = new File(projectPath)
assert configFile.exists()

def returnMessage = ''
returnMessage = configFile.getText()
//parse the xml response
def project = new XmlParser().parseText(returnMessage)

//Print Project Elements
project.children().each {
    if (it.name() in ['actions', 'description', 'keepDependencies', 'canRoam', 'disabled', 'blockBuildWhenDownstreamBuilding', 'blockBuildWhenUpstreamBuilding', 'triggers', 'concurrentBuild']) {
        if (it.text().contains('=') || it.text().contains('<')) {
            println "\t\t ${it.name()}"//: ${script.text()}"
            it.text().split('\\n').each {
                println "\t\t\t $it"
            }

        } else {
            println "\t ${it.name()}: ${it.text()}"
        }
    }
}// end project builder

//Print SCM
println "\nCONFIGURATION_SCM_START"
project.scm[0].children().each {
    scmType = it.name()
    scmVesion = it.attributes()

}// end SCM
println "\nCONFIGURATION_SCM_END"

// Print Parameters
println "\nCONFIGURATION_PARAMETER_START"
configList=[] //reset for params
reportMap=[:]
serialId = 0
project.properties.'hudson.model.ParametersDefinitionProperty'[0].'parameterDefinitions'[0].children().each {
    elementConfiguration = [:] //generic map to maintain element configuration
    serialId++
    paramTypePartsList = it.name().split("\\.")
    def paramType = (paramTypePartsList.size() > 0) ? paramTypePartsList[paramTypePartsList.size() - 1] : paramTypePartsList
    it.children().each {
        if (it.name() in ['name']) {
            println "\nPARAMETER----------------${it.value()} ${paramType}----------------"
            elementConfiguration.put('version', it.parent().attribute('plugin'))
            elementConfiguration.put('class', it.parent().name())
            elementConfiguration.put('serialId', serialId)
            elementConfiguration.put('elementType', 'parameter')
            elementConfiguration.put('value', it.value().join(','))
            elementConfiguration.put('type', paramType)
        }

    }
    visitParamType(configList,it, elementConfiguration)

    paramNodeMap=[parentNode:'',parentNodeName:'']
    paramNodeMap.parentNode=it.parent().name()
    paramNodeMap.parentNodeName=it.parent().name()                    
    setParamNodeInfo(it, paramNodeMap,elementConfiguration, buildEnv) 

} //end parameters

paramFile = new File("$reportWorkspace/paramProps.csv")

paramFile << 'PROJECT_NAME,BUILD_NUMBER,SERIAL_ID,NAME,TYPE,SCRIPTLET,SCRIPTLET_LINK,CODE_LINK,INFO_LINK,REFERENCED_PARAMS,CLASS,PLUGIN\n'
configList.each {
    if (it.elementType == 'parameter') {
        reportMap = it.findAll { k, v -> k in ['serialId', 'name', 'type', 'scriptlerScriptId','scriptCode','infoFile','referencedParameters'] }
        //plugin = it.find { k, v -> (v as String).contains('plugin') }
        plugin = it.find { k, v -> k=='version' }
        thePluginClass=it.find { k, v -> k=='class' }
        if (plugin != null) {
            //println plugin
            pluginClass = thePluginClass.getValue() //plugin.getKey()
            //println pluginClass
            reportMap.put('pluginClass', pluginClass)
            pluginVers = plugin.getValue()
            reportMap.put('pluginVers', pluginVers)
        }
        //test//reportMap.name != '' ? reportMap.name : "UNNAMED_$elementConfiguration.serialId" as String
        //paramFile << "$jobName,$buildNumber,${reportMap.serialId},${reportMap.name},${reportMap.type},${reportMap.scriptlerScriptId!=null?reportMap.scriptlerScriptId:'--'},${reportMap.scriptlerScriptId!=null?scriptletRunLink+reportMap.scriptlerScriptId:'--'},${reportMap.scriptCode!=null?reportMap.scriptCode:'--'},${reportMap.referencedParameters?.replace(',', ';')},${reportMap.pluginClass},${reportMap?.pluginVers?.minus('plugin:')}\n"
        paramFile << "$jobName,$buildNumber,${reportMap.serialId},${reportMap.name != null ? reportMap.name : "UNNAMED_$reportMap.serialId" as String},${reportMap.type},${reportMap.scriptlerScriptId!=null?reportMap.scriptlerScriptId:'--'},${reportMap.scriptlerScriptId!=null?scriptletRunLink+reportMap.scriptlerScriptId:'--'},${reportMap.scriptCode!=null?reportMap.scriptCode:'--'},${reportMap.infoFile!=null?reportMap.infoFile:'--'},${reportMap.referencedParameters?.replace(',', ';')},${reportMap.pluginClass},${reportMap?.pluginVers?.minus('plugin:')}\n"
    }
}// end check for element type
println "\nCONFIGURATION_PARAMETER_END"

//Print Builders
println "\nCONFIGURATION_BUILDER_START"
configList=[] //reset for builders
serialId = 0

// project.builders[0].children().each {
project.builders[0].each {
    elementConfiguration = [:] //generic map to maintain element configuration
    serialId++
    //println "serialId increased: $serialId"
    builderTypePartsList = it.name().split("\\.")
    def builderType = (builderTypePartsList.size() > 0) ? builderTypePartsList[builderTypePartsList.size() - 1] : builderTypePartsList
    builderVesion = it.attribute('plugin')
    println "\nBUILDER----------------[${builderType}]----------------"
    elementConfiguration.put('version', it.attribute('plugin'))
    elementConfiguration.put('class', it.name())
    elementConfiguration.put('serialId', serialId)
    elementConfiguration.put('childSerialId', '0')
    elementConfiguration.put('elementType', 'builder')
    elementConfiguration.put('type', builderType)
	switch(builderType){    
    case"ConditionalBuilder":
	visitConditionalBuilder(configList,it, elementConfiguration, buildEnv)
	 break
	case"EnvInjectBuilder":
	visit(configList,it, elementConfiguration)
	break
	default:
    //  println 'elementConfiguration Before Visit:'+elementConfiguration.keySet()
	visit(configList,it, elementConfiguration)
	}
}// end project builder

buildFile = new File("$reportWorkspace/builderProps.csv")
buildFile << 'PROJECT_NAME,BUILD_NUMBER,SERIAL_ID,TYPE,SCRIPT_ID,CODE_LINK,INFO_LINK,PLUGIN\n'
configList.each {
    reportMap=[:]
    // println it.class
    reportList=getReportMap(it, buildEnv)
    reportList.each{reportMap->
    buildFile <<  "$jobName,$buildNumber,${reportMap.serialId},${reportMap.parentNode},${reportMap.scriptId},${reportMap.scriptCode},${reportMap.infoFile},${reportMap.parentPluginVersion}\n"
    }  
    
} //end check for element type

println "\nCONFIGURATION_BUILDER_END"

//Print Publishers
println "\nCONFIGURATION_PUBLISHER_START"
configList=[] //reset for publishers
reportMap=[:]
serialId = 0
project.publishers[0].children().each{
    elementConfiguration = [:] //generic map to maintain element configuration
    serialId++
    publisherTypePartsList=it.name().split("\\.")
    def publisherType=(publisherTypePartsList.size()>0)?publisherTypePartsList[publisherTypePartsList.size()-1]:publisherTypePartsList
    println "\nPUBLISHER----------------[${publisherType}]----------------"
    elementConfiguration.put('version', it.attribute('plugin'))
    elementConfiguration.put('class', it.name())
    elementConfiguration.put('serialId', serialId)
    elementConfiguration.put('elementType', 'publisher')
    elementConfiguration.put('type', publisherType)
    visit(configList,it, elementConfiguration)
}// end project builder
pubFile = new File("$reportWorkspace/publisherProps.csv")
pubFile << 'PROJECT_NAME,BUILD_NUMBER,SERIAL_ID,TYPE,INFO_LINK,CLASS,PLUGIN\n'
configList.each {
//    println it
    if (it.elementType=='publisher'){
        reportMap = it.findAll { k, v -> k in ['serialId', 'type'] }
		plugin = it.find { k, v -> k=='version' }
	        thePluginClass=it.find { k, v -> k=='class' }
	        if (plugin != null) {
	            pluginClass = thePluginClass.getValue()
	            pluginVers = plugin.getValue()
                reportMap.put('infoFile',it["${reportMap.type}_${reportMap.serialId}" as String].infoFile)
		        reportMap.put('pluginClass', pluginClass)
	            reportMap.put('pluginVers', pluginVers)
	        }
        pubFile << "$jobName,$buildNumber,${reportMap.serialId},${reportMap.type},${reportMap.infoFile},${reportMap.pluginClass},${reportMap?.pluginVers?.minus('plugin:')}\n"
 
        
    }
} //end check for element type

println "\nCONFIGURATION_PUBLISHER_END"

//Print BuildWrappers
println "\nCONFIGURATION_BUILD_WRAPPERS_START"
configList=[] //reset for build wrappers
serialId = 0
project.buildWrappers[0].children().each{
    elementConfiguration = [:] //generic map to maintain element configuration
    serialId++
    wrapperTypePartsList=it.name().split("\\.")
    def wrapperType=(wrapperTypePartsList.size()>0)?wrapperTypePartsList[wrapperTypePartsList.size()-1]:wrapperTypePartsList
    println "\nBUILD_WRAPPER----------------[${wrapperType}]----------------"
    elementConfiguration.put('version', it.attribute('plugin'))
    elementConfiguration.put('class', it.name())
    elementConfiguration.put('serialId', serialId)
    elementConfiguration.put('elementType', 'wrapper')
    elementConfiguration.put('type', wrapperType)
    visit(configList,it, elementConfiguration)
}// end project builder
wrapFile = new File("$reportWorkspace/wrapperProps.csv")
wrapFile << 'PROJECT_NAME,BUILD_NUMBER,SERIAL_ID,TYPE,INFO_LINK,CLASS,PLUGIN\n'
configList.each {
//    println it
    if (it.elementType=='wrapper'){
        reportMap = it.findAll { k, v -> k in ['serialId', 'type'] }
		plugin = it.find { k, v -> k=='version' }
	        thePluginClass=it.find { k, v -> k=='class' }
	        if (plugin != null) {
	            pluginClass = thePluginClass.getValue()
	            pluginVers = plugin.getValue()
                reportMap.put('infoFile',it["${reportMap.type}_${reportMap.serialId}" as String].infoFile)
		        reportMap.put('pluginClass', pluginClass)
	            reportMap.put('pluginVers', pluginVers)
	        }
        wrapFile << "$jobName,$buildNumber,${reportMap.serialId},${reportMap.type},${reportMap.infoFile},${reportMap.pluginClass},${reportMap?.pluginVers?.minus('plugin:')}\n"
    }
} //end check for element type

println "\nCONFIGURATION_BUILD_WRAPPERS_END"
return 'Success'

//May 2022 updated helper methods for BUILDERS

def visit(List configList, Node node, HashMap elementConfiguration) {
    childNodeList=[] // a list of maps
    scriptRefs=[] //a list where we maintain the hrefs of the scripts encoutered
    scriptableNode=['scriptler','groovy','BatchFile','publish-over-ssh','r'] //note small 'r' for biouno.r plugin
    infoNode=['envinject','jenkins-multijob-plugin','conditional-buildstep'] 
    prefix='\t'
    //generate plugin attribute if it is missing
            if (node.attribute('plugin')==null){
                node.@'plugin'=node.name().split('\\.')[-1]+'@notPlugin'
                }
            isFromPlugin=node.attribute('plugin')!=null
            isScriptableNode=node.attribute('plugin').split('@')[0] in scriptableNode
            isInfoNode=node.attribute('plugin').split('@')[0] in infoNode 
            //logic handles various node type options
            // println 'VISITING:'+node.name()
            // println 'LOGIC FOR SWITCH'+ [isFromPlugin,isScriptableNode,isInfoNode]
            switch ([isFromPlugin,isScriptableNode,isInfoNode]){
                case [[true,true,false]]: //Builder node with script
                println ("${prefix}>SerialId:${elementConfiguration.serialId}")               
                ofin=0                 
                builderNodeMap=getNodeBuilderMap(node,ofin,elementConfiguration )
                childNodeScript=setNodeScriptCommands(node, builderNodeMap ,elementConfiguration, buildEnv) 
                childNodeInfo=setNodeInfo(node, builderNodeMap ,elementConfiguration, buildEnv)
                builderNodeMap=builderNodeMap+childNodeScript+childNodeInfo
                consoleReportBuilder(node)
                //println 'Script Node as child: '+builderNodeMap               
                childNodeList.add(builderNodeMap)                
                break
                case [[true,false,true]]:
                // println ("${node.name()}: Builder node with info")
                println ("${prefix}>SerialId:${elementConfiguration.serialId}")  
                ofin=0                 
                builderNodeMap=getNodeBuilderMap(node,ofin,elementConfiguration )
                childNodeInfo=setNodeInfo(node, builderNodeMap ,elementConfiguration, buildEnv)   
                builderNodeMap=builderNodeMap+childNodeInfo             
                childNodeList.add(builderNodeMap)
                consoleReportBuilder(node)
                break
                case [[true,false,false]]:
                //println ("${it.name()} : a nonScriptable, nonInfo node")
                println ("${prefix}>SerialId:${elementConfiguration.serialId}")  
                ofin=0                 
                builderNodeMap=getNodeBuilderMap(node,ofin,elementConfiguration )
                childNodeInfo=setNodeInfo(node, builderNodeMap ,elementConfiguration, buildEnv)   
                builderNodeMap=builderNodeMap+childNodeInfo             
                childNodeList.add(builderNodeMap)
                consoleReportBuilder(node)
                break   
            } 
   childNodeList.each{
   elementConfiguration.put("${it.parentNode}_${it.serialId}" as String,it)
   //println 'elementConfiguration:'+elementConfiguration.keySet()
   }
    /*note global configList */
    configList.add(elementConfiguration)
 return configList
}

/*
Parse a conditional multi-step builder
*/

def visitConditionalBuilder(List configList, Node conditionalRootNode, HashMap elementConfiguration, HashMap buildEnv) {
    parentNode='' //a temporary holder while working on children
    parentSerialId=elementConfiguration.serialId
    childNodeList=[] // a list of maps
    childSerialId=1//0
    scriptableNode=['groovy','BatchFile']
    infoNode=['envinject','jenkins-multijob-plugin','conditional-buildstep','parameterized-trigger'] 

    //First insert conditional builder
    builderNodeMap=getNodeBuilderMap(conditionalRootNode,0,elementConfiguration )
    // println 'Root Node Map: '+ builderNodeMap
    childNodeInfo=setNodeInfo(conditionalRootNode, builderNodeMap ,elementConfiguration, buildEnv) 
    builderNodeMap=builderNodeMap+childNodeInfo               
    childNodeList.add(builderNodeMap) 

    if (conditionalRootNode.conditionalbuilders[0].children().size() >= 1) { //check that this is not a text node
    conditionalRootNode.@hasChildren=true //node is a parent of nested builders
	ofin=0 //count of interest nodes        
	conditionalRootNode.conditionalbuilders[0].depthFirst().each { 
            if (it.attribute('plugin')==null){// && it.name() in ['hudson.tasks.BatchFile']){
               it.@'plugin'=it.name().split('\\.')[-1]+'@notPlugin'
            //    println 'NOT a PLugin' +it.attribute('plugin')
            }
            //check if this node of interest
            isFromPlugin=it.attribute('plugin')!=null
            isScriptableNode=it.attribute('plugin').split('@')[0] in scriptableNode
            isInfoNode=it.attribute('plugin').split('@')[0] in infoNode
            //logic handles various node type options
            switch ([isFromPlugin,isScriptableNode,isInfoNode]){
                case [[true,true,false]]:
                // println ("${it.name()} : a node with script")                
                ofin++                 
                // println "\t>CONDITIONAL_SCRIPT:$ofin:${it.name()}" 
                println "\t>serialId:${parentSerialId}.${childSerialId++}" 
                builderNodeMap=getNodeBuilderMap(it,ofin,elementConfiguration )
                childNodeScript=setNodeScriptCommands(it, builderNodeMap ,elementConfiguration, buildEnv) 
                childNodeInfo=setNodeInfo(it, builderNodeMap ,elementConfiguration, buildEnv) 
                builderNodeMap=builderNodeMap+childNodeScript+childNodeInfo               
                childNodeList.add(builderNodeMap)
                // println "Child of Conditional $parentSerialId" +builderNodeMap    
                // println "Child Node List of Conditional $parentSerialId" +childNodeList           
                break
                case [[true,false,true]]:
                // println ("${it.name()}: a node with info")
                println "\t>serialId:${parentSerialId}.${childSerialId++}" 
                ofin++                 
                // println "\t>CONDITIONAL_SCRIPT:$ofin:${it.name()}" 
                // println "\t>parentSerialId=${parentSerialId}" 
                builderNodeMap=getNodeBuilderMap(it,ofin,elementConfiguration )
                childNodeInfo=setNodeInfo(it, builderNodeMap ,elementConfiguration, buildEnv) 
                builderNodeMap=builderNodeMap+childNodeInfo               
                childNodeList.add(builderNodeMap)
                break
                case [[true,false,false]]:
                //println ("${it.name()} : a nonScriptable, nonInfo node")
                consoleReportBuilder(it)
                break
                } //end switch       
        }//end node.depth

    }//end node.children.size>1 (not a text node)
    else {
        println "\t ${conditionalRootNode.name()}: ${conditionalRootNode.text()}"
        elementConfiguration.put(it.name() as String, it.text() as String)
    }
// println 'Your Child node List'+childNodeList
   childNodeList.each{
   elementConfiguration.put("${it.parentNode}_${it.serialId}" as String,it)
   }
    configList.add(elementConfiguration)
return configList
}

/*
a method to pretty print elements of the builder to the console
custom prints specified nodes and attributes based on their types
*/
def consoleReportBuilder(Node node){
    // a curated remapping of important conditional attributes using switch statements
    //note the use of the null safe groovy operator ?
    prefix='\t'//'\t curated-> '
    switch(node.name() as String){
        case"runner":
            println "${prefix}${node.name()}: ${node?.attribute('class').split('\\.')[-1] as String}"
        break                        
        case "runCondition":
        println "${prefix}${node.name()}: ${node?.attribute('class').split('\\.')[-2..-1] as String}"
        break
        case"condition":
        if (node.text()!=''){
          if (node.attribute('class')==null){
            node.@'class'='notClass'
          }
          //println "OhNo!: ${node.name()} : ${node.attribute('class')}" //"${prefix}${node.name()}: ${node?.attribute('class')?.split('\\.')[-1] as String}"
          println "${prefix}${node.name()}: ${node?.attribute('class')?.split('\\.')[-1] as String}"
        }
        break
        case"conditionalbuilders":
        // println "\t ${'-'*10} ${node.name()}: ${'-'*10}"
        break 
        case["properties","configs"]:
        if (node.text()!=''){
            println "${prefix}${'-'*10} ${node.name()}: ${'-'*10}"
            node.text().split('\\n').each{p->
            println "${prefix*2}$p"
        }
        }else{
            println "${prefix}${node.name()}: "
        }
        // println "\t ${'-'*10} ${node.name()} end: ${'-'*10}"
        break 
        case"hudson.tasks.BatchFile":
        println "${prefix}>${node.name()}"                         
        break
        case"command":
        println "${prefix}${'-'*10} ${node.name()}: ${'-'*10}"
        node.text().split('\\n').each{p->
            println "${prefix*2}$p"
        }
        println "${prefix}${'-'*10} ${node.name()} end: ${'-'*10}"
        break                                                                                              
        default:
        if (node.children().size() > 1){
            // println "${prefix} ${node.name()}: multivalue"
            node.depthFirst().eachWithIndex{mv,mvCount->
            //we skip the first element as it displays the value concatenation 
            if (mvCount>0){
            println "${prefix}${mv.name()}: ${mv.text()}"            
            }

            }
        }else{
            println "${prefix}${node.name()}: ${node.text()}" 
        }
        
        } //end switch
}

/*
retrieves map of nested builder attributes
includes custom attributes useful for reporting nested builders
*/
def getNodeBuilderMap(Node theNode, int childSerialId ,HashMap elementConfiguration ){
    builderNodeMap=[:] //new child attribute map 
    parentSerialId=elementConfiguration.serialId 
    //For non-nested builders childSerialId=0
    if (childSerialId!=0) {              
    builderNodeMap.put('serialId',parentSerialId+'.'+childSerialId)
    }else{
      builderNodeMap.put('serialId',parentSerialId)  
    }
    builderNodeMap.put('parentNode',"${theNode.name().tokenize('.')[-1]}")
    builderNodeMap.put('parentPlugin',"${theNode.name()}")
    builderNodeMap.put('parentPluginVersion',"${theNode.attribute('plugin')}")
    builderNodeMap.put('parentSerialId',"${parentSerialId}") 
        theNode.depthFirst().each{e->
        //minimize chatter from parent nodes-report only single children
            if (e.depthFirst().size() <= 2){
            builderNodeMap.put (e.name(),e.text())
            }
        }
        builderNodeMap.put('childName',builderNodeMap.parentNode+'_'+builderNodeMap.serialId)
        // println builderNodeMap
    return builderNodeMap
}

/*
set the attributes associated with different script type builders to a child node
supports Groovy, batch, SSH buidlers 
Note the node has been flattened into a single map so we can easily access the desired elements
*/
def setNodeScriptCommands(Node theNode, HashMap builderNodeMap ,HashMap elementConfiguration, HashMap buildEnv){
    scriptRefs=[] //a list where we maintain the hrefs of the scripts encoutered
    scriptWorkspace =buildEnv.scriptWorkspace
    artifactPath=buildEnv.artifactPath
    //example scriptFileName: builder_1_ScriptlerBuilder.txt
    scriptFileName="${elementConfiguration.elementType}_${builderNodeMap.serialId}_${builderNodeMap.parentNode}.txt"
    if (theNode.text()!=''){
        scriptFile = new File("$scriptWorkspace/${scriptFileName}")
        
        switch(builderNodeMap.parentNode as String){
            case"BapSshBuilderPlugin":
            scriptFile << builderNodeMap.find{g-> g.key=='execCommand'}?.value
            break
            case"SingleConditionalBuilder":
            scriptFile << builderNodeMap.find{g-> g.key=='script'}?.value
            break            
            case"BatchFile":
            scriptFile << theNode.text()
            break
            default:
            scriptFile << theNode.text()
        }
        
        scriptRefs.add("${artifactPath}embeddedScripts/$scriptFileName/*view*/")
        builderNodeMap.put("scriptCode",scriptRefs.join(';'))
        childScriptElement=builderNodeMap.find{g-> g.key=='scriptFile'}?.value
        scriptCode=builderNodeMap.find{g-> g.key=='scriptSource'}?.value
        scriptCode=builderNodeMap.find{g-> g.key=='command'}?.value //for batch and R files
        
        if (childScriptElement!=null){
        scriptId=childScriptElement.replace('\\','/').tokenize('/')[-1]
        }else{
            scriptId='NA'
        }
    } else{
       builderNodeMap.put( 'scriptFileName',"TXT_IS_EMPTY")
       builderNodeMap.put( 'scriptFile',"TXT_IS_EMPTY")
    }
    return builderNodeMap
}

/*
set information attributes associated with non-script type builders to a child node
these provide useful information
*/
def setNodeInfo(Node theNode, HashMap builderNodeMap ,HashMap elementConfiguration, HashMap buildEnv){
    scriptRefs=[]
    scriptWorkspace =buildEnv.scriptWorkspace
    infoWorkspace=buildEnv.infoWorkspace
    artifactPath=buildEnv.artifactPath
    infoFileName="Info_${elementConfiguration.elementType}_${builderNodeMap.serialId}_${builderNodeMap.parentNode}.txt"
    if (theNode.text()!=''){
        // infoFile = new File("$scriptWorkspace/${infoFileName}")
        infoFile = new File("$infoWorkspace/${infoFileName}")
        theNode.depthFirst().each{e->
        //minimize chatter from parent nodes-report only single children
            if (e.depthFirst().size() <= 2){
            infoFile << e.name()+': '+e.text()+'\n'
            }
        }
        scriptRefs.add("${artifactPath}jobInfo/$infoFileName/*view*/") //use final predicted path           
        if (infoFile!=null){
        scriptId=infoFile.canonicalPath.replace('\\','/').tokenize('/')[-1]
        builderNodeMap.put( 'infoFileName',scriptId)
        builderNodeMap.put( 'infoFile',scriptRefs.join(';'))
        }else{
            scriptId='NA'
        }
    } else{
       builderNodeMap.put( 'infoFileName',"TXT_IS_EMPTY")
       builderNodeMap.put( 'infoFile',"TXT_IS_EMPTY")
    }
    return builderNodeMap
}


/*
set information attributes associated with job parameters
these provide useful information
*/
def setParamNodeInfo(Node theNode, HashMap paramNodeMap ,HashMap elementConfiguration, HashMap buildEnv){
    scriptRefs=[]
    scriptWorkspace =buildEnv.scriptWorkspace
    infoWorkspace=buildEnv.infoWorkspace
    artifactPath=buildEnv.artifactPath
    infoFileName="Info_${elementConfiguration.elementType}_${elementConfiguration.serialId}_${elementConfiguration.name != null ? elementConfiguration.name : "UNNAMED_$elementConfiguration.serialId"}.txt"
    if (theNode.text()!=''){
        infoFile = new File("$infoWorkspace/${infoFileName}")
        elementConfiguration.paramConfigInfo.each{e->
        infoFile <<  (e as String) + '\n'
        }
        scriptRefs.add("${artifactPath}jobInfo/$infoFileName/*view*/") //use predicted final path (not workspace)     
        if (infoFile!=null){
        scriptId=infoFile.canonicalPath.replace('\\','/').tokenize('/')[-1]
        elementConfiguration.put( 'infoFileName',scriptId)
        elementConfiguration.put( 'infoFile',scriptRefs.join(';'))
        println 'AHA!' + elementConfiguration.infoFile
        }else{
            scriptId='NA'
        }
    } else{
       elementConfiguration.put( 'infoFileName',"TXT_IS_EMPTY")
       elementConfiguration.put( 'infoFile',"TXT_IS_EMPTY")
    }
    return paramNodeMap
}

/*
create a reportMap to be written to builderProps.csv file
*/

def getReportMap(LinkedHashMap builderMap, HashMap buildEnv){
    reportListOfMaps=[] //need a list in case we have nested builders
    //nested conditional builders   
    builderMap.each{     
    if (it.value.getClass()==LinkedHashMap){            
        tupleKey=it.key
        reportMap = it.value?.findAll { k, v -> k in ['serialId','type','parentNode','scriptId','scriptFile','infoFile','scriptCode','execCommand','parentPlugin','parentPluginVersion']}
        //diagnostic
        // println 'Found keys in ReportMap: '+it.key+': '+reportMap 
      switch(reportMap.parentNode as String){
        case"ScriptlerBuilder":
        reportMap.scriptCode="${buildEnv.scriptletRunLink}${reportMap.scriptId}"
        break
        case["Groovy","SystemGroovy"]:
        if(!reportMap.scriptId==null){
        reportMap.scriptId=reportMap.scriptFile.replace('\\','/').split('/')[-1]
        reportMap.scriptCode=reportMap.scriptFile
        }
        break
        case"BapSshBuilderPlugin":
        reportMap.scriptId='SSH_Script'
        break 
        case"BatchFile":
        reportMap.scriptId='BatchFile'
        break  
        case"R":
        reportMap.scriptId='R_script'
        break                       
        default:
        println 'doing default action for: '+it.key
        } //end switch
    reportListOfMaps.add(reportMap)
    reportMap=null
   }//end if
    }//end builderMap.each      
   
    return reportListOfMaps
}

def visitParamType(List configList, Node node, HashMap elementConfiguration) {
    scriptRefs=[] //a list where we maintain the hrefs of the scripts encoutered
    paramConfigInfoList=[] //a list of parameter configuration options
    if (node.children().size() >= 1) { //check that this is not a text node
        node.depthFirst().each {
            if (it instanceof Node) {
                if (it.depthFirst().size() <= 2) {
                    if (it.name() in ['script','command']){
                        parentNode=it.parent().name()
                        parentNodeName=it.parent().name()
                        pluginNameList=parentNodeName.split('\\.')
                        //name from last plugin part
                        if (pluginNameList.size()>1){
                           parentNodeName=pluginNameList[-1]
                        }
                        scriptFileName="${elementConfiguration.elementType}_${elementConfiguration.serialId}_${elementConfiguration.name != null ? elementConfiguration.name : "UNNAMED_$elementConfiguration.serialId"}_${parentNodeName}.txt"
                        if (it.text()!=''){
                            scriptFile = new File("$scriptWorkspace/${scriptFileName}")
                            scriptFile << it.text()
                            scriptRefs.add("${artifactPath}embeddedScripts/$scriptFileName/*view*/")
                            elementConfiguration.put("scriptCode",scriptRefs.join(';'))
                        } else{
                            println "TXT_IS_EMPTY: $scriptFileName"
                        }

                        println "\t\t${it.name()+'-'*20}"//console script delimiter
                        it.text().split('\\n').each {
                            println "\t\t\t $it"
                        }
                    }else {
                        println "\t${it.name()}: ${it.text()}"
                        paramConfigInfoList.add("${it.name()}: ${it.text()}")
                        elementConfiguration.put(it.name() as String, it.text() != '' ? it.text() : null)
                    }

                } else {
                    //println "\t ${it.name()}: ${it.attributes()}"
                     paramConfigInfoList.add("${it.name()}: ${it.attributes()}")
                    elementConfiguration.put(it.name() as String, it.attributes() as String)
                }
            } //end NOT instance of String

        }//end node.depth


    }//end node.children.size>1 (not a text node)
    else {
        println "\t${node.name()}: ${node.text()}"
        paramConfigInfoList.add("${node.name()}: ${node.text()}")
        elementConfiguration.put(it.name() as String, it.text() as String)
    }
    /*note global configList */
    elementConfiguration.put('paramConfigInfo',paramConfigInfoList)
    configList.add(elementConfiguration)
 return configList
}

