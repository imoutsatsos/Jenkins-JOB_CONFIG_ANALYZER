/*** BEGIN META {
 "name" : "copyScriptDependencies",
 "comment" : "copies scriptlets and other script dependencies that a Jenkins job depends on",
 "parameters" : [ 'vWORKSPACE','vCONFIGCSV', 'vJOB_NAME'],
 "core": "1.596",
 "authors" : [
 { name : "Ioannis K. Moutsatsos" }
 ]
 } END META**/

/**
 * Created by moutsio1 on 12/15/2017.
 */
WORKSPACE=vWORKSPACE
propFilePath=vCONFIGCSV
PROJECT_NAME=vJOB_NAME

def env = System.getenv()
assert env.JENKINS_HOME!=null

copyDest=[scriptlet:"$WORKSPACE/scriptler/scripts", groovy:"$WORKSPACE/externalScripts", rscripts:"$WORKSPACE/externalScripts", other:"$WORKSPACE"]
copySrc=[scriptlet:"${env.JENKINS_HOME}/scriptler/scripts"]
copySource=''
copyDestination=''

getProjectScripts(propFilePath).each{

    switch( it['TYPE']){
        case"ScriptlerBuilder":
      copySource="${copySrc.scriptlet}/${it.SCRIPT_ID}"
            copyDestination=copyDest.scriptlet
            break
        case"Groovy":
            copySource=it.CODE_LINK
            copyDestination=copyDest.groovy
            if (copySource.startsWith('http://')){
            it.SKIPCOPY_FLAG=true
            }
            break
        default:
              if (['commandScript','null','NA','R-script'].contains(it.SCRIPT_ID)){
                println "Skipping COPY of ${it.TYPE}-${it.SCRIPT_ID}"
                it.SKIPCOPY_FLAG=true
              }else{
               copySource=it.CODE_LINK
               copyDestination=copyDest.groovy
              }

    }
    
    
  if (!it.SKIPCOPY_FLAG){
    if (!new File(copyDestination).exists()){
        new File(copyDestination).mkdirs()
    }
  scriptPath=replaceGlobalVars(copySource, PROJECT_NAME)
  scriptFile=new File(scriptPath)
  assert scriptFile.exists()
  copyFile(scriptFile,copyDestination,it.SCRIPT_ID)
  }
}

/*
a method to create property map of the project script files
from an existing CSV script properties artifact from JOB_CONFIG_ANALYZER
 */
def getProjectScripts(propFilePath){

    projectScripts=[] //a list of maps
    propFile= new File(propFilePath)
    assert propFile.exists()
    l=0
    propFileCols=[]

    propFile.eachLine{
        if(l==0){
            propFileCols=it.tokenize(',')
        }else{
            colVals=it.tokenize(',')
            scMap=[:]
            propFileCols.eachWithIndex{columnName,indx->
                scMap.put(columnName,colVals[indx])
            }
            projectScripts.add(scMap)
        }
        l++
    }
    return projectScripts
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

/* replaces global vars with their values from the system environemnt*/
def replaceGlobalVars(path, projectName){
    env = System.getenv()
    myEnv=env+[JOB_NAME:projectName]       
    myEnv.each{k,v->
    println k+':'+v
        if (path .contains('$'+k)){
            path=path.replace('$'+k,v)
            println "REPLACED ENVV VAR:$path"
        }
    }
    return path
}
