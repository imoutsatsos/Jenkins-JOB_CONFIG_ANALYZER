/*** BEGIN META {
  "name" : "copyJobConfig",
  "comment" : "Copies a job configuration file to a user specified path and file",
  "parameters" : [ 'jobName',fileName','workspace'],
  "core": "1.596",
  "authors" : [
    { name : "Ioannis K. Moutsatsos" }
  ]
} END META**/

def env=System.getenv()
def JENKINS_HOME=env['JENKINS_HOME']
def WORKSPACE=workspace

//make destination if not exists
if (!new File(workspace).exists()){
 new File(workspace).mkdirs()  
}


jobName=jobName
println "\nCOPY (to Workspace) config.xml: $jobName\n"
def configPath="$JENKINS_HOME/jobs/${jobName}/config.xml"
def configFile=new File(configPath)
assert configFile.exists()

copyFile(configFile, WORKSPACE,fileName)


/* a simple method to copy a file */
def copyFile(configFile,destination, fileName)
{
    def file = new FileOutputStream("$destination/$fileName")
    def out = new BufferedOutputStream(file)
    out << configFile.newDataInputStream()
    out.close()
}