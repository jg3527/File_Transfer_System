JFLAGS = -g
JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
	DV.java \
	MyProtocol.java \
	Debug.java \
	Transfer.java \

default: classes

classes: $(CLASSES:.java=.class)

clean:
	rm -f *.class
