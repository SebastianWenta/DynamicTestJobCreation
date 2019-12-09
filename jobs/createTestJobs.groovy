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

def testScript = ""

testConfigurationJson.Scenarios.eachWithIndex{ scenario, index ->
    println "$index - $scenario.name - $scenario.id"
    testScript+="""stage('$scenario.id') {
                        agent {
                            label 'SgStacje'
                        }
                        steps {
                            script{
                                println("$scenario.id - $scenario.name")
                                def time = 5
                                echo "Waiting 5 seconds for test to complete"
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
}

println testScript

pipelineJob("$testPlanName") {
    definition {
        cps {
            script('''
    pipeline {
        agent none
        stages {
            stage('Run Tests') {
                parallel {
                    ''' + testScript + '''
        		}
			}
    	}
	}
      '''.stripIndent())
            sandbox()
        }
    }
}

/**
 * Adding jobs to view
 */

listView("DSL") {
    jobs {
        name("$testPlanName")
        regex(/DSL-.*/)
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