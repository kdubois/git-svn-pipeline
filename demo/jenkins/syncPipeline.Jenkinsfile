// syncGitSvnPipeline should be added to Jenkins Global Pipeline Scripts
syncGitSvnPipeline(
        'demo', // project
        'http://svn-server/demo/trunk', // svn repo
        '', // revision
        false, // Sync Automated Tests
        true // Run Automated Tests
)

/**
 * Jenkins Pipeline Script to Sync between a legacy SVN repository and a Git repository
 * @param project project name
 * @param svnRepo svn repository url
 * @param revision svn revision to start at when creating the git repository (for large svn repositories) / leave empty if not needed
 * @param syncAT If there's a separate repository for Automated Tests, sync that repository as well
 * @param runAT Run Automated Tests as part of the Pipeline
 * @param isAT If this script is called from an Automated Test Jenkinsfile, then run things a little differently
 * @return void
 */
def call(String project, String svnRepo, String revision = '', boolean syncAT = true, boolean runAT = false, boolean isAT = false) {
    pipeline {
        agent any
        environment {
            MVN = '/var/jenkins_home/tools/hudson.tasks.Maven_MavenInstallation/maven3/apache-maven-3.5.3/bin/mvn'
            PROJECT_U = project.toUpperCase()
            GITLAB_URL = "gitlab.intra"
            SVN_CREDENTIALS_ID = "123-456-789" // credentials should be set up in Jenkins Credentials
        }
        parameters {
            booleanParam(name: 'DO_RELEASE', defaultValue: true, description: 'Merge a given release branch into master and release the code to Subversion')
            string(name: "RELEASE_BRANCH", defaultValue: 'develop', description: 'The branch to release from, eg. release_1.0')
            string(name: "TAG", defaultValue: '', description: 'Optional, tag git repositories and Docker Images with this tag')
        }
        options {
            gitLabConnection("Gitlab")
        }
        stages {
            stage('Checkout from Subversion') {
                steps {
                    script {
                        if (!fileExists('.git/svn/refs')) {
                            withCredentials([usernamePassword(credentialsId: "${SVN_CREDENTIALS_ID}", passwordVariable: 'svn_pw', usernameVariable: 'svn_user')]) {
                                cleanWs()
                                // If you get server certificate validation errors, you may need to log in to the Jenkins server via CLI and first run an 'svn list <url>' command
                                sh "svn --username=${svn_user} --password=${svn_pw} list ${svnRepo} << EOF\n" +
                                        "p\n" +
                                        "EOF"
                                sh "svn --username=${svn_user} --password=${svn_pw}  export ${svnRepo}/authors.txt authors_tmp.txt"
                                sh "echo ${svn_pw} | git svn clone ${revision} --username=${svn_user} --authors-file=authors_tmp.txt --use-log-author ${svnRepo} . "
                            }
                            repo = isAT ? 'at' : project
                            sh "git remote add origin ssh://git@${GITLAB_URL}/${project}/${repo}.git"
                            sh 'git config user.name "jenkins"'
                            sh "git config user.email \"jenkins@${GITLAB_URL}\""
                            sh 'git fetch'

                            sh "   if [ ! -z `git rev-parse --verify --quiet master` ]\n" +
                                    "   then\n" +
                                    "     echo \"Branch name master already exists.\"\n" +
                                    "     git branch --set-upstream-to=origin/master master\n" +
                                    "     git push -f origin master\n" +
                                    "   else\n" +
                                    "     git push -u origin master\n" +
                                    "     git checkout -b develop\n" +
                                    "     git push -u origin develop\n" +
                                    "   fi   \n"
                        }
                    }
                }
            }
            stage('Rebase from Subversion') {
                steps {
                    sh 'git rebase --abort || true;'
                    sh "git checkout master"
                    sh "git pull origin master"
                    sh "git reset --hard origin/master"
                    withCredentials([usernamePassword(credentialsId: "${SVN_CREDENTIALS_ID}", passwordVariable: 'svn_pw', usernameVariable: 'svn_user')]) {
                        sh "echo ${svn_pw} | git svn rebase --use-log-author --authors-file=authors.txt"
                    }
                    sh "git push -f origin master"
                }
            }
            stage('Merge Release into master branch') {
                when {
                    beforeAgent true
                    expression {
                        return params.DO_RELEASE == true && params.RELEASE_BRANCH != ''
                    }
                }
                steps {
                    sh "git checkout ${RELEASE_BRANCH}"
                    sh 'git pull'
                    sh 'git checkout master'
                    sh "git merge ${RELEASE_BRANCH}"
                    sh 'git push -f origin master'
                }
            }
            stage('Sync AT repository') {
                when {
                    expression { return syncAT }
                }
                steps {
                    script {
                        build job: "${PROJECT_U}_AT_Release_Pipeline",
                                parameters: [
                                        booleanParam(name: 'DO_RELEASE', value: params.DO_RELEASE),
                                        string(name: 'RELEASE_BRANCH', value: "${RELEASE_BRANCH}"),
                                        string(name: 'TAG', value: "${params.TAG}")
                                ]
                    }
                }
            }
            stage('Run CI/CD Pipeline') {
                steps {
                    script {
                        if (isAT) {
                            build job: "${PROJECT_U}_AT_Build_Quality",
                                    parameters: [
                                            string(name: 'BRANCH', value: 'master'),
                                            string(name: 'gitlabSourceBranch', value: 'master')
                                    ]
                        } else {
                            tagLatest = params.DO_RELEASE == true
                            build job: "${PROJECT_U}_CICD_Pipeline",
                                    parameters: [
                                            string(name: 'PIPELINE_BRANCH', value: 'master'),
                                            string(name: 'TAG', value: "${params.TAG}"),
                                            booleanParam(name: 'RUN_AT_TESTS', value: runAT),
                                            booleanParam(name: 'TAG_LATEST', value: tagLatest),
                                            string(name: 'AT_BRANCH', value: 'master')
                                    ]
                        }
                    }
                }
            }
            stage('Commit Changes to Svn') {
                steps {
                    sh 'git checkout master'
                    sh "git svn dcommit --add-author-from --use-log-author"
                }
            }
            stage('Push updates to master and develop') {
                steps {
                    sh 'git checkout master'
                    sh "git push -f origin master"
                    sh "git checkout develop"
                    sh "git pull"
                    script {
                        rebase = sh script: 'git rebase master || true;', returnStdout: true
                        while (rebase.contains('CONFLICT')) {
                            rebase = sh script: 'git rebase --skip || true;', returnStdout: true
                        }
                    }
                    sh "git push -f origin develop"
                    sh "git checkout master"
                }
            }
        }
    }
}