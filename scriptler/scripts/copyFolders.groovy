/* a script to copy specified folders from a parent folder 
 folders to copy must reside in same parent folder
 names of folders to copy are formatted as a comma separated list

vDestination=vDestination//"C:/CWorkspace/DMPQM487/Temp"
vParent=vParent//"C:/CWorkspace"
vFolder2Copy=vFolder2Copy//'DMPQM514,DMPQM619,Bam'

*/
destination=vDestination
parent=vParent
toCopy=vFolder2Copy

def ant= new AntBuilder()

vFolder2Copy.split(',').each{
dirName="$parent/${it.trim()}"
folder= new File(dirName)
if (folder.exists()){
ant.copy(todir: "$destination/$it"){
fileset(dir: dirName )
}
println "Copied $dirName"
}else{
println "Skipping missing folder: $dirName"
}

}//end each
return 'Success: (from copyFolders)'