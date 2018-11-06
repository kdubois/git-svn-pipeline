FROM elleflorio/svn-server

# FOR TESTING PURPOSES ONLY !!
RUN htpasswd -b /etc/subversion/passwd tester tester &&\
    svnadmin create /home/svn/demo &&\
    chown -R apache /home/svn/demo

COPY dav_svn.conf /home/svn/demo/conf/
# authors.txt is required by git-svn to sync
COPY authors.txt /root/

WORKDIR /root
RUN apachectl start &&\
    svn checkout --force  --username tester --password tester http://localhost/svn/demo &&\
    cd demo &&\
    mv /root/authors.txt . &&\
    svn add authors.txt &&\
    svn commit --username tester --password tester --non-interactive -m authors
