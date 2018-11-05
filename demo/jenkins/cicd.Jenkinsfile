def mvnHome
def mvn
def project = 'demo'
def appPath = 'demo'
def hasDockerizedWebServer = true
def hasDB = false
def skipITs = false
def skipTests = false
def runSonar = true

pipeline {
    agent any
    environment {
        PROJECT_U = project.toUpperCase()
        TEST_URL = 'localhost'
        GIT_REPO = "root/${project}" //
        DOCKER_REGISTRY = "my-docker-registry.com"
        DB_IMAGE = "${DOCKER_REGISTRY}/${project}/${project}/db-${project}"
        WL_IMAGE = "${DOCKER_REGISTRY}/${project}/${project}/webserver-${project}"
        GITLAB_URL = "gitlab.intra"
        ARTIFACTORY_SERVER = "artifactory" // configure an artifactory server connection in the Jenkins artifactory plugin settings
        INTERNAL_SERVER_PORT = "8080"
        INTERNAL_SERVER_DEBUG_PORT = "8453" // default 8453 for weblogic
        INTERNAL_DB_PORT = "50000" // default 50000 for db2
        DOCKER_REGISTRY_CREDENTIALS = "12345679-123456" // configure in Jenkins Credentials
    }
    parameters {
        string(name: "PIPELINE_BRANCH", defaultValue: '', description: 'branch to run the pipeline on, eg. develop')
        string(name: "TAG", defaultValue: '', description: 'optionally tag generated images and push to registry')
        booleanParam(name: 'TAG_LATEST', defaultValue: false, description: 'tag image as \'latest\'')
        booleanParam(name: 'KEEP_CONTAINERS', defaultValue: false, description: 'Keep containers running after job has finished?')
        booleanParam(name: 'RUN_AT_TESTS', defaultValue: hasDockerizedWebServer, description: 'Run AT Tests?')
        string(name: "AT_BRANCH", defaultValue: '', description: 'Automated Tests Repository branch name')
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
                    mvn = "'${mvnHome}/bin/mvn' -s .m2/settings.xml"

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
                                            url          : "http://${GITLAB_URL}/${GIT_REPO}.git"
                                    ]
                            ]])
                    sh 'git merge origin/master'
                }
            }
        }
        stage('Build & Deploy DB Container') {
            when {
                expression {
                    return hasDB
                }
            }
            steps {
                updateGitlabCommitStatus name: 'build', state: 'running'
                withCredentials([
                        string(credentialsId: 'MY_PROXY', variable: 'MY_PROXY'),
                        usernamePassword(credentialsId: "${PROJECT_U}_DB_U_P", passwordVariable: "DBPW", usernameVariable: "DBUSER")]) {
                    script {
                        dbImage = docker.build(
                                "${DB_IMAGE}:pipeline",
                                "--build-arg DBNAME=${PROJECT_U} --build-arg DBUSER=${DBUSER} " +
                                        "--build-arg DBPWD=${DBPW} --build-arg http_proxy=${MY_PROXY} " +
                                        "-f db.Dockerfile .")
                    }
                }
                sh "docker run --rm -d -p ${INTERNAL_DB_PORT} --name=db-${project}-pipeline-${env.BUILD_NUMBER} ${DB_IMAGE}:pipeline"
            }
        }
        stage('Build & Deploy Server') {
            steps {
                updateGitlabCommitStatus name: 'build', state: 'running'
                sh "${mvn} clean -DskipTests=true -f demo/pom.xml"
                script {
                    serverport = ''
                    dbport = ''
                    debugport = ''
                    if (hasDB) {
                        // get dynamically generated db port
                        dbport = sh script: "docker port db-${project}-pipeline-${env.BUILD_NUMBER}" +
                                " | sed 's/.*://' | tr -d '\040\011\012\015'", returnStdout: true

                        if (hasDockerizedWebServer) {
                            // Build artifact to be deployed in image
                            sh "${mvn} install -DskipTests=true"
                            wlImage = docker.build("${WL_IMAGE}:pipeline")
                            sh "docker run --rm -d -p ${INTERNAL_SERVER_PORT} -p ${INTERNAL_SERVER_DEBUG_PORT} " +
                                    "-e \"DBHOST=${TEST_URL}\" -e \"DBPORT=${dbport}\" " +
                                    "--name=webserver-${project}-pipeline-${env.BUILD_NUMBER} ${WL_IMAGE}:pipeline"

                            // get dynamically generated ports for the webserver server
                            serverport = sh script: "docker port webserver-${project}-pipeline-${env.BUILD_NUMBER} 7001" +
                                    " | sed 's/.*://' | tr -d '\040\011\012\015'", returnStdout: true
                            debugport = sh script: "docker port webserver-${project}-pipeline-${env.BUILD_NUMBER} 8453" +
                                    " | sed 's/.*://' | tr -d '\040\011\012\015'", returnStdout: true

                            if (params.KEEP_CONTAINERS == true) {
                                if ("${PIPELINE_BRANCH}" != '') {
                                    gitlabSourceBranch = "${PIPELINE_BRANCH}"
                                }
                                // post connection information in Jenkins build description
                                currentBuild.description = "Branch: ${gitlabSourceBranch}<br>" +
                                        "<a href='http://${TEST_URL}:${serverport}/${appPath}'>App Test Port: ${serverport}</a><br>" +
                                        "DB Port: ${dbport} | Debug Port: ${debugport}"
                            }
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

                    def goals = "jacoco:prepare-agent install jacoco:report  -Dtests.run.argLine= -Dfile.encoding=UTF-8 " +
                            "-DskipITs=${skipITs} -DskipTests=${skipTests} -Duser.timezone=Europe/Brussels " +
                            "-D${project}.project.db.host=${TEST_URL} -D${project}.project.db.port=${dbport ?: ''} " +
                            "-Dcontainer.admin.port=${serverport} -Dcontainer.admin.host=${TEST_URL} -f demo/pom.xml "
                    sh "${mvn} ${goals}"

                    if (runSonar){
                        withSonarQubeEnv('SonarQube') {
                            sh "${mvn} sonar:sonar -Dsonar.projectName=${PROJECT_U} -Dsonar.projectKey=${PROJECT_U}"
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
                withDockerRegistry(credentialsId: "${DOCKER_REGISTRY_CREDENTIALS}", url: "${DOCKER_REGISTRY}") {
                    script {
                        if (params.TAG != '') {
                            if (binding.hasVariable('dbImage')) {
                                dbImage.push(params.TAG)
                            }
                            if (binding.hasVariable('wlImage')) {
                                wlImage.push(params.TAG)
                            }
                        }
                        if (params.TAG_LATEST) {
                            if (binding.hasVariable('dbImage')) {
                                dbImage.push('latest')
                            }
                            if (binding.hasVariable('wlImage')) {
                                wlImage.push('latest')
                            }
                        }
                    }
                }
            }
        }
        stage('Tag repository') {
            when {
                expression {
                    return params.TAG != ''
                }
            }
            steps {
                sh "git tag ${params.TAG} --force"
                sh "git push -f origin ${params.TAG}"
            }
        }
    }
    post {
        always {
            script {
                if (params.KEEP_CONTAINERS == false) {
                    def containers = ["db-${project}-pipeline-${env.BUILD_NUMBER}", "webserver-${project}-pipeline-${env.BUILD_NUMBER}"]
                    for ( container in containers ) {
                        sh "if [ `docker ps -a --filter \"name=${container}\" --format {{.Names}} | wc -l` = 1 ]; then docker rm -f ${container}; fi;"
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