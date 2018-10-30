def dbport=''
def serverport=''
def debugport=''
def dbImage
def wlImage

def call(String project, String appPath = '', hasDockerizedWeblogic = true, hasDB = true, skipITs = false, skipTests = false) {
    pipeline {
        agent any
        environment {
            MAVEN_HOME = '/var/jenkins_home/tools/hudson.tasks.Maven_MavenInstallation/maven3/apache-maven-3.5.3/'
            MVN = "${MAVEN_HOME}bin/mvn"
            PROJECT_U = project.toUpperCase()
            TEST_URL = 'my-test-url.com'
            GIT_REPO = "${project}/${project}"
            DB_IMAGE = "my-registry/${project}/${project}/db2-${project}"
            WL_IMAGE = "my-registry/${project}/${project}/weblogic-${project}"
            GITLAB_URL = "gitlab.intra"
            ARTIFACTORY_SERVER = "artifactory" // configure an artifactory server connection in the Jenkins artifactory plugin settings
        }
        parameters {
            string(name: "PIPELINE_BRANCH", defaultValue: '', description: 'branch to run the pipeline on, eg. develop')
            string(name: "TAG", defaultValue: '', description: 'optionally tag generated images and push to registry')
            booleanParam(name: 'TAG_LATEST', defaultValue: false, description: 'tag image as \'latest\'')
            booleanParam(name: 'KEEP_CONTAINERS', defaultValue: false, description: 'Keep containers running after job has finished?')
            booleanParam(name: 'RUN_AT_TESTS', defaultValue: hasDockerizedWeblogic, description: 'Run AT Tests?')
            string(name: "AT_BRANCH", defaultValue: '', description: 'Automated Tests Repository branch name')
            booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: 'Skip ALL tests')
        }
        options {
            gitLabConnection("Gitlab")
        }
        stages {
            stage('checkout SCM') {
                steps {
                    cleanWs()
                    updateGitlabCommitStatus name: 'build', state: 'pending'
                    script {
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
                                                url          : "ssh://git@${GITLAB_URL}/${GIT_REPO}.git"
                                        ]
                                ]])
                        sh 'git merge origin/develop'
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
                    sh "docker run --rm -d -p 50000 --name=db2-${project}-pipeline-${env.BUILD_NUMBER} ${DB_IMAGE}:pipeline"
                }
            }
            stage('Build & Deploy Server') {
                steps {
                    updateGitlabCommitStatus name: 'build', state: 'running'
                    sh "${MVN} clean -DskipTests=true"
                    script {
                        serverport = ''
                        dbport = ''
                        debugport = ''
                        if (hasDB) {
                            // get dynamically generated db port
                            dbport = sh script: "docker port db2-${project}-pipeline-${env.BUILD_NUMBER}" +
                                    " | sed 's/.*://' | tr -d '\040\011\012\015'", returnStdout: true

                            if (hasDockerizedWeblogic) {
                                // Build artifact to be deployed in image
                                sh "${MVN} install -DskipTests=true"
                                wlImage = docker.build("${WL_IMAGE}:pipeline")
                                sh "docker run --rm -d -p 7001 -p 8453 -e \"DBHOST=${TEST_URL}\" -e \"DBPORT=${dbport}\" " +
                                        "--name=weblogic-${project}-pipeline-${env.BUILD_NUMBER} ${WL_IMAGE}:pipeline"

                                // get dynamically generated ports for the weblogic server
                                serverport = sh script: "docker port weblogic-${project}-pipeline-${env.BUILD_NUMBER} 7001" +
                                        " | sed 's/.*://' | tr -d '\040\011\012\015'", returnStdout: true
                                debugport = sh script: "docker port weblogic-${project}-pipeline-${env.BUILD_NUMBER} 8453" +
                                        " | sed 's/.*://' | tr -d '\040\011\012\015'", returnStdout: true

                                if (params.KEEP_CONTAINERS == true) {
                                    if ("${PIPELINE_BRANCH}" != '') {
                                        gitlabSourceBranch = "${PIPELINE_BRANCH}"
                                    }
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
                        def resolve_server = Artifactory.server "${ARTIFACTORY_SERVER}"
                        def deploy_server = Artifactory.server "${ARTIFACTORY_SERVER}"

                        def rtMaven = Artifactory.newMavenBuild()
                        // pushes snapshots to a local artifactory (handy for snaphots needed during merge requests when there are dependendies between different projects)
                        rtMaven.deployer server: deploy_server, releaseRepo: 'libs-release-local', snapshotRepo: 'libs-snapshot-local'
                        def goals = "jacoco:prepare-agent install jacoco:report  -Dtests.run.argLine= -Dfile.encoding=UTF-8 " +
                                "-DskipITs=${skipITs} -DskipTests=${skipTests} -PDOCKER -s .m2/settings.xml -Duser.timezone=Europe/Brussels " +
                                "-D${project}.project.db.host=${TEST_URL} -D${project}.project.db.port=${dbport ?: ''} " +
                                "-Dcontainer.admin.port=${serverport} -Dcontainer.admin.host=${TEST_URL}"
                        def buildInfo = rtMaven.run pom: 'pom.xml', goals: goals.toString()
                        deploy_server.publishBuildInfo buildInfo
                    }
                    withSonarQubeEnv('SonarQube') {
                        sh "${MVN} sonar:sonar -Dsonar.projectName=${PROJECT_U} -Dsonar.projectKey=${PROJECT_U}"
                    }
                }
            }
            // Automated Tests that are in a separate repository
            stage('AT Tests') {
                when {
                    expression { return hasDockerizedWeblogic && (params.RUN_AT_TESTS || params.KEEP_CONTAINERS) }
                }
                steps {
                    script {
                        atBranch = params.AT_BRANCH != '' ? params.AT_BRANCH : 'develop'

                        if (!params.AT_BRANCH?.trim() && gitlabSourceBranch != 'develop') {
                            atPipelineBranchExists = sh script: "git ls-remote --heads ssh://git@${GITLAB_URL}/${project}/at.git ${gitlabSourceBranch} | wc -l", returnStdout: true
                            println "<<<" + (String) atPipelineBranchExists.replaceAll("Warning.*", "").replaceAll("\\s", "") + ">>>"
                            if (atPipelineBranchExists.replaceAll("Warning.*", "").replaceAll("\\s", "").toInteger()) {
                                println "branch exists in both source and AT repo"
                                atBranch = "${gitlabSourceBranch}"
                            }
                        }
                        println "AT Branch is: " + atBranch
                    }
                    cleanWs()
                    checkout(
                            [$class                           : 'GitSCM',
                             branches                         : [[name: "*/${atBranch}"]],
                             doGenerateSubmoduleConfigurations: false,
                             extensions                       : [],
                             submoduleCfg                     : [],
                             userRemoteConfigs                : [[credentialsId: 'a61fe617-4e1d-47db-aa11-fc048c4a7f2b',
                                                                  url          : "ssh://git@${GITLAB_URL}/${project}/at.git"]]])
                    // replace localhost with the test url of the jenkins server
                    sh "sed -i 's/localhost/${TEST_URL}/g' environment-config.xml"
                    // hack to replace ports with the ports that were created for the various docker containers
                    sh "sed -i 's/<DB2_Instance_Port>.*<\\/DB2_Instance_Port>/<DB2_Instance_Port>${dbport}<\\/DB2_Instance_Port>/g' environment-config.xml"
                    sh "sed -i 's/<Server_Port>.*<\\/Server_Port>/<Server_Port>${serverport}<\\/Server_Port>/g' environment-config.xml"
                    sh "until \$(curl --output /dev/null --silent --head --fail ${TEST_URL}:${serverport}/console); do\n" +
                            "    printf '.'\n" +
                            "    sleep 2\n" +
                            "done"
                    script {
                        if (params.RUN_AT_TESTS) {
                            sh "cd Test_Scripts/Execution && " +
                                    "${MVN} clean install " +
                                    "-Denvironment=DOCKER "
                        }
                        // if containers should be kept, then pre-load the db with a standard test data set
                        if (params.KEEP_CONTAINERS == true) {
                            sh "cd Test_Scripts/Execution && " +
                                    "${MVN} test -Dtest=*zzz* -Dcase=*fill* " +
                                    "-Denvironment=DOCKER "
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
                    withDockerRegistry(credentialsId: 'c8c91252-45ae-402d-b706-f938de218a2d', url: 'http://gitlab-registry.finbel.intra') {
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
                        def containers = ["db2-${project}-pipeline-${env.BUILD_NUMBER}", "weblogic-${project}-pipeline-${env.BUILD_NUMBER}"]
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
}

