FROM jenkins/jenkins:lts

USER root

ARG http_proxy

ENV http_proxy=$http_proxy
ENV https_proxy=$http_proxy

#this value should match with the docker host's gid  -> ls -aln /var/run/docker.sock  | awk '{print $4}'
ENV DOCKER_HOST_GID=992

RUN unlink /etc/localtime && ln -s /usr/share/zoneinfo/Europe/Brussels /etc/localtime &&\
    apt-get update && \
    apt-get -y --allow-unauthenticated install apt-transport-https \
         ca-certificates \
         curl \
         gnupg2 \
         software-properties-common && \
    curl -fsSL https://download.docker.com/linux/$(. /etc/os-release; echo "$ID")/gpg > /tmp/dkey; apt-key add /tmp/dkey &&\
    add-apt-repository \
       "deb [arch=amd64] https://download.docker.com/linux/$(. /etc/os-release; echo "$ID") \
       $(lsb_release -cs) \
       stable" &&\
    apt-get update

RUN apt-get -y install docker-ce git-svn \
    && groupmod -g $DOCKER_HOST_GID docker && usermod -a -G docker jenkins

USER jenkins

ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Dhudson.slaves.WorkspaceList='_' -Duser.timezone=Europe/Brussels"

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt

RUN /usr/local/bin/install-plugins.sh < /usr/share/jenkins/ref/plugins.txt

COPY tools/* /var/jenkins_home/downloads/

COPY groovy/* /usr/share/jenkins/ref/init.groovy.d/

COPY config/* /var/jenkins_home/

COPY .m2/ /var/jenkins/home/.m2/

