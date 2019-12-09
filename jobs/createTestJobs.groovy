import groovy.json.JsonSlurper

/**
 * Configuration verification
 */

def jsonSlurper = new JsonSlurper()
def environment = "TEST"
def confgurationData = jsonSlurper.parse(new File("$WORKSPACE/src/resources/configuration.json")).Environments.find {it.env==environment}

println("JIRA - " + jira)

if (confgurationData==null) {
    println "No configuration data for environment $environment"
    return
}

println "Configuration data: \n$confgurationData"

/**
 * Getting test scenarios data form JIRA
 */

def urlToGetTestsFromJira = confgurationData.url + confgurationData.pathToGetTests + jira

println ("URL to JIRA: " + urlToGetTestsFromJira)

def getRequest = new URL(urlToGetTestsFromJira).openConnection();
def getResponse = getRequest.getResponseCode();
println("Reponse code from $urlToGetTestsFromJira")

def responsePayload = ""

if(getResponse.equals(200)) {
    responsePayload = getRequest.getInputStream().getText()
    println(responsePayload);
} else {
    println "Response code different than expected 200: $getResponse"
}

def testConfigurationJson = jsonSlurper.parseText(responsePayload)

/**
 * Building pipeline
 */

def testPlanName = testConfigurationJson.TestPlan
println (testPlanName + ": $testPlanName")

def testScripts = []

def iterationScript = ""
def internalIterator = 0

testConfigurationJson.Scenarios.eachWithIndex{ scenario, index ->
    println "$index - $scenario.name - $scenario.id"

    def scriptValue = """stage('$scenario.id') {
                        agent {
                            label 'SgStacje'
                        }
                        steps {
                            script{
                                println("$scenario.id - $scenario.name")
                                def time = 1
                                echo "Waiting 1 seconds for test to complete"
                                sleep time.toInteger() // seconds
                            }
                        }
                        post {
                        always {
                            script{
                                println "Ended"
                            }
                        }
                    }
                    }                
            """

    internalIterator++

    iterationScript+=scriptValue
    if (internalIterator==10){
        println "Adding to array with ${index}"
        testScripts.add(iterationScript)
        iterationScript = ""
        internalIterator = 0
    }
}

testScripts.add(iterationScript)

def arraySize =  testScripts.size()

testScripts.eachWithIndex{ String part, int index ->

    println "index: ${index} "
    println "part: ${part} "


    pipelineJob("${testPlanName}_${index}") {
        definition {
            cps {
                script('''
    pipeline {
        agent none
        stages {
            stage('Run Tests') {
                parallel {
                    ''' + part + '''
        		}
			}
            stage ('Run next job') {
                steps{
                    script{
                        try {
                            build job: \'''' + testPlanName + '''_''' + (index + 1) + '''\', propagate: false
                        } catch (Exception e){}
                    }
                }
            }
    	}
	}
      '''.stripIndent())
                sandbox()
            }
        }
    }

}

/**
 * Running test job
 */

queue("${testPlanName}_0")


/**
 * Adding jobs to view
 */

listView("DSL") {
    jobs {
        regex(/DSL-.*/)
        name("DSL")
    }
    columns {
        status()
        weather()
        name()
        description()
        lastSuccess()
        lastFailure()
        lastDuration()
        buildButton()
    }
}