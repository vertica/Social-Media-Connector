Third-party Dependencies:
    JSONCpp:    
        Required package:   jsconcpp-src-0.6.0-rc2.tar.gz
        Project Homepage:   http://sourceforge.net/projects/jsoncpp/
        Note:
            The latest "released" version of this project is 0.5.0.  0.6.0rc2 
            can be found under the 'Files' tab on the project homepage.

    SCONS:
        Required package:   scons-local-2.3.0-tar.gz
        Project homepage:   http://www.scons.org/

    Flume:
        Project homepage:  http://flume.apache.org/
        Required package:  

    JDK:
        Project homepage:
        Required package:  jdk-6u11-linux-x64.bin

Prepare the third-party software:
1.  Download each of the required packages to the third-party/dist directory.
2.  Expand the jdk-6u11 package
        cd dist
        sh ./jdk-6u11-linux-x64.bin
            (you will need to accept the license agreement)
3.  In the third-party directory type 'make'


