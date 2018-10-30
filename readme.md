# Jenkins Pipeline Scripts for Java CICD pipelines 
This repository contains scripts used in CICD Pipelines for projects that still depend on an upstream Subversion 
repository but that use a local gitlab instance so they can leverage a better merge request and quality flow.  

Pre-Requisites: 
* A Jenkins server
* A Gitlab server
* (optionally) an Artifactory server *
* (optionally) a SonarQube server *


Install steps:
1. Add pipeline_scripts folder to the Global Pipeline Libraries in: Manage Jenkins > Configure System > Global Pipeline Libraries
2. Create a CICD pipeline job that points to the CICD/Jenkinsfile 
3. Create a GitSvnSync pipeline job that uses the SvnGitSync/Jenkinsfile

This is a highly customized configuration so you will definitely need to take a look a the groovy scripts and modify 
(at least) the environment variables at the top of the script and probably the maven goals in the cicd.groovy script.  You will also need to customize the Jenkinsfiles to 
set the correct project name and possibly some other settings.  Feel free to contact me with questions or create any pull requests -
It would be interesting to see these script applied/adapted to different projects. 
