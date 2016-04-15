Fork of eXist Native XML Database
=========================

This is a fork of [eXist](http://www.exist-db.org "eXist") Native XML Database. 

Customizations of this fork
--------------------------
- ".classpath" corrected to build and run with Eclipse
- "installer/apps.properties" with only Dashboard,eXide,shared
  (removed: doc,demo,xsltforms-demo,xsltforms,bf-XForms,fundocs,markdown,xqjson,monex)
  Available in [public-repo](http://demo.exist-db.org/exist/apps/public-repo/index.html) 
- "extensions/build.properties" with JNDI activated (to use LDAP authentication)
- "log4j2.xml" with reduced logs : "warn" instead of "debug" for
   - "org.exist.storage.SystemTask"
   - "org.exist.statistics"
- build.properties with RK instead of RC

Clone eXist
--------------------------
```bash
$ cd /tmp
$ rm -rf /tmp/exist-3.0 dataDir=/tmp/data-3.0
$ git clone git@github.com:RemiKoutcherawy/exist.git
$ mv exist exist-rk
$ cd exist-rk
```

Build Installer
--------------------------
```bash
$ ./build.sh installer
```

Install
--------------------------
Directories homeDir:/tmp/exist-3.0 dataDir=/tmp/data-3.0 are in options30.txt

```bash
$ rm -rf /tmp/exist-3.0 dataDir=/tmp/data-3.0
$ java -jar installer/eXist-db-setup-3.0.RK-develop-fd7e45e.jar -options options30.txt
$ cd exist-3.0
$ echo "sm:passwd('admin', 'mypassword')" | ./bin/client.sh -l -x -P \$adminPasswd
```

Run on command line
--------------------------
```bash
$ ./bin/startup.sh &
```

Open [Localhost](http://localhost:8080/)
Log with the password you just set 'mypassword'

Run on Eclipse
--------------------------
1/ Startup
   Main class : org.exist.start.Main
   Program arguments : jetty
   VM arguments : -Dexist.home=/tmp/exist-rk
2/ Shutdown (port 8080)
   Main class : org.exist.start.Main
   Program arguments : shutdown -u admin
   VM arguments : 
3/ Server
   Main class : org.exist.start.Main
   Program arguments : standalone
   VM arguments : -Djetty.port=8088 -Dexist.home=/Users/remi/git/exist-rk


