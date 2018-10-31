# Jenkins CICD Pipeline Scripts for Containerized Java Applications and an Upstream Svn Dependency
This repository contains scripts used in CICD Pipelines for projects that still depend on an upstream Subversion 
repository but that use a local gitlab instance so they can leverage a better merge request and quality flow.  

Pre-Requisites: 
* A Jenkins server
* A Gitlab server
* (optional) A Subversion server (for the Sync part)
* (optionally) A configured Artifactory Jenkins plugin (1)
* (optionally) A configured SonarQube Jenkins plugin (2)

Install steps:
1. Add pipeline_scripts folder to the Global Pipeline Libraries in: Manage Jenkins > Configure System > Global Pipeline Libraries
2. Create a CICD pipeline job that points to the CICD/Jenkinsfile 
3. Create a GitSvnSync pipeline job that uses the SvnGitSync/Jenkinsfile

This is a highly customized configuration so you will definitely need to take a look a the groovy scripts and modify 
(at least) the environment variables at the top of the script and probably the maven goals in the cicd.groovy script.  You will also need to customize the Jenkinsfiles to 
set the correct project name and possibly some other settings.  Feel free to contact me with questions or create any pull requests -
It would be interesting to see these script applied/adapted to different projects. 


_(1) There's a 'runSonar' variable that can be set to false to disable the use of Sonar_  
_(2) If Artifactory is not needed, remove the artifactory build commands from the cicd.groovy file_


## Run the demo
start the cicd applications:

cd Docker && docker-compose up -d 
Make sure all applications are up and running (gitlab takes several minutes). You can check on the status by opening
the Portainer UI, by default hosted at http://localhost:9010
Once gitlab is up and running, go to http://localhost and create a password for the root user, 
then log in with username root and the password you just created

Create group 'demo', click on create project and from the top menu, click 'Import project', and import this project.

