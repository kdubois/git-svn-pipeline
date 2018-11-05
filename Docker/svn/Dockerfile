FROM elleflorio/svn-server

# FOR TESTING PURPOSES ONLY !!
RUN htpasswd -b /etc/subversion/passwd tester tester && \
    svnadmin create /home/svn/demo

# authors.txt is required by git-svn to sync
COPY authors.txt /home/svn/demo/

VOLUME /etc/subversion
VOLUME /home/svn
