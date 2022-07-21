/*** BEGIN META {
 "name" : "parametrizeTemplate",
 "comment" : "Generates text file in workspace from a template file",
 "parameters" : [ 'vTemplateURLPath','vTemplateParam','vParamValue','vOutfolder'],
 "core": "1.596",
 "authors" : [
 { name : "Ioannis K. Moutsatsos" }
 ]
 } END META**/
/**
 * Created by moutsio1 on 4/18/2016.
 * A script to generate a parametrized text from a template
 * Requires two comma separated lists of param names and values. They must be of same size an dmatch order
 * supports template input from URL and Local Path 
 */

def templateURLPath=vTemplateURLPath
def templateParam=vTemplateParam 
def paramValue=vParamValue 
def outfolder = vOutfolder
def theTemplate='' //will be read from URL or Local File Path

def templateBinding=[:]
 
/*create a default file name for the text file
* Name from Template file name by removing the case insensitive string 'template'
* */
textName=templateURLPath.split('/').last().replaceAll("(?i)template",'')
tParams=templateParam.split(',')
tVals=paramValue.split(',')
assert tParams.size()==tVals.size()
v=0
tParams.each{
    templateBinding.put("$it" as String, tVals[v])
    v++
}

switch(templateURLPath){
 case ~/https:.*/:
 	println templateURLPath
    theTemplate=templateURLPath.toURL().getText()
 break
 default:
    templateFile= new File(templateURLPath)
    assert templateFile.exists()
    theTemplate=templateFile.text
}




pTemplate=new File("$outfolder/$textName")
pTemplateWriter= pTemplate.newWriter(false)
engine = new groovy.text.GStringTemplateEngine()
arrayTemplate = engine.createTemplate(theTemplate)
try{
  pTemplateWriter<< arrayTemplate.make(templateBinding)
  pTemplateWriter.flush()
  pTemplateWriter.close()
} catch(Exception e) {
	println e.getMessage()
}

return 'Success: (from parametrizeTemplate)'