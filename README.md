###############################################################################
## Install / Update 3rd party libraries in local repo (save in GIT).
###############################################################################

                           !! For developers only !!

We hold several libraries locally in the project since they are not available
from public Maven repositories. The local repository is used automatically but
If you want to update one of library (newer version) you may have a look at the 
following instructions. 

## BridJ
Allows to access native libraries directly from Java (no JNI stuff to
write or compile). There was JNA and BridJ is an alternative to JNA (is 
promising but not very popular yet)

BridJ is not up to date in maven repo. 

'''
mkdir -p /tmp/bridj
cd /tmp/bridj
git clone http://github.com/nativelibs4java/BridJ.git
cd BridJ
mvn clean package
'''

(actual is version 0.7.1-snapshot)

'''
cd -
mvn install:install-file \
    -Dfile=/tmp/bridj/BridJ/target/bridj-0.7.1-SNAPSHOT.jar \
    -Dsources=/tmp/bridj/BridJ/target/bridj-0.7.1-SNAPSHOT-sources.jar \
    -DgroupId=com.nativelibs4java \
    -DartifactId=bridj -Dversion=0.7.1 -Dpackaging=jar -DcreateChecksum \
    -DlocalRepositoryPath=./lib/repository/
'''

-------------------------------------------------------------------------
# OLD Not tested 
# OLD Not tested 
# OLD Not tested 
PortAudio is the native C library we want to use to access audio devices
on any platforms. It is pretty straight forward to compile under Linux and
is probably already installed if you use Ubuntu. We provide compiled version
into lib/<arch> (see: http://code.google.com/p/bridj/wiki/LibrariesLookup).

Moreover we generated portaudio.jar (BridJ glue code to access portaudio
native libs). We used JNAerator to generate it directly from portaudio.h. 
JNAerator anaylse the header code, generate the glue and pack it 
automagically (we use the command line tool, not the UI). Then we added the
generated JAR into local repo.

'''
mvn install:install-file \
    -Dfile=/tmp/portaudio.jar \
    -Dsources=/tmp/portaudio.jar \
    -DgroupId=org.portaudio \
    -DartifactId=portaudio -Dversion=0.0.19 -Dpackaging=jar -DcreateChecksum \
    -DlocalRepositoryPath=./lib/repository/
'''

Opus Codec

Download opus source file (last is 1.1rc3)

'''
cd /tmp/
tar xzvf $HOME/Downloads/opus*
cd /tmp/opus-1.1-rc3/
./configure
## or: ./configure --enable-float-approx
make

## go into project directory
cp /tmp/opus-1.1-rc3/.libs/libopus.so lib/linux_x64

## use JNArator to create JAR file
java -jar jnaerator-0.12-SNAPSHOT-20130727.jar \
 /tmp/opus-1.1-rc3/include/opus_custom.h \
 /tmp/opus-1.1-rc3/include/opus_defines.h \
 /tmp/opus-1.1-rc3/include/opus.h \
 /tmp/opus-1.1-rc3/include/opus_multistream.h \
 /tmp/opus-1.1-rc3/include/opus_types.h \
 opus
 
## go into project directory
mvn install:install-file \
    -Dfile=$HOME/Downloads/opus.jar \
    -Dsources=$HOME/Downloads/opus.jar \
    -DgroupId=org.xiph \
    -DartifactId=opus -Dversion=1.1rc3 -Dpackaging=jar -DcreateChecksum \
    -DlocalRepositoryPath=./lib/repository/
 

## SHA4J (not necessary. just for tests)
mvn install:install-file \
    -Dfile=${HOME}/tmp/sha4j/sha4j.jar \
    -Dsources=${HOME}/tmp/sha4j/sha4j-src.jar \
    -DgroupId=com.softabar \
    -DartifactId=sha4j -Dversion=1.0.0 -Dpackaging=jar -DcreateChecksum \
    -DlocalRepositoryPath=./lib/repository/
'''
    