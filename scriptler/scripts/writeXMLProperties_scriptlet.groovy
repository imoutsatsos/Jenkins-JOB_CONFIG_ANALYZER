/*** BEGIN META {"name" : "writeXMLProperties_scriptlet",
 "comment" : "Writes an XML Summary report from a properties file. Report configuration is read from a separate properties configuration file. Supports table filtering",
 "parameters" : [ 'workspaceVar','configProps'],
 "core": "1.596",
 "authors" : [{ name : "Ioannis Moutsatsos" }]} END META**/

/**
 * Writes a Summary Display Jenkins Plugin XML Template from a configuration file.
 * Summary content can be read from files formatted as Java properties or in delimited format
 * Author: Ioannis K. Moutsatsos
 * Last Update: 3/6/2018
 * DMPQM-721, DMPQM-701
 * DMPQM-473, DMPQM-213, DMPQM-298, DMPQM-215, DMPQM-204,
 * DMPQM-195, DMPQM-194, DMPQM-189, DMPQM-174, DMPQM-153,
 * Required Script Parameters: workspaceVar, configProps
 */

import groovy.xml.*

println "\n---------Write XML Template for Summary Display Plugin (writeXMLProperties_scriptlet.groovy)---------"
def workspace = workspaceVar
def options = [:]
options.i = configProps //scriptlet parameter

def imageExtensions = ['tif', 'tiff', 'png', 'jpeg', 'jpg', 'gif', 'bmp','svg']
operRelational = ['>', '>=', '<', '<=', 'in']
operMethod = ['startsWith', 'contains', 'endsWith', 'matches']

//java script injected into table for Expanding/Collapsing multiValue elements
addScript = true
multiValueCount = 0

/* Set default size for rendered images.
   May be changed from report configuration on per table basis using the 'imgwidth' table property
 */
def imgWidth = 200
def separator = ','           //default field separator
def multiValueSeparator = ';' //default multi-value separator
def fieldDelimiter =''        //default field delimiter

def noPropUse = false // a flag whether properties file will be used
//Create the properties objects, from the file system:
Properties configProps = new Properties()   // report configuration
Properties summaryProps = new Properties()   // report content
File configFile = new File(options.i)

configProps.load(configFile.newDataInputStream())
//envVars assist in expanding job related parammeters
env = System.getenv()
//println binding.getVariables()
/* expand tokens in properties */
configProps.each{
//propEval=(it.value.startsWith('\"'))?evaluate(it.value):it.value
propEval=(it.value.startsWith('!!'))?evaluate(it.value.replace('!!','"')):it.value
it.value=propEval as String
}
propSource = configProps.getProperty('summary.properties')
if (propSource.startsWith('none')) {
    noPropUse = true
    println 'Report does not use properties file'
} else {
    if (propSource.startsWith('http')) {
        propSource.toURL().eachLine {
            if (it.contains('=')) {
                urlprop = it.split('=')
                summaryProps.put(urlprop[0], urlprop[1])
            }
        }
    } else {
        summaryFile = new File("${workspace}/${configProps.getProperty('summary.properties')}")
        summaryProps.load(summaryFile.newDataInputStream())
    }
} //end none else

def columnSet = []
def rowheader = []
def headerIndex = []
/* Maps for column selection criteria */
exactSelect = [:]
relationalSelect = [:]
isFiltered = false //will be set appropriately if the table data is filtered with a table.select property


def reportStyle = configProps.getProperty('report.style')

def writer = new StringWriter()
def xml = new MarkupBuilder(writer)
/*
Reference: https://wiki.jenkins-ci.org/display/JENKINS/Summary+Display+Plugin
Report style options include
  Section (name)
  Field (name,value, href)
  Table
  Tabs (field, table)
  Accordion (field, table)
 */
switch (reportStyle) {
    case "tab":
        thead = []; theadTemp = []
        thead = configProps.getProperty('tab.header').split(',')
        //confirm that content file (CSV) exists for each tab.header and remove those headers for which it does not
        println "TABS-Original: $thead"
        thead.each {
            if (configProps.getProperty("content.${it}") == 'table') {
                    tabContent=existsTabContent(workspace,configProps.getProperty("table.data.${it}"))
                    if (tabContent['exists']) {
                        theadTemp.add(it)
                    } else {
                        println "\tCould not find content: $tabContent"
                    }
            }            
            if (configProps.getProperty("content.${it}") == 'field' && !noPropUse) {
                tabContent = configProps.getProperty("summary.properties")
                theadTemp.add(it)
            }

        }
        thead = theadTemp //replace header with subset where content exists
        println "TABS-Adjusted: $thead (from available content)"
        xml.tabs {
            thead.each {
                //println it
                tabContent = configProps.getProperty("content.${it}")
                if (configProps.getProperty("separator.${it}") != null) {
                    separator = configProps.getProperty("separator.${it}")
                }
                if (configProps.getProperty("mvSeparator.${it}") != null) {
                    multiValueSeparator = configProps.getProperty("mvSeparator.${it}")
                }
                if (configProps.getProperty("fieldDelimiter.${it}") != null) {
                                    fieldDelimiter = configProps.getProperty("fieldDelimiter.${it}")
                                    separator="$fieldDelimiter$separator$fieldDelimiter"
                                    println "fieldDelimiter: $fieldDelimiter"
                                    println "Separator: $separator"
                }
                startOfKey = configProps.getProperty("field.key.${it}")
                valColor = configProps.getProperty('field.value.color')
                tabName = it
                tab(name: "$it") {
                    switch (tabContent) {
                        case "field":
                            summaryProps.sort().each { k, v ->
                                if (k.toString().startsWith(startOfKey) && v != null) {
                                    if (v.startsWith('http')) {
                                        field(name: k, value: getLinkName(v), detailcolor: valColor, href: '/' + v.split(':/')[1])
                                    } else {
                                        field(name: k, value: v, detailcolor: valColor)
                                    }
                                }

                            }//end each
                            break
                        case "table":
                            //create a table from referenced file
                            // println "Working with $tabName"
                            tabContent=existsTabContent(workspace,configProps.getProperty("table.data.${tabName}"))
                            dataTableSource = tabContent['path']                            
                            println "\n$tabName : Table data from: $dataTableSource"
                            propKey = "table.header.${tabName}"
                            // if no table.length property is defined we write the entire table
                            def ignoreLineCount = false
                            def rownum = 0
                            if (configProps.getProperty("table.select.${tabName}") != null) {
                                exactSelect = [:]
                                relationalSelect = [:]
                                isFiltered = true
                                parseSelectCriteria(configProps.getProperty("table.select.${tabName}"))
                            } else {
                                isFiltered = false
                            }
                            if (configProps.getProperty("table.length.${tabName}") != null) {
                                rownum = configProps.getProperty("table.length.${tabName}").toInteger()
                            } else {
                                ignoreLineCount = true
                            }
                            if (configProps.getProperty("table.imgwidth.${tabName}") != null) {
                                imgWidth = configProps.getProperty("table.imgwidth.${tabName}").toInteger()
                            }

                            if (dataTableSource.startsWith('http')) {
                                dataTableSource.toURL().withReader { reader ->
                                    columnSet = reader.readLine().split(separator).collect{it.replace(fieldDelimiter,'')}                                    
                                    if (configProps.containsKey("table.header.${tabName}".toString())) {
                                        rowheader = configProps.getProperty("table.header.${tabName}").split(',')
                                    } else {
                                        rowheader = columnSet
                                    }
                                    headerIndex = createIndexIntoList(rowheader.toList(), columnSet.toList())
                                }

                            } else {
                                def dataFile = new File(dataTableSource)
                                dataFile.withReader {
                                    reader ->
                                        columnSet = reader.readLine().split(separator).collect{it.replace(fieldDelimiter,'')}                                       
                                        if (configProps.containsKey("table.header.${tabName}".toString())) {
                                            rowheader = configProps.getProperty("table.header.${tabName}").split(',')
                                        } else {
                                            rowheader = columnSet
                                        }
                                        headerIndex = createIndexIntoList(rowheader.toList(), columnSet.toList())
                                }
                                //columnSet //must read CSV file from path
                            }
                            // DMPQM-298 make table sort-able
                            table(sorttable: "yes") {
                                //create table header
                                //now add table data
                                if (dataTableSource.startsWith('http')) {
                                    dataFile = dataTableSource.toURL()
                                } else {
                                    dataFile = new File(dataTableSource)
                                }
                                lineCount = 0
                                dataFile.eachLine { uline, rowIndex ->
                                    if (isSelected(rowheader, uline, exactSelect, relationalSelect, rowIndex)) {
                                        headerIndex.removeAll([-1])
                                        lineValueSet = uline.split(separator, -1)
                                        columnValueMatch = lineValueSet.size() == columnSet.size()

                                        if (lineCount < rownum + 1 && columnValueMatch) {
                                            lineValueSet = uline.split(separator, -1)[headerIndex]
                                            tr() {
                                                lineValueSet.each { h ->
                                                    h = (h.startsWith('!!')) ? evaluate(h.replace('!!', '"')) : h //expand tokens
                                                    h=h.replace(fieldDelimiter,'')
                                                    //if column heading exists we create a table cell, else we skip
                                                    //we also check that the row can be split into enough values or we skip
                                                    //check if multi-value
                                                    isMultiValue = false

                                                    propValueList = []
                                                    if (h.split(multiValueSeparator).size() > 1) {
                                                        isMultiValue = true
                                                        multiValueCount++

                                                    }
                                                    isImage = false //default flag for image file-values

                                                    if (h != null) {
                                                        imageExtensions.each {
                                                            if (h.endsWith(it)) {
                                                                isImage = true
                                                            }
                                                        }
                                                        if (isMultiValue) {
                                                            propValueList = h.tokenize(multiValueSeparator)
                                                            xml.mkp.yieldUnescaped("""<td>${getValueListCdData(propValueList, multiValueCount, addScript)}</td>""")
                                                            addScript = false //added only once then we turn off
                                                        } else {
                                                            // td(value: h, bgcolor: 'white', fontcolor: 'black', align: 'left')
                                                            tdAttributes = getElementAttributes(h)
                                                            if (tdAttributes.containsKey('cdata')) {
                                                                xml.mkp.yieldUnescaped("""<td>${tdAttributes.cdata}</td>""")
                                                            } else {
                                                                td(tdAttributes)
                                                            }
                                                        }
                                                    }
                                                }

                                            }
                                            // if we are counting lines we keep track
                                            if (!ignoreLineCount) {
                                                lineCount++
                                            }
                                        }
                                    }//end if isSelected
                                }//end each line
                            }
                            break
                    }//end content
                } //end tab

            }
        }

        break
    case "table":
        println 'Table reports are not currently supported'
        break
}

def writer4file = new FileWriter("$workspace/writeXmlSummary.xml")
XmlUtil.serialize(writer.toString(), writer4file)
println "\nSummary Display XML Template: ${new File("$workspace/writeXmlSummary.xml").getCanonicalPath()}"
writer4file.close() //close the file

/*
Find index of a list members in another list
For example if source[A,D,F] and target[A,B,C,D,E,F,G] we want to return sourceIndex[0,3,5]
 */

def createIndexIntoList(List source, List target) {
    // println 'Getting index'
    def indexList = []
    source.each { s ->
        indexList.add(target.indexOf(s))
    }
    return indexList
}
/*
Method checks if a URL is accessible
 */

def getResponseCode(String urlString) throws MalformedURLException, IOException {
    URL u = new URL(urlString);
    HttpURLConnection huc = (HttpURLConnection) u.openConnection();
    huc.setRequestMethod("GET");
    huc.connect();
    return huc.getResponseCode();
}

/*
* Parses a groovy map string with column selection criteria
* modifies global vars exactSelect, relationalSelect
* */

def parseSelectCriteria(selectMapString) {
    selectClauses = evaluate("$selectMapString")
    /*parse and assign exact and relational selection criteria*/
    selectClauses.each { k, v ->
        if (v.getClass() == LinkedHashMap) {
            println "\tSELECT '$k' WHERE: $v"
            relationalSelect.put(k, v)
        } else {
            println "\tSELECT '$k' WHERE: $v"
            exactSelect.put(k, v)
        }

    }
}

/*
* Method to determine if a row should be included
* We always include header row and do not filter when the table.select is not set
* */

def isSelected(headList, rowText, exactSelect, relationalSelect, rowCount) {
    if (isFiltered && rowCount > 1) {
        line = rowText.split(',')
        criteria = []
        if (exactSelect != [:]) {
//            println "Exact select $exactSelect"
            criteria.add(isExactRowMatch(headList, line, exactSelect))
        }
        if (relationalSelect != [:]) {
//            println "Method select $relationalSelect"
            criteria.add(isRelationalRowMatch(headList, line, relationalSelect))
        }

        return criteria.contains(false) == false

    } else {
        //unless isFiltered we select all rows
        return true
    }
}

/*
* Method determines whether a row should be included by the matching criteria
 Matches by exact values using AND between clauses
*/

def isExactRowMatch(headlist, line, selClauses) {
    columnIndex = selClauses.keySet()
    ci = headlist.findIndexValues { it in columnIndex }
    testVals = line[ci]
    selectCombinations = GroovyCollections.combinations(selClauses.values())
    return selectCombinations.findResult { (it as String) == (testVals as String) ? it : null } != null
}

/*
 Method creates appropriate assertions that check whether a row
 contains data that match the relational operators and methods
 Note that we rely on a special grammar for that.
*/

def isRelationalRowMatch(headlist, line, selClauses) {
    testClause = [] //a list to keep result of tests
    columnIndex = selClauses.keySet()
    columnIndex.each {
        ci = headlist.findIndexValues { ind -> ind in it }
        testValue = line[ci]
        testValue.each { tv ->
            /* assert that only supported operators are used */
            assert selClauses[it].operator in operRelational.plus(operMethod)

            if (selClauses[it].operator in operRelational) {
                assertion = "$tv ${selClauses[it].operator} ${selClauses[it].value}"
//                println "$tv ${selClauses[it].operator} ${selClauses[it].value}"
                if (selClauses[it].negate == true) {
//              println 'Negating assertion'
                    assertion = "$tv ${selClauses[it].operator} ${selClauses[it].value}==false"
                } else {
                    assertion = "$tv ${selClauses[it].operator} ${selClauses[it].value}".replace('\\', '/')
                }
                testResult = evaluate(assertion)
                testClause.add(testResult)
            }//end in operRelational operator

            if (selClauses[it].operator in operMethod) {
                methodTest = [] //a list to keep test from methods
                selClauses[it].value.each { op ->
//                    println "${selClauses[it]}: ${selClauses[it].negate}"
                    if (selClauses[it].negate == true) {
//              println 'Negating assertion'
                        assertion = "\'${tv}\'.${selClauses[it].operator}(\'$op\')==false".replace('\\', '/')
                    } else {
                        assertion = "\'${tv}\'.${selClauses[it].operator}(\'$op\')".replace('\\', '/')
                    }
//              println 'Asserting:'+assertion
                    methodTest.add(evaluate(assertion))
                }
                testClause.add(methodTest.contains(true))
            }//end in operMethod operator


        }

//        println testClause
    }
    return testClause.contains(false) == false
}

/*
Method generates CDDATA section for insertion into the report template
*/

def getValueListCdData(ArrayList propValueList, id, boolean addScript) {

//java script injected into table for Expanding/Collapsing multiValue elements
    jScript = '''<script>
function showHide(elm, btn) {
console.log('clicking');
jQuery('#'+elm).toggle();
if(jQuery('#'+btn).val()=="Expand"){
jQuery('#'+btn).val('Collapse');
}else{
jQuery('#'+btn).val('Expand');
}
}
</script>'''
    cPrefix = '<![CDATA['
    cButton = """<input onclick="showHide('ulID$id','exco$id')" name="exco" id="exco$id" type="button" value="Expand">--${propValueList.size()}--"""
    cUl = """<ul style="display: none" id="ulID$id">${propValueList.collect { '<li>' + renderHtml(it) + '</li>' }.join('')}</ul>"""
    cPostfix = ']]>'
    return cPrefix + cButton + cUl + (addScript ? jScript : '') + cPostfix
}

/*
Method renders an HTML element based on the element value
The value may indicate an image, or URL
*/

def renderHtml(elValue) {
    imageExtensions = ['tif', 'tiff', 'png', 'jpeg', 'jpg', 'gif', 'bmp','svg']
    imgWidth = 150

    isURI = false
    isImage = false
    cData = ''
// check if elValue is a URI

    if (elValue.startsWith('http')) {
        isURI = true
    }
//check if cell value is an image
    imageExtensions.each { x ->
        if (elValue.endsWith(x)) {
            isImage = true
        }
    }


    switch ([isURI, isImage]) {

        case [[true, false]]:
            cData = """<a style="color:black; font-weight:;" href="${'/' + elValue.split(':/')[1]}" onclick="window.open(this.href,'nom_Popup','height=400 , width=400 ,location=no ,resizable=yes ,scrollbars=yes'); return false;">${getLinkName(elValue)}</a>"""
            break
        case [[true, true]]:
            imgUrl = (elValue.split(':/')[1]).replaceAll('\\\\', '')
            cData = """<a href=\"${'/' + imgUrl}\"/><img width=$imgWidth src=\"${'/' + imgUrl}\"/></a>"""
            break
        default:
            cData = """<a style="color:black; font-weight:;">$elValue</a>"""
    }

    return cData
}

/*
Method returns a map of the attributes for rendering a <td> element
based on the element value/type
 */

def getElementAttributes(String elValue) {
    attrMap = [bgcolor: 'white', fontcolor: 'black', align: 'left']
    imageExtensions = ['tif', 'tiff', 'png', 'jpeg', 'jpg', 'gif', 'bmp','svg']
    imgWidth = 150

    isURI = false
    isImage = false
    cData = ''
// check if elValue is a URI

    if (elValue.startsWith('http')) {
        isURI = true
    }
//check if cell value is an image
    imageExtensions.each { x ->
        if (elValue.endsWith(x)) {
            isImage = true
        }
    }

    switch ([isURI, isImage]) {

        case [[true, false]]:
            attrMap.'value' = getLinkName(elValue)
            attrMap.cdata= """<![CDATA[<a style="color:black; font-weight:;" href="${'/' + elValue.split(':/')[1]}" onclick="window.open(this.href,'nom_Popup','height=400 , width=400 ,location=no ,resizable=yes ,scrollbars=yes'); return false;">${getLinkName(elValue)}</a>]]>"""
           // attrMap.href = "${'/' + elValue.split(':/')[1]}"
            break
        case [[true, true]]:
            imgUrl = (elValue.split(':/')[1]).replaceAll('\\\\', '')
            attrMap.href = "${'/' + imgUrl}"
            attrMap.cdata = """<![CDATA[<a href="${'/' + imgUrl}"/><img width=$imgWidth src="${'/' + imgUrl}"/></a>]]>"""
            break
        default:
            attrMap.'value' = elValue
    }

    return attrMap
}

/*
A method to name a link
Uses Jenkins domain knowledge to identify artifacts and scriptlets
*/
def getLinkName(elemValue){
    isArtifact=false
    isScriptlet=false
    isView=false
    uriList=elemValue.tokenize('/')
    isArtifact=uriList.contains('artifact')
    isScriptlet=uriList.contains('scriptler')
    isView=uriList.contains('*view*')

    switch([isArtifact,isView,isScriptlet]){
        case[[true,false,false]]:
            return  uriList[-1]+ '-Download-'
            break
        case[[true,true,false]]:
            return  uriList[-2]+ '-View-'
            break
        case[[false,false,true]]:
            return   uriList[-1].tokenize('=')[-1]+ '-Scriptlet-'
            break
        default:
            return 'Link'
    }

}


/* 
a method that checks whether a filePath/URL maps to a real file
returns a map of exists flag and path/URL (null if file does not exist)
*/
def existsTabContent(workspace,filePath){
println 'Executing check for existence:'+"$workspace/$filePath"
    tabContent=[:]  
    tabContentInWorkspace = new File ("$workspace/$filePath")
    tabContentElsewhere= new File(filePath)
    
    if (tabContentInWorkspace.exists() && tabContentInWorkspace.size()>0){
    tabContent."exists"=true
    tabContent."path"=tabContentInWorkspace.canonicalPath
    } else     
    if(tabContentElsewhere.exists() && tabContentElsewhere.size()>0){
    tabContent."exists"=true
    tabContent."path"=tabContentElsewhere.canonicalPath
    }else
    if (filePath.startsWith('http') && getResponseCode(filePath) == 200){                          
	    tabContent."exists"=true
	    tabContent."path"=filePath
    } else {
	    println "\tCould not access content: $filePath"
	    tabContent."exists"=false
	    tabContent."path"=filePath                    
    }
    return tabContent
    }
 