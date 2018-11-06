FROM elleflorio/svn-server

# FOR TESTING PURPOSES ONLY !!
RUN htpasswd -b /etc/subversion/passwd tester tester &&\
    svnadmin create /home/svn/demo &&\
    chown -R apache /home/svn/demo

COPY dav_svn.conf /home/svn/demo/conf/
# authors.txt is required by git-svn to sync
COPY demo /tmp/demo

WORKDIR /root
RUN apachectl start &&\
    svn checkout --force  --username tester --password tester http://localhost/svn/demo &&\
    cd demo &&\
    mv /tmp/demo/* . &&\
    mkdir ./.m2 && mv /tmp/demo/.m2/settings.xml ./.m2/ &&\
    svn add * .m2 &&\
    svn commit --username tester --password tester --non-interactive -m demo
