## Running a CICD pipeline on RHEL 7 with Docker, Gitlab and Jenkins

### RHEL Docker install and configuration:
1. Install Docker (on rhel < 7.5, install docker-latest):  
    `sudo yum install docker`
    
1. Add aliases to /etc/hosts for local registry eg.
    `127.0.0.1 gitlab.intra gitlab-registry.intra`    

1. IF you're behind a corporate proxy, configure proxy settings 
    * create/edit file:  `sudo vi /etc/systemd/system/docker-latest.service.d/http-proxy.conf`
    * Add the following lines:    
        `[Service]`  
        `Environment="HTTP_PROXY=http://{user}:{password}@your_proxy_url:8080" "NO_PROXY=localhost,gitlab-registry.intra,gitlab.intra"`
    * Then in the cli run: `systemctl daemon-reload`
    * `export no_proxy=gitlab.intra`
    * `export http_proxy=http://{user}:{password}@your_proxy_url:8080`
    * `export https_proxy=http://{user}:{password}@your_proxy_url:8080`

1. Change docker storage driver to overlay:   
    `sudo systemctl stop docker-latest`  
    `sudo container-storage-setup`  
    `sudo vi /etc/sysconfig/docker-storage` & ADD: `DOCKER_STORAGE_OPTIONS="--storage-driver=overlay"`  
    `sudo systemctl start docker-latest`

1. Install git:  
    `sudo yum install git`
    
1. Create 'cicd' user & group:  
    `sudo useradd -g cicd cicd`

1. Create a 'jenkins' user with uid 1000
    `sudo useradd -u 1000 jenkins` 
    
### CICD Suite installation
1. check out this codebase, eg:
    `sudo mkdir /var/repositories`  
    `sudo -u cicd mkdir repositories`     
    `sudo -u cicd git clone git@github.com:kdubois/jenkins.git /var/repositories/jenkins`
   
1. Build the jenkins image
    `sudo docker build -t gitlab-registry.intra/infra/docker/jenkins /var/repositories/jenkins/Docker/jenkins`
1. Build the svn image
    `sudo docker build -t gitlab-registry.intra/infra/docker/svn /var/repositories/jenkins/Docker/svn`  
      
1. Deploy docker containers using Docker Swarm (can be done on Kubernetes cluster as well). This deploys Jenkins, Gitlab, SonarQube, Artifactory and Portainer instances
    `sudo docker swarm init`
    `sudo docker stack deploy cicd --compose-file=/var/repositories/jenkins/Docker/docker-compose.yml`
    
1. (Optional) Install gitlab runner:
    `curl -L https://packages.gitlab.com/install/repositories/runner/gitlab-runner/script.rpm.sh | sudo bash`  
    `sudo yum install gitlab-runner`  
    `sudo gitlab-runner register`
    
1. Jenkins configuration:
    * In theory, the jenkins image that is built from the source code in Docker/jenkins/Dockerfile should contain the plugins needed, but you may need to configure some more settings in the 'Manage Jenkins' UI.
    * If maven or java won't execute, check noexec settings of the host  
        * in my case, /var/lib/docker was mounted as noexec so I had to unmount and remount as exec  
            eg. `sudo mount -o remount,exec /var/lib/docker`  
            Do this for every mounted directory.   
        * You may need to update the /etc/fstab file directly if the above doesn't work, and change 'noexec' for the mounted directory to 'exec'. 
        * You'll probably need to restart the server afterwards (`sudo reboot now`) 
    * Add user '1000' to any mounted directory as well  
        eg. `sudo chown -R 1000:cicd /var/lib/docker-latest/volumes/cicd_jenkins`
    * In main settings, enable Docker cloud and set Docker URI to `unix:///var/run/docker.sock`      
    * Create deploy key in gitlab, and add it to Jenkins to be able to checkout git projects 
    and push/pull from the docker registry , as well as report build statuses back to Gitlab.
    * Add SonarQube url to the Sonar settings, eg sonar.intra:9000

1. Trouble shooting:
    * **Docker is not responding properly or a service won't restart**:
        * `sudo systemctl status docker-latest` should show any errors docker is seeing.  
        If, for example, a service was restarted while a network reconfiguration was happening, then the network config could have gotten corrupted.  A system reboot would fix the issue.
    * **Git Rebase errors**:
        * If a Jenkins job fails due to a git merge/rebase error, you may need to exec into the jenkins container and go to the workspace to clear up the rebase issue.  Follow these steps to resolve the issue:   
            * Examine the failed job output and verify if there's a git rebase issue. If so, copy the workspace directory from the output (hint: it's in square brackets, eg [AutoDT_Release_Pipeline_2]) 
            * In Portainer, find the running Jenkins container and click the 'console' button
                * Alternatively, ssh into the host machine and execute the following command: `docker exec -it $(docker ps | grep jenkins | awk "{print \$1}") bash`
            * `cd /var/jenkins_home/workspace/<workspace name>` (where <workspace name would be what you copied from the Jenkins Job output earlier, eg. AutoDT_Release_Pipeline_2)
            * `git status` should tell you that a rebase is in progress
            * repeat `git rebase --skip` until you see a message saying "No changes -- Patch already applied."
            * verify the branch you're on: `git branch`
            * push the branch back up to the remote: `git push -f origin develop`  (replace 'develop' with the actual branch name from the previous step)
             

 
    
