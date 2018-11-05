def mvnHome
def mvn
def project = 'demo'
def appPath = 'demo'
def hasDockerizedWebServer = true
def skipITs = false
def skipTests = false
def runSonar = true
def dockerHost = 'tcp://docker.for.win.localhost:2375' // default docker for windows daemon address, replace with eg. /var/run/docker.sock
def gitGroup = 'root'
def webServerPort = '8080' // internal port of the web server
def gitlabUrl = 'gitlab.intra'
def serverHost = 'localhost'
def serverProtocol = 'http'
def dockerRegistry = "gitlab-registry.intra"
def dockerCredentialId = "gitlab" // configure in Jenkins Credentials
def projectPath = 'demo/'

pipeline {
    agent any
    environment {
        PROJECT_U = project.toUpperCase()
        GIT_REPO = "${gitGroup}/${project}" 
        WL_IMAGE = "${dockerRegistry}/${gitGroup}/${project}/webserver-${project}"

    }
    parameters {
        string(name: "PIPELINE_BRANCH", defaultValue: '', description: 'branch to run the pipeline on, eg. develop')
        string(name: "TAG", defaultValue: '', description: 'optionally tag generated images and push to registry')
        booleanParam(name: 'TAG_LATEST', defaultValue: false, description: 'tag image as \'latest\'')
        booleanParam(name: 'KEEP_CONTAINERS', defaultValue: false, description: 'Keep containers running after job has finished?')
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: 'Skip ALL tests')
    }
    options {
        gitLabConnection("Gitlab")
    }
    stages {
        stage('setup') {
            steps {
                cleanWs()
                updateGitlabCommitStatus name: 'build', state: 'pending'
                script {
                    mvnHome = tool 'maven3'
                    mvn = "'${mvnHome}/bin/mvn' -s ${projectPath}.m2/settings.xml"

                    if ("${PIPELINE_BRANCH}" != '') {
                        gitlabSourceBranch = "${PIPELINE_BRANCH}"
                    }
                    checkout([
                            $class                           : 'GitSCM',
                            branches                         : [[name: "origin/${gitlabSourceBranch}"]],
                            doGenerateSubmoduleConfigurations: false,
                            extensions                       : [],
                            submoduleCfg                     : [],
                            userRemoteConfigs                : [
                                    [
                                            credentialsId: 'a2f915a3-9e75-42f6-b31e-c2c884d396da',
                                            url          : "http://${gitlabUrl}/${GIT_REPO}.git"
                                    ]
                            ]])
                    sh 'git merge origin/master'
                }
            }
        }
        stage('Build & Deploy Test Server') {
            steps {
                updateGitlabCommitStatus name: 'build', state: 'running'
                sh "${mvn} clean -DskipTests=true -f ${projectPath}pom.xml"
                script {
                    if (hasDockerizedWebServer) {
                        // Build artifact to be deployed in image
                        sh "${mvn} install -DskipTests=true -f ${projectPath}pom.xml -s ${projectPath}.m2/settings.xml"
                        docker.withServer("${dockerHost}") {
                            wlImage = docker.build("${WL_IMAGE}:pipeline", "${projectPath}")
                            sh "docker run --rm -d -p ${webServerPort} " +
                                    "--name=webserver-${project}-pipeline-${env.BUILD_NUMBER} ${WL_IMAGE}:pipeline"

                            // get dynamically generated ports for the webserver server
                            serverPort = sh script: "docker port webserver-${project}-pipeline-${env.BUILD_NUMBER} ${webServerPort}" +
                                    " | sed 's/.*://' | tr -d '\040\011\012\015'", returnStdout: true
                        }

                        if (params.KEEP_CONTAINERS == true) {
                            if ("${PIPELINE_BRANCH}" != '') {
                                gitlabSourceBranch = "${PIPELINE_BRANCH}"
                            }
                            // post connection information in Jenkins build description
                            currentBuild.description = "Branch: ${gitlabSourceBranch}<br>" +
                                    "<a href='${serverProtocol}://${serverHost}:${serverPort}/${appPath}'>App Test Port: ${serverPort}</a>"
                        }
                    }

                }
            }
        }
        stage('Maven Build, Test & Sonar') {
            steps {
                script {
                    if (params.SKIP_TESTS == true) {
                        skipTests = true
                        skipITs = true
                    }

                    def goals = "jacoco:prepare-agent install jacoco:report -Dtests.run.argLine= -Dfile.encoding=UTF-8 " +
                            "-DskipITs=${skipITs} -DskipTests=${skipTests} -Duser.timezone=Europe/Brussels " +
                            "-Dlocal.server.port=${serverPort} -Dlocal.server.host=${serverHost} " +
                            "-Dlocal.server.protocol=${serverProtocol} -f ${projectPath}pom.xml "
                    sh "${mvn} ${goals}"

                    if (runSonar) {
                        withSonarQubeEnv('SonarQube') {
                            sh "${mvn} sonar:sonar -Dsonar.projectName=${PROJECT_U} -Dsonar.projectKey=${PROJECT_U} -f ${projectPath}pom.xml "
                        }
                    }
                }
            }
        }
        stage('Tag and Push Images') {
            when {
                expression {
                    return params.TAG != '' || params.TAG_LATEST
                }
            }
            steps {
                script {
                    docker.withServer("${dockerHost}") {
                        withDockerRegistry(credentialsId: "${dockerCredentialId}", url: "${serverProtocol}://${dockerRegistry}") {

                            if (params.TAG != '') {
                                if (binding.hasVariable('wlImage')) {
                                    wlImage.push(params.TAG)
                                }
                            }
                            if (params.TAG_LATEST) {
                                if (binding.hasVariable('wlImage')) {
                                    wlImage.push('latest')
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    post {
        always {
            script {
                if (params.KEEP_CONTAINERS == false) {
                    def containers = ["webserver-${project}-pipeline-${env.BUILD_NUMBER}"]
                    docker.withServer("${dockerHost}") {
                        for (container in containers) {
                            sh "if [ `docker ps -a --filter \"name=${container}\" --format {{.Names}} | wc -l` = 1 ]; then docker rm -f ${container}; fi;"
                        }
                    }
                }
            }
        }
        success {
            updateGitlabCommitStatus name: 'build', state: 'success'
        }
        failure {
            updateGitlabCommitStatus name: 'build', state: 'failed'
        }
        aborted {
            updateGitlabCommitStatus name: 'build', state: 'canceled'
        }
    }
}