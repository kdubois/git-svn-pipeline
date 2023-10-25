# Automated Bidirectional Subversion - Git Sync with Jenkins
This repository contains scripts used in CICD Pipelines for projects that still depend on an upstream Subversion 
repository but that use a local gitlab instance so they can leverage a better merge request and quality flow.  

Pre-Requisites: 
* A Jenkins server
* A Gitlab server
* (optional) A Subversion server (for the Sync part)
* (optionally) A configured SonarQube Jenkins plugin (2)

Install steps:
1. Create a CICD pipeline job that points to the Jenkinsfiles/cicd.Jenkinsfile
1. Create a GitSvnSync pipeline job that uses the Jenkinsfiles/synPipeline.Jenkinsfile

_(1) There's a 'runSonar' variable that can be set to false to disable the use of Sonar_  


## POC / Demo

To run a demo with a containerized CI/CD suite, follow the instructions below. This includes instances of Jenkins, Gitlab,
Sonar + db, Svn and Portainer.  

Requirements: 
* Docker
* At least 4gb of RAM available to Docker 
* docker-compose or any other container orchestration tool

### Environment setup: 
1. Create custom external network (necessary on Docker for Windows): `docker network create external_network`  
1. Run `docker-compose -p cicd up -d`  
1. Make sure all applications are up and running (gitlab takes several minutes). You can check on the status by opening
the Portainer UI, by default hosted at http://localhost:9010
1. Once gitlab is up and running, go to http://localhost 
    1. create a password for the root user, 
    1. then log in with username root and the password you just created
    1. Create / Copy the private API token from Profile Settings -> Account and save for later 
    1. Go to admin > settings > network > outbound requests, and check the 'Allow requests to the local network from hooks and services' (admin/application_settings/network)
    1. Create group 'demo', click on create project and from the top menu, click 'Import project', and import this project from Github (name it git-svn-pipeline)
    1. Create an empty project 'demo', 
    1. In settings/repository create a deploy key with write access. (you'll need to create an ssh key, and paste the public key in the key field)
        
1. Go to Sonar, create an admin user and in the user settings, create a token
1. Go to Jenkins (http://localhost:8080), 
    1. In Credentials:
        1. Create username/password credentials for svn  with id 'svn' and user/pw tester/tester (this is defined in the Docker/svn/Dockerfile)
        1. Create username/password credentials for gitlab with id 'gitlab' 
        1. Create SSH Username with private key credentials for the gitlab deploy key with id 'jenkins-deploy-key' and enter the private ssh key that matches with the public key you created for gitlab. 
    1. In Jenkins System Settings:
        1. Configure SonarQube server: name 'SonarQube', Server Url 'http://sonar.intra:9000' and use the token you created in Sonar
        1. Configure a Gitlab connection, name it 'Gitlab', host 'http://gitlab.intra', and use the token previously created in Gitlab
        1. Pipeline Model definition: label 'docker.local', registry url 'http://gitlab-registry.intra' and add credentials to access gitlab
        1. Cloud > Docker: name 'docker.local', Docker Host URI to tcp://docker.for.win.localhost:2375
    1. In Global Tool Configuration:
        1. Configure JDK installation for Java 9 > 
        1. Configure Maven installation and give it the nam 'maven3'
    1. Create a new 'freestyle' job with a maven build step, it doesn't have to point to any real maven project, 
        but this job just needs to run once so Jenkins will download the JDK and maven you have just configured
    
### We're finally ready to create the actual pipeline jobs!
    
1. Create a new pipeline job (New Item > name (important!): 'pipeline-demo' and select 'pipeline' ) 
    1. Scroll down to Build Triggers, check the 'Build when a change is pushed to Gitlab' and 'opened merge requests'
    1. Scroll further down to the pipeline settings:
        1. Definition: Pipeline script from SCM
        1. SCM: Git
        1. Repositories > Repository Url: http://gitlab.intra/demo/git-svn-pipeline.git . Use git credentials you've set up earlier.
        1. Branches to build '*/master'
        1. Script Path: 'Jenkinsfiles/cicd.Jenkinsfile'
        1. Save
    1. You can try running the job, it should result in a failure as the job is getting configured from the pipeline script.
1. Create a second pipeline job (name is not important this time) 
    1. Don't set a build trigger for this job, but disallow concurrent builds
    1. Scroll down to pipeline settings:
        1. Definition: Pipeline script from SCM
        1. SCM: Git
        1. Repositories > Repository Url: http://gitlab.intra/demo/git-svn-pipeline.git . Use git credentials you've set up earlier.
        1. Branches to build '*/master'
        1. Script Path: 'Jenkinsfiles/syncPipeline.Jenkinsfile'
        1. Save
    1. Run the job 2 times (first run will fail due to parameters setup) - 
    If everything was setup correctly, you should now see the svn repository being synced to the 'demo' git repository,
    and the cicd pipeline being triggered by the sync pipeline.
        
      
        

