def project = 'demo'
def gitGroup = 'root'
def svnRepo = 'http://svn/svn/demo'
def revision = ''
def gitlabUrl = 'gitlab.intra'
def svnCredentials = 'svn'

pipeline {
    agent any
    environment {
        PROJECT_U = project.toUpperCase()
    }
    parameters {
        booleanParam(name: 'DO_RELEASE', defaultValue: true, description: 'Merge a given release branch into master and release the code to Subversion')
        string(name: "RELEASE_BRANCH", defaultValue: 'develop', description: 'The branch to release from, eg. release_1.0')
        string(name: "TAG", defaultValue: '', description: 'Optional, tag Docker Images with this tag')
    }
    options {
        gitLabConnection("Gitlab")
    }
    stages {
        stage('Checkout from Subversion') {
            steps {
                script {
                    if (!fileExists('.git/svn/refs')) {
                        withCredentials([usernamePassword(credentialsId: "${svnCredentials}", passwordVariable: 'svn_pw', usernameVariable: 'svn_user')]) {
                            cleanWs()
                            // If you get server certificate validation errors, you may need to log in to the Jenkins server via CLI and first run an 'svn list <url>' command
                            sh "svn --username=${svn_user} --password=${svn_pw} list ${svnRepo} << EOF\n" +
                                    "p\n" +
                                    "EOF"
                            sh "svn --username=${svn_user} --password=${svn_pw}  export ${svnRepo}/authors.txt authors_tmp.txt"
                            sh "echo ${svn_pw} | git svn clone ${revision} --username=${svn_user} --authors-file=authors_tmp.txt --use-log-author ${svnRepo} . "
                        }
                        sh "git remote add origin ssh://git@${gitlabUrl}/${gitGroup}/${project}.git"
                        sh 'git config user.name "jenkins"'
                        sh "git config user.email \"jenkins@${gitlabUrl}\""
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
                withCredentials([usernamePassword(credentialsId: "${svnCredentials}", passwordVariable: 'svn_pw', usernameVariable: 'svn_user')]) {
                    script {
                        svnRebase = sh "echo ${svn_pw} | git svn rebase --use-log-author --authors-file=authors.txt", returnStdout: true
                        while (svnRebase.contains('CONFLICT')) {
                            svnRebase = sh script: 'git rebase --skip || true;', returnStdout: true
                        }
                    }
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
        stage('Run CI/CD Pipeline') {
            steps {
                script {
                    tagLatest = params.DO_RELEASE == true
                    build job: "${PROJECT_U}_CICD_Pipeline",
                            parameters: [
                                    string(name: 'PIPELINE_BRANCH', value: 'master'),
                                    string(name: 'TAG', value: "${params.TAG}"),
                                    booleanParam(name: 'TAG_LATEST', value: tagLatest)
                            ]
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
