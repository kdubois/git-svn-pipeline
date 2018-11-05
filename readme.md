# Jenkins CICD Pipeline Scripts for Containerized Java Applications and an Upstream Svn Dependency
This repository contains scripts used in CICD Pipelines for projects that still depend on an upstream Subversion 
repository but that use a local gitlab instance so they can leverage a better merge request and quality flow.  

Pre-Requisites: 
* A Jenkins server
* A Gitlab server
* (optional) A Subversion server (for the Sync part)
* (optionally) A configured SonarQube Jenkins plugin (2)

Install steps:
1. Add pipeline_scripts folder to the Global Pipeline Libraries in: Manage Jenkins > Configure System > Global Pipeline Libraries
2. Create a CICD pipeline job that points to the CICD/Jenkinsfile 
3. Create a GitSvnSync pipeline job that uses the SvnGitSync/Jenkinsfile

_(1) There's a 'runSonar' variable that can be set to false to disable the use of Sonar_  


## Run the demo on windows with Docker Swarm
start the cicd applications with Docker Swarm

`docker swarm init` (if you don't have a swarm running yet)  
Create custom external network (necessary on docker for windows): `docker network create -d overlay external_network`  
`docker stack deploy cicd --compose-file=Docker/docker-compose.yml`  
`docker exec -t <container_id of svn container> htpasswd -b /etc/subversion/passwd tester tester`  


Make sure all applications are up and running (gitlab takes several minutes). You can check on the status by opening
the Portainer UI, by default hosted at http://localhost:9010
Once gitlab is up and running, go to http://localhost and create a password for the root user, 
then log in with username root and the password you just created

Create group 'demo', click on create project and from the top menu, click 'Import project', and import this project.
Go to Jenkins (http://localhost:8080), and configure settings in Manage Jenkins (eg. install a JDK, Docker cloud, gitlab connection, sonarQube etc.)
In Jenkins System Settings, Cloud > Docker set Docker Host URI to tcp://docker.for.win.localhost:2375
In Tools, configure JDK and maven, then run a freestyle job with a maven step to make sure jdk and maven are installed 
Create a pipeline job in Jenkins, and use the git repository you just created to run the demo/jenkins/cicd.Jenkinsfile
gitlab integration, if using localhost: admin/application_settings > 