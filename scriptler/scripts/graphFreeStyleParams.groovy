/*** BEGIN META {
  "name" : "graphFreeStyleParams",
  "comment" : "Creates a dot structure for visualization of project parameter flow with Graphviz/GVEdit",
  "parameters" : [ 'jobName','paramDotPath'],
  "core": "2.121",
  "authors" : [
    { name : "Ioannis K. Moutsatsos" }
  ]
} END META**/

/*
Node Graph for Freestyle Parameters
Custom support for
hudson.model.StringParameterDefinition
hudson.model.BooleanParameterDefinition
hudson.model.TextParameterDefinition
hudson.model.FileParameterDefinition

class org.biouno.unochoice.ChoiceParameter
class org.biouno.unochoice.CascadeChoiceParameter
class org.biouno.unochoice.DynamicReferenceParameter

*/
job=jenkins.model.Jenkins.instance.getJob(jobName)
dotFile= new File (paramDotPath)
fileWriter=dotFile.newWriter(false)

dotCommandList=[]
paramList=[]
scriptNode=[:]

gColorNames=['darkolivegreen1','burlywood2','coral','aquamarine1','oldlace','plum1','sienna2','lightgray','darkorange1','gold','magenta1','orangered2','tan','lavender','greenyellow']
booleanStyle="""[fillcolor=yellow, style="rounded,filled",shape=diamond, color=blue]"""
stringStyle="""[shape=plaintext]"""
textStyle="""[shape=underline, color=blue]"""
defaultStyle="""[shape=rect, color=green]"""
acStyle="""[shape=invhouse, color=blue]"""
cascadeStyle="""[shape=oval, color=blue]"""
fileStyle="""[shape=tab, color=black]"""
drStyle="""[shape=hexagon, color=blue]""" //class unochoice.DynamicReferenceParameter
drHiddenStyle="""[style=filled, shape=hexagon, fillcolor=gray]""" //hidden (ET_FORMATTED_HIDDEN_HTML) unochoice.DynamicReferenceParameter
scriptLink="""[dir=both color="maroon4"]"""
seqId=1

job.properties.each{
  //println it.value.class
  
  if( it.value.class==hudson.model.ParametersDefinitionProperty){
       
    it.value.getParameterDefinitions().each{pdn->
      paramNode=[:]
      //println '\n\t'+pdn.name
      //println '\t\t'+pdn.getClass() +'\n'
      paramName=pdn.name.replace('.','_') //dot does not support names with dot
          if (paramName==''){
            paramName="UNNAMED_$seqId"
    }
      paramNode.put('name',paramName)
      paramNode.put('class',pdn.getClass())
      paramNode.put('scriptType','na')
      paramNode.put('scriptName','na')
      paramNode.put('choiceType','na')
      paramNode.put('refParam',[])
     
      
      if (pdn  in org.biouno.unochoice.UnoChoiceParameter 
	  && pdn.getScript().getClass()==org.biouno.unochoice.model.ScriptlerScript ){
        //println '\t\t'+pdn.getScript().scriptlerScriptId
        //println '\t\t'+pdn.getScript().descriptor.plugin
       // println '\t\t'+pdn.getScript().getParameters()
        paramNode.put('scriptType','ScriptlerScript')
        paramNode.put('scriptName',pdn.getScript().scriptlerScriptId)
        paramNode.put('choiceType',pdn.getChoiceType())
        
      }
      if (pdn in org.biouno.unochoice.CascadeChoiceParameter || pdn in org.biouno.unochoice.DynamicReferenceParameter){
        //println '\t\t'+"${pdn.getChoiceType()}:\t${pdn.name}"
        paramNode.put('choiceType',pdn.getChoiceType())
        paramNode.put('refParam',pdn.getReferencedParameters().tokenize(','))
      }
      paramList.add(paramNode)
      seqId++
    }
    
    
  } //end each Parameter Definition Property
  
} //end each job properties

//print pre-amble

preAmble= """digraph {
    rankdir=LR;
subgraph cluster_0 {
        label="$jobName Parameters";
"""
dotCommandList.add(preAmble)

paramList.each{l=it.value->
    switch(l.class as String){
    case "class hudson.model.BooleanParameterDefinition":
         dotCommandList.add( "\t${l.name}$booleanStyle;")
    break
    case "class hudson.model.StringParameterDefinition":
         dotCommandList.add( "\t${l.name}$stringStyle;")
    break
    case "class hudson.model.FileParameterDefinition":
         dotCommandList.add( "\t${l.name}$fileStyle;")
    break  
    case "class hudson.model.TextParameterDefinition":
         dotCommandList.add( "\t${l.name}$textStyle;")
    break
    case "class org.biouno.unochoice.ChoiceParameter":
        dotCommandList.add( "\t${l.name}$acStyle;")
    break
          case "class org.biouno.unochoice.CascadeChoiceParameter":
        dotCommandList.add( "\t${l.name}$cascadeStyle;")
    break
    case "class org.biouno.unochoice.DynamicReferenceParameter":
      if(l.choiceType=='ET_FORMATTED_HIDDEN_HTML'){
        dotCommandList.add( "\t${l.name}$drHiddenStyle;")
      }else{
        dotCommandList.add( "\t${l.name}$drStyle;")
      }
    break
      default:
	  dotCommandList.add( "\t${l.name}$defaultStyle;")
}
  /*  if (l.refParam.size==0){      
      println "\t${l.name};"
      if ((l.class as String).contains('Boolean')){
      println "\t${l.name}$booleanStyle;"
      }
    }
    */
    l.refParam.each{
      dotCommandList.add( "\t$it -> ${l.name};")
         
     }
  
}
    
//close cluster_0 start cluster_1
closeClust_0= """}
subgraph cluster_1{
label="scripts";"""
dotCommandList.add(closeClust_0)

  gcn=0
  paramList.each{l=it.value->  
      if(l.scriptName!='na'){
        scriptLabel=l.scriptName.replace('.groovy','')
        dotCommandList.add( "\t$scriptLabel[shape=box, style=filled, fillcolor=${gColorNames[gcn]}];")
        dotCommandList.add( "\t${l.name}[style=filled, fillcolor=${gColorNames[gcn]}];")
        dotCommandList.add( "\t$scriptLabel -> ${l.name}$scriptLink;") 
        gcn++
        if (gcn>gColorNames.size){
            //recycle colors
            gcn=0
        }
      }
  }

//close cluster_1
closeClust_1= """
	}
}
"""
dotCommandList.add(closeClust_1)
try{
fileWriter<<dotCommandList.join('\n')
fileWriter.flush()
fileWriter.close()
} catch(Exception e) {
	println e.getMessage()
}
println paramDotPath
return 'Success: (from graphFreeStyleParams)'