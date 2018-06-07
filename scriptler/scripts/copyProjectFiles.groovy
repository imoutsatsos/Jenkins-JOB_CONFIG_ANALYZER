workspace=vDestination
//make destination if not exists
if (!new File(workspace).exists()){
 new File(workspace).mkdirs()  
}
propFilePath=vPropFilePath
artiCol=vArtifactCol as int//the column containing the artifact name
artiPath=vArtifactPath //the path containing the artifacts

println "Copying to: $workspace"
getScriptPaths(propFilePath, artiCol, artiPath).each{
scriptName=it.key
scriptPath=replaceGlobalVars(it.value)
scriptFile=new File(scriptPath)
assert scriptFile.exists()
copyFile(scriptFile,workspace,scriptName)
}




/* a simple method to copy a file */
def copyFile(File theFile,destination, fileName)
{
  def destinationFile= new File("$destination/$fileName")  
  def file = new FileOutputStream(destinationFile)
    def out = new BufferedOutputStream(file)
    out << theFile.newDataInputStream()
    out.close()
  println "\t ${destinationFile.name}"
}
/*
a method to create a map of script names to paths
from a csv style file
using a map insures that a singe copy of the file is created
even if it is used multiple times
*/
def getScriptPaths(propFilePath, artCol, artPath){
notScripts=['SCRIPTLET','','null','--','SCRIPT_ID','R-script','scriptFile','CODE_LINK']
scriptPaths=[:]
propFile= new File(propFilePath)
assert propFile.exists()
propFile.eachLine{
scriptName=it.split(',')[artCol-1]
if (notScripts.any({it==scriptName})){
}else{
  if(artPath!=''){
println "Using user provided script path: $artPath"
    scriptPath=artPath+scriptName
scriptPaths.put(scriptName,scriptPath)
  }else{
    scriptFileName=scriptName.replace('\\','/').split('/').last()
    scriptPath=scriptName    
     //ignore http links
    if(!scriptPath.startsWith('http')){
    println "Auto detecting path as: $scriptPath"
      scriptPaths.put(scriptFileName,scriptPath)
      
    }
    
  }
}
  println scriptPaths
return scriptPaths
}

}

/* replaces global vars with their values from the system environemnt*/
def replaceGlobalVars(path){
  env = System.getenv()
  env.each{k,v->
  if (path .contains('$'+k)){
  path=path.replace('$'+k,v)
    println "REPLACED ENVV VAR:$path"
  }
  }
  return path
}