/*** BEGIN META {
  "name" : "displayAllJobs",
  "comment" : "Creates a list of all jobs on the Jenkins Server",
  "parameters" : [ ],
  "core": "1.596",
  "authors" : [
    { name : "Ioannis K. Moutsatsos" }
  ]
} END META**/

// Access to the Hudson Singleton
hudsonInstance = hudson.model.Hudson.instance

// Retrieve all Jobs which starts with -jobs-
allItems = hudsonInstance.items
choices=[]

// Create choices list
allItems.each { job ->
    choices.add(job.name)
}
return choices