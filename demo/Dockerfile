FROM tomcat:9.0.12-jre11-slim

COPY target/*.war /usr/local/tomcat/webapps/
COPY conf/tomcat-users.xml /usr/local/tomcat/conf/
COPY conf/manager-context.xml /usr/local/tomcat/webapps/manager/META-INF/context.xml